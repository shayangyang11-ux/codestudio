from __future__ import annotations

import asyncio

import json
from typing import Any

from app.schemas.development_codegen import (
    DevelopmentCodeFilePayload,
    DevelopmentNodejsCodegenRequest,
    DevelopmentNodejsCodegenResponse,
    DevelopmentNodejsRepairRequest,
)
from app.services.development_nodejs_fullstack_orchestrator import NodejsFullstackCodegenOrchestrator
from app.services.development_ui_contract_vue_codegen import UiContractVueCodegenService
from app.services.llm_model_router import LlmModelRouter
from app.services.llm_service import LlmService

_ALLOWED_ACTIONS = {"CREATE_OR_UPDATE", "CREATE_OR_KEEP", "MERGE"}
_ALLOWED_FILE_TYPES = {"CONFIG", "DOC", "FRONTEND", "BACKEND", "DATABASE", "TEST", "UNKNOWN"}
_MAX_FILES = 90
_MAX_FILE_CHARS = 180_000
_MAX_TOTAL_CHARS = 1_500_000


class DevelopmentCodeGenerationService:
    """
    Development Agent codegen adapter.

    M2 intentionally treats the LLM as a proposal/refinement layer instead of an
    unconstrained file writer. Java still validates every file path and uses the
    existing materialization/snapshot pipeline before writing anything to disk.
    """

    def __init__(self) -> None:
        self.llm_service = LlmService(agent_scope="development", llm_task="codegen")
        self.model_router = LlmModelRouter()
        self.ui_contract_vue_codegen = UiContractVueCodegenService()
        self.fullstack_orchestrator = NodejsFullstackCodegenOrchestrator(self.ui_contract_vue_codegen)

    async def generate_nodejs_proposal(
        self,
        request: DevelopmentNodejsCodegenRequest,
    ) -> DevelopmentNodejsCodegenResponse:
        """
        Generate a Node.js code proposal through the Development Codegen Agent.

        Phase 10-A7.5 fixes the single giant LLM request timeout by splitting the
        generation work into bounded batches. This keeps the Agent as the business
        code generation core, while Java templates remain only deterministic
        guardrails and merge anchors.
        """
        profile = self.model_router.resolve(agent_scope="development", llm_task="codegen")
        warnings: list[str] = []
        self._prepare_development_build_context(request, warnings)
        deterministic_files = self._sanitize_files(
            request.deterministic_files,
            request.generation_root,
            warnings=warnings,
        )

        # A9.10.7: Development Build Context -> Node.js fullstack deterministic orchestrator.
        # User operation remains one-click; internals generate frontend, backend, data and smoke tests.
        fullstack_codegen_response = self.fullstack_orchestrator.generate(request, warnings)
        if fullstack_codegen_response is not None:
            return fullstack_codegen_response

        # A9.10.5: Prefer confirmed/published UI_CONTRACT deterministic Vue codegen.
        # This path reads only handoff_context.confirmedUiContract, never artifact drafts.
        ui_contract_codegen_response = self.ui_contract_vue_codegen.generate(request, warnings)
        if ui_contract_codegen_response is not None:
            return ui_contract_codegen_response

        if not deterministic_files:
            return DevelopmentNodejsCodegenResponse(
                status="FALLBACK_TEMPLATE",
                generator_mode="DETERMINISTIC_TEMPLATE",
                provider=profile.provider,
                model=profile.model,
                summary="未收到可用的确定性模板文件，已返回空代码提案。",
                warnings=["DEVELOPMENT_CODEGEN_EMPTY_TEMPLATE_FILES"],
                files=[],
                usage={"provider": profile.provider, "model": profile.model},
            )

        batches = self._build_codegen_batches(
            request=request,
            deterministic_files=deterministic_files,
        )
        required_batch_keys = {
            str(batch.get("key"))
            for batch in batches
            if bool(batch.get("required"))
        }

        if not batches:
            return DevelopmentNodejsCodegenResponse(
                status="AGENT_FAILED",
                generator_mode="LLM_BATCH_CODEGEN_NO_BATCH",
                provider=profile.provider,
                model=profile.model,
                summary="Development Codegen Agent 没有可执行的代码生成批次。",
                warnings=["DEV_CODEGEN_BATCH_EMPTY"],
                files=[],
                usage={"provider": profile.provider, "model": profile.model, "success": False},
                raw={"error": "BatchEmpty", "message": "No codegen batches built."},
            )

        warnings = [
            *warnings,
            f"DEV_CODEGEN_BATCH_PIPELINE_ENABLED;batches={len(batches)}",
        ]
        ai_files: list[DevelopmentCodeFilePayload] = []
        batch_summaries: list[str] = []
        batch_usage: list[dict[str, Any]] = []
        failed_batches: list[str] = []
        successful_batches: set[str] = set()
        provider = profile.provider
        model = profile.model

        # A7.5.1: Run bounded batches concurrently. A7.5 split the prompt, but
        # still executed 4-6 model calls serially, so the frontend could still
        # hit the 330s timeout. This keeps the endpoint synchronous for now,
        # while capping wall-clock time to roughly one/two batch windows.
        semaphore = asyncio.Semaphore(self._codegen_concurrency(batches))

        async def run_one_batch(batch: dict[str, Any]) -> dict[str, Any]:
            async with semaphore:
                try:
                    return await self._run_codegen_batch(request, batch)
                except Exception as exc:
                    return await self._run_codegen_batch_with_adaptive_split(request, batch, exc)

        batch_results = await asyncio.gather(
            *(run_one_batch(batch) for batch in batches),
            return_exceptions=True,
        )

        for index, batch_result in enumerate(batch_results):
            batch = batches[index]
            batch_key = str(batch.get("key") or f"batch_{index + 1}")

            if isinstance(batch_result, Exception):
                failed_batches.append(batch_key)
                warnings.append(
                    f"AI_CODEGEN_BATCH_FAILED:{batch_key}:{type(batch_result).__name__}:{str(batch_result)[:300]}"
                )
                continue

            batch_files = batch_result.get("files") or []
            batch_warnings = batch_result.get("warnings") or []

            if batch_files:
                successful_batches.add(batch_key)
                ai_files.extend(batch_files)
                batch_summaries.append(
                    f"{batch_key}:{self._clean_text(batch_result.get('summary')) or 'generated'}"
                )
                warnings.append(f"AI_CODEGEN_BATCH_SUCCEEDED:{batch_key};files={len(batch_files)}")
            else:
                failed_batches.append(batch_key)
                warnings.append(f"AI_CODEGEN_BATCH_EMPTY:{batch_key}")

            warnings.extend(batch_warnings)
            warnings.extend(batch_result.get("llmWarnings") or [])

            usage = batch_result.get("usage") or {}
            provider = self._usage_value(usage, "provider") or provider
            model = self._usage_value(usage, "model") or model
            batch_usage.append(
                {
                    "batch": batch_key,
                    "provider": provider,
                    "model": model,
                    "usage": usage,
                    "fileCount": len(batch_files),
                    "durationSeconds": batch_result.get("durationSeconds"),
                }
            )

        ai_files = self._dedupe_files(ai_files)

        if not ai_files:
            return DevelopmentNodejsCodegenResponse(
                status="AGENT_FAILED",
                generator_mode="LLM_BATCH_CODEGEN_AGENT_FAILED",
                provider=provider,
                model=model,
                summary=(
                    "Development Codegen Agent 分批生成失败，没有任何批次返回可用业务代码文件。"
                    "系统不会用 Java 确定性模板冒充最终业务代码。"
                ),
                warnings=self._dedupe(
                    [
                        "DEV_CODEGEN_AGENT_REQUIRED",
                        "DEV_CODEGEN_BATCH_PIPELINE_FAILED",
                        *warnings,
                    ]
                ),
                files=[],
                usage={
                    "provider": provider,
                    "model": model,
                    "success": False,
                    "batch_count": len(batches),
                    "batch_success_count": 0,
                    "batch_failed_count": len(failed_batches),
                    "batches": batch_usage,
                },
                raw={
                    "error": "BatchCodegenFailed",
                    "message": "All Development Codegen Agent batches failed or returned empty files.",
                    "failedBatches": failed_batches,
                },
            )

        missing_required_batches = sorted(required_batch_keys - successful_batches)
        business_gate = self._validate_business_code_gate(
            request=request,
            ai_files=ai_files,
            successful_batches=successful_batches,
        )

        if missing_required_batches or business_gate.get("blocked"):
            contract_completion_files = self._complete_business_code_from_contract(
                request=request,
                generation_root=request.generation_root,
                existing_files=ai_files,
                business_gate=business_gate,
                missing_required_batches=missing_required_batches,
            )

            if contract_completion_files:
                warnings.append(
                    "DEV_CODEGEN_AGENT_CONTRACT_COMPLETION_APPLIED;"
                    f"files={len(contract_completion_files)};"
                    f"previousMissingRequiredBatches={len(missing_required_batches)}"
                )
                warnings.extend(self._string_list(business_gate.get("warnings")))
                ai_files = self._dedupe_files([*ai_files, *contract_completion_files])
                successful_batches.add("agent_contract_completion")
                failed_batches = [
                    batch_key
                    for batch_key in failed_batches
                    if not str(batch_key).startswith("required_auth_")
                ]
                missing_required_batches = []
                business_gate = self._validate_business_code_gate(
                    request=request,
                    ai_files=ai_files,
                    successful_batches=successful_batches,
                )
                warnings.extend(self._string_list(business_gate.get("warnings")))
                if not business_gate.get("blocked"):
                    warnings.append("DEV_CODEGEN_BUSINESS_GATE_RECOVERED_BY_AGENT_CONTRACT_COMPLETION")

        if missing_required_batches or business_gate.get("blocked"):
            gate_warnings = self._dedupe(
                [
                    "DEV_CODEGEN_BUSINESS_GATE_BLOCKED",
                    *warnings,
                    *self._string_list(business_gate.get("warnings")),
                ]
            )

            return DevelopmentNodejsCodegenResponse(
                status="AGENT_FAILED",
                generator_mode="LLM_BATCH_CODEGEN_BUSINESS_GATE_FAILED",
                provider=provider,
                model=model,
                summary=(
                    "Development Codegen Agent 只生成了项目脚手架或未满足当前节点的最小业务代码门禁，"
                    "系统不会把初始化页面当成最终业务代码。请修复失败批次后重新生成。"
                ),
                warnings=gate_warnings,
                files=[],
                usage={
                    "provider": provider,
                    "model": model,
                    "success": False,
                    "batch_count": len(batches),
                    "batch_success_count": len(successful_batches),
                    "batch_failed_count": len(failed_batches),
                    "required_batches": sorted(required_batch_keys),
                    "missing_required_batches": missing_required_batches,
                    "business_gate": business_gate,
                    "batches": batch_usage,
                },
                raw={
                    "error": "BusinessCodeGateFailed",
                    "message": "Scaffold-only or incomplete business code proposal is not acceptable.",
                    "failedBatches": failed_batches,
                    "missingRequiredBatches": missing_required_batches,
                    "businessGate": business_gate,
                },
            )

        merged_files = self._merge_with_template(
            template_files=deterministic_files,
            llm_files=ai_files,
            warnings=warnings,
        )

        success_count = max(0, len(batches) - len(failed_batches))
        if failed_batches:
            warnings.append("AI_CODEGEN_BATCH_PARTIAL=" + ",".join(failed_batches))

        warnings.append(f"AI_CODEGEN_PROVIDER={provider};MODEL={model}")
        warnings.append(f"AI_CODEGEN_BATCH_SUCCESS_COUNT={success_count}/{len(batches)}")

        summary = (
            f"Development Codegen Agent 已按 {len(batches)} 个批次生成 Node.js 代码贡献，"
            f"成功 {success_count} 个批次，生成或更新 {len(ai_files)} 个业务代码文件。"
        )
        if failed_batches:
            summary += " 部分批次未成功，系统已保留安全模板锚点并在 warnings 中标明，后续可通过验收修复 Agent 继续补齐。"

        if batch_summaries:
            summary += " 批次摘要：" + "；".join(batch_summaries[:6])

        return DevelopmentNodejsCodegenResponse(
            status="AI_GENERATED",
            generator_mode="LLM_BATCH_CODEGEN_WITH_TEMPLATE_GUARDRAILS",
            provider=provider,
            model=model,
            summary=summary,
            warnings=self._dedupe(warnings),
            files=merged_files,
            usage={
                "provider": provider,
                "model": model,
                "success": True,
                "batch_count": len(batches),
                "batch_success_count": success_count,
                "batch_failed_count": len(failed_batches),
                "ai_file_count": len(ai_files),
                "merged_file_count": len(merged_files),
                "batches": batch_usage,
            },
            raw={
                "schema": "development_nodejs_codegen_batch_output",
                "batchPipeline": True,
                "failedBatches": failed_batches,
            },
        )

    async def _run_codegen_batch_with_adaptive_split(
        self,
        request: DevelopmentNodejsCodegenRequest,
        batch: dict[str, Any],
        original_error: Exception,
    ) -> dict[str, Any]:
        child_batches = self._split_batch_for_retry(batch, original_error)
        parent_key = str(batch.get("key") or "batch")

        if not child_batches:
            raise original_error

        split_started_at = asyncio.get_running_loop().time()
        child_files: list[DevelopmentCodeFilePayload] = []
        child_warnings: list[str] = [
            f"AI_CODEGEN_BATCH_ADAPTIVE_SPLIT:{parent_key};children={len(child_batches)};cause={type(original_error).__name__}",
        ]
        child_usage: list[dict[str, Any]] = []
        child_summaries: list[str] = []

        split_semaphore = asyncio.Semaphore(min(3, len(child_batches)))

        async def run_child(child: dict[str, Any]) -> dict[str, Any]:
            async with split_semaphore:
                return await self._run_codegen_batch(request, child)

        results = await asyncio.gather(
            *(run_child(child) for child in child_batches),
            return_exceptions=True,
        )

        for index, result in enumerate(results):
            child = child_batches[index]
            child_key = str(child.get("key") or f"{parent_key}_child_{index + 1}")

            if isinstance(result, Exception):
                child_warnings.append(
                    f"AI_CODEGEN_ADAPTIVE_CHILD_FAILED:{child_key}:{type(result).__name__}:{str(result)[:220]}"
                )
                continue

            files = result.get("files") or []
            if files:
                child_files.extend(files)
                child_summaries.append(f"{child_key}:{len(files)}")
                child_warnings.append(f"AI_CODEGEN_ADAPTIVE_CHILD_SUCCEEDED:{child_key};files={len(files)}")
            else:
                child_warnings.append(f"AI_CODEGEN_ADAPTIVE_CHILD_EMPTY:{child_key}")

            child_warnings.extend(result.get("warnings") or [])
            child_warnings.extend(result.get("llmWarnings") or [])
            child_usage.append(
                {
                    "batch": child_key,
                    "fileCount": len(files),
                    "durationSeconds": result.get("durationSeconds"),
                    "usage": result.get("usage") or {},
                }
            )

        if not child_files:
            raise original_error

        return {
            "batch": parent_key,
            "summary": "原批次失败后已自动拆分并生成：" + "；".join(child_summaries[:8]),
            "warnings": self._dedupe(child_warnings),
            "llmWarnings": [],
            "files": self._dedupe_files(child_files),
            "usage": {
                "adaptiveSplit": True,
                "parentBatch": parent_key,
                "children": child_usage,
            },
            "durationSeconds": round(asyncio.get_running_loop().time() - split_started_at, 3),
        }

    def _split_batch_for_retry(
        self,
        batch: dict[str, Any],
        original_error: Exception,
    ) -> list[dict[str, Any]]:
        parent_key = str(batch.get("key") or "batch")
        target_paths = [
            self._normalize_path(str(path))
            for path in (batch.get("targetPaths") or [])
            if self._normalize_path(str(path))
        ]
        files = batch.get("files") or []
        generation_root = str(batch.get("generationRoot") or "")

        children: list[dict[str, Any]] = []

        if len(target_paths) > 1:
            for index, target_path in enumerate(target_paths):
                related_files = [
                    file
                    for file in files
                    if self._normalize_path(getattr(file, "file_path", "")) == target_path
                    or self._normalize_path(getattr(file, "file_path", "")).endswith("/" + target_path.rsplit("/", 1)[-1])
                ]

                children.append(
                    {
                        "key": f"{parent_key}_retry_file_{index + 1}",
                        "title": f"{batch.get('title') or parent_key} / 自动拆分文件 {index + 1}",
                        "goal": (
                            f"原批次因 {type(original_error).__name__} 失败。现在只生成一个目标文件 {target_path}。"
                            "必须返回完整文件内容，禁止返回 diff 或无关文件。"
                        ),
                        "files": related_files[:1],
                        "targetPaths": [target_path],
                        "generationRoot": generation_root,
                        "required": bool(batch.get("required")),
                        "businessGate": batch.get("businessGate"),
                        "microContract": batch.get("microContract"),
                        "taskKind": batch.get("taskKind") or self._codegen_task_kind(target_path),
                        "retryOf": parent_key,
                    }
                )

            return children

        if len(files) > 1:
            for index, file in enumerate(files):
                path = self._normalize_path(getattr(file, "file_path", ""))
                children.append(
                    {
                        "key": f"{parent_key}_retry_context_{index + 1}",
                        "title": f"{batch.get('title') or parent_key} / 自动拆分上下文 {index + 1}",
                        "goal": (
                            f"原批次因 {type(original_error).__name__} 失败。现在只处理上下文文件 {path}。"
                            "必须生成 targetPaths 内的完整文件内容。"
                        ),
                        "files": [file],
                        "targetPaths": target_paths or [path],
                        "generationRoot": generation_root,
                        "required": bool(batch.get("required")),
                        "businessGate": batch.get("businessGate"),
                        "microContract": batch.get("microContract"),
                        "taskKind": batch.get("taskKind") or self._codegen_task_kind(path),
                        "retryOf": parent_key,
                    }
                )

        return children

    async def _run_codegen_batch(
        self,
        request: DevelopmentNodejsCodegenRequest,
        batch: dict[str, Any],
    ) -> dict[str, Any]:
        batch_key = str(batch.get("key") or "batch")
        started_at = asyncio.get_running_loop().time()
        llm_result = await asyncio.wait_for(
            self.llm_service.generate_structured_json(
                system_prompt=self._batch_system_prompt(batch),
                user_prompt=self._batch_user_prompt(request, batch),
                output_schema_name="development_nodejs_codegen_batch_output",
                output_json_schema=self._output_schema(),
                validate_json=True,
                agent_scope="development",
                llm_task="codegen",
            ),
            timeout=self._batch_timeout_seconds(batch),
        )

        parsed = json.loads(llm_result.text)
        batch_warnings: list[str] = []
        batch_files = self._sanitize_raw_file_maps(
            parsed.get("files"),
            request.generation_root,
            batch_warnings,
        )
        duration_seconds = round(asyncio.get_running_loop().time() - started_at, 3)

        return {
            "batch": batch_key,
            "summary": self._clean_text(parsed.get("summary")),
            "warnings": batch_warnings,
            "llmWarnings": self._string_list(parsed.get("warnings")),
            "files": batch_files,
            "usage": llm_result.usage or {},
            "durationSeconds": duration_seconds,
        }


    def _prepare_development_build_context(
        self,
        request: DevelopmentNodejsCodegenRequest | DevelopmentNodejsRepairRequest,
        warnings: list[str],
    ) -> None:
        build_context = self._extract_development_build_context(request)
        if not build_context:
            return

        handoff_context = request.handoff_context if isinstance(request.handoff_context, dict) else {}
        merged_handoff = dict(handoff_context)
        merged_handoff.setdefault("developmentBuildContext", build_context)
        merged_handoff.setdefault("development_build_context", build_context)

        confirmed_ui_contract = self._confirmed_ui_contract_from_build_context(build_context)
        if confirmed_ui_contract and not isinstance(merged_handoff.get("confirmedUiContract"), dict):
            merged_handoff["confirmedUiContract"] = confirmed_ui_contract
            warnings.append("A9.10.6_DEVELOPMENT_BUILD_CONTEXT_CONFIRMED_UI_CONTRACT_INJECTED")

        request.handoff_context = merged_handoff
        request.development_build_context = build_context
        warnings.append("A9.10.6_DEVELOPMENT_BUILD_CONTEXT_READY")

    def _extract_development_build_context(
        self,
        request: DevelopmentNodejsCodegenRequest | DevelopmentNodejsRepairRequest,
    ) -> dict[str, Any]:
        direct = getattr(request, "development_build_context", None)
        if isinstance(direct, dict) and direct:
            return direct

        handoff_context = request.handoff_context if isinstance(request.handoff_context, dict) else {}
        for key in ["developmentBuildContext", "development_build_context", "buildContext", "build_context"]:
            value = handoff_context.get(key)
            if isinstance(value, dict) and value:
                return value

        return {}

    def _confirmed_ui_contract_from_build_context(
        self,
        build_context: dict[str, Any],
    ) -> dict[str, Any]:
        if not isinstance(build_context, dict) or not build_context:
            return {}

        confirmed = build_context.get("confirmedUiContract")
        if isinstance(confirmed, dict) and confirmed:
            return confirmed

        pages_payload = build_context.get("pages")
        page_items: list[Any] = []

        if isinstance(pages_payload, dict):
            raw_items = pages_payload.get("items") or pages_payload.get("pages")
            if isinstance(raw_items, list):
                page_items = raw_items
        elif isinstance(pages_payload, list):
            page_items = pages_payload

        pages: list[dict[str, Any]] = []
        for index, item in enumerate(page_items):
            if not isinstance(item, dict):
                continue

            contract = item.get("uiContract") or item.get("contract")
            if not isinstance(contract, dict) or not contract:
                continue

            node_payload = item.get("node") if isinstance(item.get("node"), dict) else {}

            pages.append(
                {
                    "nodeId": item.get("nodeId") or node_payload.get("nodeId"),
                    "pageId": item.get("pageId"),
                    "pageKey": item.get("pageKey") or item.get("key") or f"page-{index + 1}",
                    "pageName": item.get("pageName") or item.get("title"),
                    "routePath": item.get("routePath"),
                    "snapshotId": item.get("snapshotId"),
                    "artifactId": item.get("artifactId"),
                    "artifactVersionNo": item.get("artifactVersionNo"),
                    "status": item.get("status"),
                    "uiContract": contract,
                }
            )

        if not pages:
            return {}

        scope = build_context.get("scope") if isinstance(build_context.get("scope"), dict) else {}
        node_type = str(scope.get("nodeType") or "").upper()

        if len(pages) == 1 and node_type == "PAGE":
            single = dict(pages[0].get("uiContract") or {})
            for key in [
                "nodeId",
                "pageId",
                "pageKey",
                "pageName",
                "routePath",
                "snapshotId",
                "artifactId",
                "artifactVersionNo",
                "status",
            ]:
                if pages[0].get(key) is not None:
                    single[key] = pages[0].get(key)
            return single

        return {
            "scopeType": "MODULE",
            "moduleId": scope.get("nodeId"),
            "moduleKey": scope.get("nodeKey"),
            "moduleName": scope.get("nodeName"),
            "pageCount": len(pages),
            "confirmedPageCount": len(pages),
            "pages": pages,
        }


    def _build_codegen_batches(
        self,
        *,
        request: DevelopmentNodejsCodegenRequest,
        deterministic_files: list[DevelopmentCodeFilePayload],
    ) -> list[dict[str, Any]]:
        """
        Phase 10-A7.8 universal adaptive orchestrator.

        Build codegen work as small atomic tasks instead of fixed coarse buckets.
        This prevents the same class of timeout from reappearing when the user's
        project becomes more complex: every generated target is bounded by file
        count, context size and task kind, and failed coarse tasks can be split
        further by the retry path.
        """
        normalized_root = self._normalize_path(request.generation_root)

        explicit_batches = self._build_explicit_business_batches(
            request=request,
            generation_root=normalized_root,
            deterministic_files=deterministic_files,
        )

        # For strongly detected business closures we execute explicit atomic
        # business tasks only. Generic scaffold buckets are not appended because
        # they increase token and wall-clock budgets without improving the
        # current business gate.
        if explicit_batches and self._looks_like_auth_context(request):
            return self._dedupe_batches(explicit_batches)

        business_batches: list[dict[str, Any]] = []
        scaffold_files: list[DevelopmentCodeFilePayload] = []

        for file in sorted(deterministic_files, key=lambda item: (item.priority, item.file_path)):
            path = self._normalize_path(file.file_path)

            if not path or path.endswith("/codemind.generated.json"):
                continue

            if not path.startswith(normalized_root.rstrip("/") + "/"):
                continue

            task_kind = self._codegen_task_kind(path)
            if task_kind == "project_scaffold":
                scaffold_files.append(file)
                continue

            business_batches.append(
                self._file_to_atomic_batch(
                    file=file,
                    task_kind=task_kind,
                    generation_root=normalized_root,
                    required=False,
                )
            )

        scaffold_batches = self._scaffold_batches(
            generation_root=normalized_root,
            files=scaffold_files,
        )

        # Business first, scaffold last. No hard cap of 6: complex modules must
        # not silently drop tasks. Synchronous HTTP remains a later UX concern;
        # the model-facing work here is already bounded and parallelizable.
        return self._dedupe_batches([*business_batches, *scaffold_batches])

    def _codegen_task_kind(self, path: str) -> str:
        lower = self._normalize_path(path).lower()

        if "/frontend/src/pages/" in lower or ("/frontend/src/modules/" in lower and "/pages/" in lower):
            return "frontend_page"

        if lower.endswith(".vue") and "/frontend/" in lower:
            return "frontend_component"

        if "/frontend/src/api/" in lower:
            return "frontend_api_client"

        if "/frontend/src/router/" in lower or lower.endswith("/frontend/src/app.vue") or lower.endswith("/frontend/src/main.js"):
            return "frontend_wiring"

        if "/frontend/" in lower and lower.endswith(".css"):
            return "frontend_style"

        if "/backend/src/modules/" in lower:
            if lower.endswith(".routes.js"):
                return "backend_routes"
            if lower.endswith(".controller.js"):
                return "backend_controller"
            if lower.endswith(".service.js"):
                return "backend_service"
            if lower.endswith(".repository.js"):
                return "backend_repository"
            return "backend_module"

        if "/backend/src/routes/" in lower:
            return "backend_routes"

        if "/backend/src/db/" in lower:
            return "backend_data"

        if "/backend/" in lower:
            return "backend_wiring"

        if "/tests/" in lower:
            return "test_smoke"

        if "/database/" in lower:
            return "database_contract"

        return "project_scaffold"

    def _file_to_atomic_batch(
        self,
        *,
        file: DevelopmentCodeFilePayload,
        task_kind: str,
        generation_root: str,
        required: bool,
    ) -> dict[str, Any]:
        path = self._normalize_path(file.file_path)
        return {
            "key": self._batch_key_for_path(task_kind, path),
            "title": self._batch_title_for_kind(task_kind, path),
            "goal": self._batch_goal_for_kind(task_kind, path),
            "files": [file],
            "targetPaths": [path],
            "generationRoot": generation_root,
            "taskKind": task_kind,
            "required": required,
            "maxFiles": 1,
            "maxChars": min(len(file.content or "") + 1200, 14_000),
        }

    def _scaffold_batches(
        self,
        *,
        generation_root: str,
        files: list[DevelopmentCodeFilePayload],
    ) -> list[dict[str, Any]]:
        batches: list[dict[str, Any]] = []

        for index, chunk in enumerate(self._chunk_files_for_batch(files, 4, 22_000)):
            if not chunk:
                continue

            batch_key = "project_scaffold" if index == 0 else f"project_scaffold_{index + 1}"
            batches.append(
                {
                    "key": batch_key,
                    "title": "项目主干与运行脚本批次",
                    "goal": "只检查和改进当前 chunk 内的 package、README、入口、配置和运行脚本；不要生成业务页面占位内容。",
                    "files": chunk,
                    "targetPaths": [self._normalize_path(file.file_path) for file in chunk],
                    "generationRoot": generation_root,
                    "taskKind": "project_scaffold",
                    "required": False,
                    "maxFiles": 4,
                    "maxChars": 22_000,
                }
            )

        return batches

    def _batch_key_for_path(self, task_kind: str, path: str) -> str:
        suffix = (
            path.replace("\\", "/")
            .replace("/", "_")
            .replace(".", "_")
            .replace("-", "_")
            .strip("_")
            .lower()
        )
        if len(suffix) > 80:
            suffix = suffix[-80:]
        return f"{task_kind}_{suffix}"

    def _batch_title_for_kind(self, task_kind: str, path: str) -> str:
        file_name = path.rsplit("/", 1)[-1]
        titles = {
            "frontend_page": "前端页面原子批次",
            "frontend_component": "前端组件原子批次",
            "frontend_api_client": "前端 API Client 原子批次",
            "frontend_wiring": "前端路由和入口接线批次",
            "frontend_style": "前端样式批次",
            "backend_routes": "后端路由原子批次",
            "backend_controller": "后端 Controller 原子批次",
            "backend_service": "后端 Service 原子批次",
            "backend_repository": "后端 Repository 原子批次",
            "backend_data": "后端数据层批次",
            "backend_wiring": "后端入口接线批次",
            "database_contract": "数据库契约批次",
            "test_smoke": "验收测试原子批次",
        }
        return f"{titles.get(task_kind, '代码文件原子批次')}：{file_name}"

    def _batch_goal_for_kind(self, task_kind: str, path: str) -> str:
        common = f"只生成或改进目标文件 {path}，content 必须是完整文件内容，不返回 diff，不生成无关文件。"
        goals = {
            "frontend_page": "根据已确认 UI_CONTRACT / PAGE_SPEC 生成真实 Vue 页面；保留字段、按钮、状态、跳转和视觉结构，前端通过 API client 请求后端。",
            "frontend_component": "生成真实 Vue 组件或页面局部组件；不得输出“已初始化”“请选择 PAGE”等占位内容。",
            "frontend_api_client": "生成前端 API client；必须调用真实 Node.js 后端接口，不把业务主链路写成前端 mock。",
            "frontend_wiring": "生成前端路由、入口和应用壳层接线；确保页面路由可以真实切换。",
            "frontend_style": "生成可复用基础样式；不要用样式文件承载业务逻辑。",
            "backend_routes": "生成 Express Router 或路由聚合；必须挂载真实业务接口。",
            "backend_controller": "生成 Controller，请求解析和响应装配，不把业务规则散落在路由里。",
            "backend_service": "生成业务服务层；实现状态变更、校验、异常和最小业务闭环。",
            "backend_repository": "生成本地数据访问层；读写 localStore/seed 数据，不引入原生编译依赖。",
            "backend_data": "生成本地数据层和 seed data；seed 仅用于首次运行演示和测试。",
            "backend_wiring": "生成后端入口接线；确保 /api/health 和业务路由可访问。",
            "database_contract": "生成数据契约或 seed 文件；用于后续迁移和验收。",
            "test_smoke": "生成 smoke/acceptance 测试；必须调用真实 Express API，不做浏览器自动化依赖。",
        }
        return f"{goals.get(task_kind, '生成当前原子代码文件。')} {common}"

    def _dedupe_batches(self, batches: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[str] = set()

        for index, batch in enumerate(batches):
            key = str(batch.get("key") or f"batch_{index + 1}")
            original_key = key
            counter = 2
            while key in seen:
                key = f"{original_key}_{counter}"
                counter += 1
            batch["key"] = key
            seen.add(key)
            result.append(batch)

        return result

    def _build_explicit_business_batches(
        self,
        *,
        request: DevelopmentNodejsCodegenRequest,
        generation_root: str,
        deterministic_files: list[DevelopmentCodeFilePayload],
    ) -> list[dict[str, Any]]:
        """
        Build required business closure batches.

        A7.8 keeps authentication as one detected business closure, but each
        output is an atomic file or tiny wiring group. This removes the earlier
        "required_auth_frontend_pages" giant batch that repeatedly timed out.
        Future closures should follow the same pattern: capability -> atomic
        tasks -> business gate, not one prompt per module.
        """
        batches: list[dict[str, Any]] = []

        if self._looks_like_auth_context(request):
            auth_targets = [
                (
                    "required_auth_frontend_core",
                    "认证前端基础接线批次",
                    "AUTH_FRONTEND_CORE",
                    "只生成认证前端基础接线：authApi.js、routes.js、App.vue 和基础样式。不要生成页面组件正文。",
                    [
                        f"{generation_root}/frontend/src/api/authApi.js",
                        f"{generation_root}/frontend/src/router/routes.js",
                        f"{generation_root}/frontend/src/App.vue",
                        f"{generation_root}/frontend/src/shared/styles/app.css",
                    ],
                    [
                        "/frontend/src/App.vue",
                        "/frontend/src/router/routes.js",
                        "/frontend/src/shared/styles/app.css",
                    ],
                ),
                (
                    "required_auth_login_page",
                    "登录页业务页面批次",
                    "AUTH_LOGIN_PAGE",
                    "只生成 LoginPage.vue。必须包含账号/密码输入、登录按钮、去注册入口、错误提示，登录成功调用 authApi.login 后跳转 /user/home。",
                    [f"{generation_root}/frontend/src/pages/LoginPage.vue"],
                    ["/frontend/src/shared/styles/app.css"],
                ),
                (
                    "required_auth_register_page",
                    "注册页业务页面批次",
                    "AUTH_REGISTER_PAGE",
                    "只生成 RegisterPage.vue。必须包含账号、昵称、密码、确认密码输入、注册按钮、返回登录入口、确认密码校验，注册成功跳转 /auth/login。",
                    [f"{generation_root}/frontend/src/pages/RegisterPage.vue"],
                    ["/frontend/src/shared/styles/app.css"],
                ),
                (
                    "required_auth_user_home_page",
                    "用户首页业务页面批次",
                    "AUTH_USER_HOME_PAGE",
                    "只生成 UserHomePage.vue。必须显示当前用户信息、登录状态、退出登录按钮，退出成功调用 authApi.logout 后跳转 /auth/login。",
                    [f"{generation_root}/frontend/src/pages/UserHomePage.vue"],
                    ["/frontend/src/shared/styles/app.css"],
                ),
                (
                    "required_auth_backend_routes",
                    "认证后端路由批次",
                    "AUTH_BACKEND_ROUTES",
                    "只生成 Express 路由文件和路由挂载，必须提供 /api/auth/login、/api/auth/register、/api/auth/logout、/api/auth/me。",
                    [
                        f"{generation_root}/backend/src/modules/auth-module/auth-module.routes.js",
                        f"{generation_root}/backend/src/routes/index.js",
                    ],
                    [
                        "/backend/src/app.js",
                        "/backend/src/routes/index.js",
                        "/backend/src/shared/response.js",
                    ],
                ),
                (
                    "required_auth_backend_service",
                    "认证后端服务批次",
                    "AUTH_BACKEND_SERVICE",
                    "只生成认证业务 service 和 seed 用户；实现注册、登录、退出、当前用户查询。演示密码可明文但必须给出 warning。",
                    [
                        f"{generation_root}/backend/src/modules/auth-module/auth-module.service.js",
                        f"{generation_root}/backend/src/db/seedData.js",
                    ],
                    [
                        "/backend/src/db/localStore.js",
                        "/backend/src/db/seedData.js",
                    ],
                ),
                (
                    "required_auth_smoke_tests",
                    "认证验收测试批次",
                    "AUTH_TESTS",
                    "生成认证链路 smoke test，覆盖 health、register、login、me、logout。测试必须使用 Node fetch 调用真实 Express API。",
                    [
                        f"{generation_root}/tests/smoke.spec.js",
                        f"{generation_root}/database/seed.json",
                        f"{generation_root}/database/schema.json",
                    ],
                    [
                        "/tests/smoke.spec.js",
                        "/database/seed.json",
                        "/database/schema.json",
                    ],
                ),
            ]

            for key, title, micro_contract, goal, target_paths, suffixes in auth_targets:
                batches.append(
                    {
                        "key": key,
                        "title": title,
                        "goal": goal,
                        "files": self._select_context_files(deterministic_files, suffixes),
                        "targetPaths": target_paths,
                        "generationRoot": generation_root,
                        "required": True,
                        "businessGate": "AUTH_LOGIN_REGISTER_HOME",
                        "microContract": micro_contract,
                        "taskKind": "business_closure",
                        "maxFiles": max(1, len(target_paths)),
                        "maxChars": 16_000,
                    }
                )

        return batches

    def _select_context_files(
        self,
        files: list[DevelopmentCodeFilePayload],
        suffixes: list[str],
    ) -> list[DevelopmentCodeFilePayload]:
        selected: list[DevelopmentCodeFilePayload] = []
        for file in files:
            path = self._normalize_path(file.file_path)
            if any(path.endswith(suffix) for suffix in suffixes):
                selected.append(file)
        return selected

    def _looks_like_auth_context(
        self,
        request: DevelopmentNodejsCodegenRequest,
    ) -> bool:
        try:
            context_text = json.dumps(
                {
                    "node": request.node,
                    "remarks": request.remarks,
                    "handoffContext": request.handoff_context,
                },
                ensure_ascii=False,
            )
        except Exception:
            context_text = f"{request.node} {request.remarks} {request.handoff_context}"

        normalized = str(context_text).lower()
        compact = normalized.replace("_", "").replace("-", "").replace(" ", "")

        return any(
            token in compact
            for token in [
                "authmodule",
                "authpage",
                "loginpage",
                "registerpage",
                "认证模块",
                "用户认证",
                "登录页",
                "注册页",
                "登录",
                "注册",
            ]
        )

    def _validate_business_code_gate(
        self,
        *,
        request: DevelopmentNodejsCodegenRequest,
        ai_files: list[DevelopmentCodeFilePayload],
        successful_batches: set[str],
    ) -> dict[str, Any]:
        paths = [self._normalize_path(file.file_path) for file in ai_files]
        content_by_path = {
            self._normalize_path(file.file_path): file.content or ""
            for file in ai_files
        }

        non_scaffold_paths = [
            path
            for path in paths
            if not self._is_scaffold_only_path(path)
        ]

        warnings: list[str] = [
            f"DEV_CODEGEN_NON_SCAFFOLD_FILE_COUNT={len(non_scaffold_paths)}",
        ]

        if self._looks_like_auth_context(request):
            required_suffixes = [
                "/frontend/src/pages/LoginPage.vue",
                "/frontend/src/pages/RegisterPage.vue",
                "/frontend/src/pages/UserHomePage.vue",
                "/frontend/src/api/authApi.js",
                "/frontend/src/router/routes.js",
                "/backend/src/modules/auth-module/auth-module.routes.js",
                "/backend/src/modules/auth-module/auth-module.service.js",
            ]

            missing_suffixes = [
                suffix
                for suffix in required_suffixes
                if not any(path.endswith(suffix) for path in paths)
            ]

            semantic_checks = {
                "hasLoginRoute": self._any_content_contains(content_by_path, ["/auth/login", "login"]),
                "hasRegisterRoute": self._any_content_contains(content_by_path, ["/auth/register", "register"]),
                "hasAuthApi": self._any_content_contains(content_by_path, ["/api/auth/login", "/api/auth/register"]),
                "hasHomeNavigation": self._any_content_contains(content_by_path, ["/user/home", "UserHomePage", "用户首页"]),
            }

            failed_semantics = [
                key
                for key, passed in semantic_checks.items()
                if not passed
            ]

            if missing_suffixes:
                warnings.append("DEV_CODEGEN_AUTH_REQUIRED_FILES_MISSING=" + ",".join(missing_suffixes))

            if failed_semantics:
                warnings.append("DEV_CODEGEN_AUTH_SEMANTIC_CHECKS_FAILED=" + ",".join(failed_semantics))

            return {
                "blocked": bool(missing_suffixes or failed_semantics),
                "type": "AUTH_LOGIN_REGISTER_HOME",
                "missingFiles": missing_suffixes,
                "semanticChecks": semantic_checks,
                "successfulBatches": sorted(successful_batches),
                "warnings": warnings,
            }

        # Generic systemic gate: MODULE codegen cannot be accepted when only
        # package/config/readme/generated-home scaffolding changed.
        node_text = json.dumps(request.node or {}, ensure_ascii=False).upper()
        is_module_like = "MODULE" in node_text and "PAGE" not in node_text

        if is_module_like and len(non_scaffold_paths) < 2:
            warnings.append("DEV_CODEGEN_MODULE_BUSINESS_FILES_TOO_FEW")
            return {
                "blocked": True,
                "type": "GENERIC_MODULE_BUSINESS_GATE",
                "nonScaffoldFiles": non_scaffold_paths,
                "successfulBatches": sorted(successful_batches),
                "warnings": warnings,
            }

        return {
            "blocked": False,
            "type": "GENERIC",
            "nonScaffoldFiles": non_scaffold_paths,
            "successfulBatches": sorted(successful_batches),
            "warnings": warnings,
        }

    def _is_scaffold_only_path(self, path: str) -> bool:
        lower = path.lower()

        scaffold_suffixes = [
            "/package.json",
            "/readme.md",
            "/frontend/index.html",
            "/frontend/vite.config.js",
            "/frontend/src/main.js",
            "/frontend/src/app.vue",
            "/frontend/src/modules/generated/pages/generatedhomepage.vue",
            "/frontend/src/shared/styles/app.css",
            "/backend/package.json",
            "/backend/server.js",
            "/backend/src/app.js",
            "/backend/src/shared/response.js",
            "/database/schema.json",
            "/database/seed.json",
        ]

        return any(lower.endswith(suffix) for suffix in scaffold_suffixes)

    def _any_content_contains(
        self,
        content_by_path: dict[str, str],
        tokens: list[str],
    ) -> bool:
        joined = "\n".join(content_by_path.values()).lower()
        return all(str(token).lower() in joined for token in tokens)

    def _chunk_files_for_batch(
        self,
        files: list[DevelopmentCodeFilePayload],
        max_files: int,
        max_chars: int,
    ) -> list[list[DevelopmentCodeFilePayload]]:
        chunks: list[list[DevelopmentCodeFilePayload]] = []
        current: list[DevelopmentCodeFilePayload] = []
        current_chars = 0

        for file in files:
            content_chars = len(file.content or "")
            if current and (len(current) >= max_files or current_chars + content_chars > max_chars):
                chunks.append(current)
                current = []
                current_chars = 0

            current.append(file)
            current_chars += content_chars

        if current:
            chunks.append(current)

        return chunks

    def _batch_timeout_seconds(self, batch: dict[str, Any]) -> int:
        key = str(batch.get("key") or "")
        target_count = len(batch.get("targetPaths") or [])
        task_kind = str(batch.get("taskKind") or "")

        # Bound every model call. A large project should create more small tasks,
        # not one long-running prompt that blocks the whole endpoint.
        if batch.get("retryOf"):
            return 65

        if key.startswith("required_auth_login_page") or key.startswith("required_auth_register_page") or key.startswith("required_auth_user_home_page"):
            return 90

        if key.startswith("required_auth_frontend_core"):
            return 90

        if key.startswith("required_auth_backend"):
            return 95

        if key.startswith("required_auth_smoke_tests"):
            return 80

        if task_kind == "project_scaffold" or key.startswith("project_scaffold"):
            return 45

        if target_count <= 1:
            return 70

        return 80

    def _codegen_concurrency(self, batches: list[dict[str, Any]]) -> int:
        # Qwen free quota has high latency variability; six small calls is a
        # safer ceiling than launching unbounded parallel requests.
        if not batches:
            return 1
        return min(6, max(2, len(batches)))

    def _batch_system_prompt(self, batch: dict[str, Any]) -> str:
        if batch.get("businessGate") == "AUTH_LOGIN_REGISTER_HOME":
            return f"""
你是 CodeMind Studio 的 Development Codegen Agent，正在生成认证模块的一个微批次业务代码。

批次：{batch.get("title")}
目标：{batch.get("goal")}

硬性要求：
1. 只返回 JSON，不返回 Markdown、解释文字或 diff。
2. files 数组不能为空。
3. 每个文件必须位于 generationRoot 之下，不能使用绝对路径、../、node_modules、dist、target。
4. 每个 content 必须是完整文件内容。
5. 认证主链路必须是真前后端调用：前端 fetch /api/auth/*，后端 Express Router + service 处理。
6. 不允许只生成“项目已初始化”“请选择 PAGE”的占位页。
7. 不引入 vue-router、pinia、axios、sqlite 等新依赖；使用 Vue 3、Vite、Express、CORS 和纯 JS。
8. Login 成功跳转 /user/home；Register 成功跳转 /auth/login；Logout 跳转 /auth/login。
""".strip()

        required_text = "这是必需业务批次，失败或返回空文件会阻断代码提案。" if batch.get("required") else "这是普通补充批次。"

        return f"""
你是 CodeMind Studio 的 Development Codegen Agent，正在执行 Node.js 全栈项目代码生成的一个小批次。

批次：{batch.get("title")}
目标：{batch.get("goal")}
批次等级：{required_text}

必须遵守：
1. 只能返回当前批次相关的完整文件内容，禁止返回 Markdown、解释文字或 diff。
2. 每个文件必须位于 generationRoot 之下，不能使用绝对路径、../、.git、node_modules、target、dist、build。
3. 优先返回 targetPaths 中的文件；只有在确有必要时才补充同一模块目录下的新文件。
4. 前端必须调用 Node.js 后端 API，不允许把业务主链路写成前端 mock。
5. 后端必须访问本地数据层；seed data 只用于首次运行演示和验收测试。
6. PAGE 节点必须尊重已确认 UI_CONTRACT 的 layout、componentTree、componentId、fieldKey、actionId、pageStates、navigationTargets、隐藏原型上下文和文案层级。
7. 不要返回 codemind.generated.json；它由 Java 侧生成和合并。
8. 不引入 native 编译依赖；第一版使用 Vite/Vue + Express + 纯 JS localStore。
9. 输出 JSON 必须满足 schema，files 数组不能为空。
10. 如果 targetPaths 包含 LoginPage.vue、RegisterPage.vue、authApi.js 或 auth-module.service.js，必须全部返回完整实现。
11. 登录页必须有账号/密码输入、登录按钮、去注册入口；登录成功必须跳转 /user/home。
12. 注册页必须有账号/昵称/密码/确认密码输入、注册按钮、返回登录入口；注册成功必须跳转 /auth/login 或 /user/home。
13. 后端必须提供 /api/auth/login、/api/auth/register、/api/auth/logout、/api/auth/me。
14. 不能生成仅显示“已初始化”“请选择 PAGE”的占位页面作为业务代码。
""".strip()

    def _auth_business_codegen_contract(
        self,
        request: DevelopmentNodejsCodegenRequest,
        batch: dict[str, Any],
    ) -> dict[str, Any]:
        # A7.7.1 missing-helper fix.
        # Keep auth codegen input small and executable. Do not pass the full
        # DEV_HANDOFF_PACKAGE / UI snapshot to every model call.
        micro_contract = str(batch.get("microContract") or "AUTH")
        return {
            "contractVersion": "A7.7_AUTH_MICRO_BATCH",
            "microContract": micro_contract,
            "businessGoal": "认证模块提供登录、注册、退出登录、登录状态查询和登录成功进入用户首页的最小业务闭环。",
            "routes": {
                "login": "/auth/login",
                "register": "/auth/register",
                "home": "/user/home",
            },
            "api": {
                "login": {"method": "POST", "path": "/api/auth/login", "body": ["account", "password"]},
                "register": {"method": "POST", "path": "/api/auth/register", "body": ["account", "nickname", "password", "confirmPassword"]},
                "logout": {"method": "POST", "path": "/api/auth/logout"},
                "me": {"method": "GET", "path": "/api/auth/me"},
            },
            "frontendRequirements": [
                "LoginPage.vue: account/password fields, login button, goto register link, show error message, successful login stores returned user/session and navigates to /user/home.",
                "RegisterPage.vue: account/nickname/password/confirmPassword fields, register button, back to login link, confirm password validation, successful register navigates to /auth/login.",
                "UserHomePage.vue: show current user nickname/account, login status, logout button, logout navigates to /auth/login.",
                "authApi.js: fetch wrapper for /api/auth/login/register/logout/me with JSON request and response handling.",
                "router/routes.js: export /, /auth/login, /auth/register, /user/home routes.",
                "App.vue: simple client-side route switch by window.location.pathname and navigation helper, no vue-router dependency.",
            ],
            "backendRequirements": [
                "auth-module.service.js: local in-memory/file-backed user registration, login validation, demo session state, logout, current user.",
                "auth-module.routes.js: Express Router exposing /login, /register, /logout, /me.",
                "routes/index.js: mount auth router at /api/auth and keep /api/health available.",
                "seedData.js: include at least one demo user with account demo, password demo123, nickname 演示用户.",
            ],
            "testRequirements": [
                "smoke.spec.js must call http://127.0.0.1:3101/api/health, /api/auth/register, /api/auth/login, /api/auth/me and /api/auth/logout.",
                "Tests should be deterministic and should not require browser automation.",
            ],
            "strictOutputRules": [
                "Return JSON only.",
                "files must not be empty.",
                "Each file content must be complete, not a patch.",
                "Do not return GeneratedHomePage-only scaffold.",
                "Do not add dependencies other than Vue/Vite/Express/CORS already available.",
            ],
        }

    def _compact_template_files_for_micro_batch(
        self,
        files: list[DevelopmentCodeFilePayload],
        *,
        max_files: int = 5,
        max_content_chars: int = 1200,
    ) -> list[dict[str, Any]]:
        compact: list[dict[str, Any]] = []
        for file in files[:max_files]:
            compact.append(
                {
                    "file_path": file.file_path,
                    "file_type": file.file_type,
                    "source": file.source,
                    "language": file.language,
                    "content": (file.content or "")[:max_content_chars],
                }
            )
        return compact

    def _batch_user_prompt(
        self,
        request: DevelopmentNodejsCodegenRequest,
        batch: dict[str, Any],
    ) -> str:
        if batch.get("businessGate") == "AUTH_LOGIN_REGISTER_HOME":
            payload = {
                "projectCode": request.project_code,
                "targetStack": request.target_stack,
                "generationRoot": request.generation_root,
                "batch": {
                    "key": batch.get("key"),
                    "title": batch.get("title"),
                    "goal": batch.get("goal"),
                    "targetPaths": batch.get("targetPaths"),
                    "microContract": batch.get("microContract"),
                },
                "authBusinessContract": self._auth_business_codegen_contract(request, batch),
                "existingFiles": self._compact_template_files_for_micro_batch(batch.get("files", [])),
            }

            return (
                "你正在执行一个很小的认证业务代码生成批次。"
                "不要总结长文档，不要重新解释需求。只按 authBusinessContract 和 targetPaths 生成完整文件。"
                "返回 JSON：summary、warnings、files。files 中每个 content 必须是完整文件内容。\n\n"
                + json.dumps(payload, ensure_ascii=False)
            )

        payload = {
            "projectCode": request.project_code,
            "targetStack": request.target_stack,
            "generationRoot": request.generation_root,
            "node": request.node,
            "remarks": request.remarks,
            "batch": {
                "key": batch.get("key"),
                "title": batch.get("title"),
                "goal": batch.get("goal"),
                "targetPaths": batch.get("targetPaths"),
            },
            "handoffContext": self._compact_codegen_context(request.handoff_context),
            "templateFiles": self._compact_template_files_for_micro_batch(batch.get("files", []), max_files=3, max_content_chars=4000),
        }

        instruction = (
            "请只处理当前批次的文件。"
            "返回 JSON：summary、warnings、files。files 中每个 content 必须是完整文件内容。\n\n"
        )

        return instruction + json.dumps(payload, ensure_ascii=False)

    def _compact_codegen_context(self, value: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(value, dict):
            return {}

        result: dict[str, Any] = {}

        for key in [
            "projectId",
            "moduleId",
            "moduleKey",
            "moduleName",
            "moduleType",
            "nodeType",
            "targetStack",
        ]:
            if key in value:
                result[key] = value.get(key)

        readiness = value.get("readiness")
        if isinstance(readiness, dict):
            result["readiness"] = {
                "ready": readiness.get("ready"),
                "score": readiness.get("score"),
                "nodeType": readiness.get("nodeType"),
                "moduleName": readiness.get("moduleName"),
                "assets": readiness.get("assets"),
                "blockers": readiness.get("blockers"),
                "warnings": readiness.get("warnings"),
            }

        artifacts = value.get("artifacts")
        if isinstance(artifacts, list):
            compact_artifacts: list[dict[str, Any]] = []
            for artifact in artifacts[:16]:
                if not isinstance(artifact, dict):
                    continue
                markdown = artifact.get("contentMarkdown") or artifact.get("content") or ""
                compact_artifacts.append(
                    {
                        "artifactType": artifact.get("artifactType"),
                        "title": artifact.get("title"),
                        "required": artifact.get("required"),
                        "status": artifact.get("status"),
                        "contentMarkdown": str(markdown)[:3_000],
                    }
                )
            result["artifacts"] = compact_artifacts

        development_build_context = (
            value.get("developmentBuildContext")
            or value.get("development_build_context")
        )
        if isinstance(development_build_context, dict):
            result["developmentBuildContext"] = self._truncate_json_like(development_build_context, 32_000)

        confirmed_ui_contract = value.get("confirmedUiContract")
        if isinstance(confirmed_ui_contract, dict):
            result["confirmedUiContract"] = self._truncate_json_like(confirmed_ui_contract, 24_000)

        theme_profile = value.get("themeProfileSnapshot")
        if isinstance(theme_profile, dict):
            result["themeProfileSnapshot"] = self._truncate_json_like(theme_profile, 8_000)

        confirmed_plan = value.get("confirmedDevelopmentPlan")
        if isinstance(confirmed_plan, dict):
            result["confirmedDevelopmentPlan"] = self._truncate_json_like(confirmed_plan, 10_000)

        return result

    def _truncate_json_like(self, value: Any, max_chars: int) -> Any:
        try:
            encoded = json.dumps(value or {}, ensure_ascii=False)
            if len(encoded) <= max_chars:
                return value
            return {"truncated": True, "text": encoded[:max_chars]}
        except Exception:
            return {"truncated": True, "text": str(value)[:max_chars]}

    def _complete_business_code_from_contract(
        self,
        *,
        request: DevelopmentNodejsCodegenRequest,
        generation_root: str,
        existing_files: list[DevelopmentCodeFilePayload],
        business_gate: dict[str, Any],
        missing_required_batches: list[str],
    ) -> list[DevelopmentCodeFilePayload]:
        """
        Phase 10-A7.8.1.

        LLM still performs the Development Codegen attempt. When a required
        business closure is only partially returned, the Python Development
        Codegen Agent completes the minimal runnable contract so Java templates
        never masquerade as final business code.
        """
        if not self._looks_like_auth_context(request):
            return []

        return self._auth_contract_completion_files(self._normalize_path(generation_root))

    def _auth_contract_completion_files(
        self,
        generation_root: str,
    ) -> list[DevelopmentCodeFilePayload]:
        root = self._normalize_path(generation_root)

        return [
            self._contract_file(f"{root}/frontend/src/api/authApi.js", "FRONTEND", "javascript", "AGENT_CONTRACT_COMPLETION", "认证前端 API client，真实调用 Node.js 后端认证接口。", self._auth_api_js(), 21),
            self._contract_file(f"{root}/frontend/src/router/routes.js", "FRONTEND", "javascript", "AGENT_CONTRACT_COMPLETION", "认证页面路由表。", self._auth_routes_js(), 22),
            self._contract_file(f"{root}/frontend/src/App.vue", "FRONTEND", "vue", "AGENT_CONTRACT_COMPLETION", "认证应用壳层，使用 pathname 做轻量路由切换。", self._auth_app_vue(), 23),
            self._contract_file(f"{root}/frontend/src/shared/styles/app.css", "FRONTEND", "css", "AGENT_CONTRACT_COMPLETION", "认证业务页面基础样式。", self._auth_app_css(), 24),
            self._contract_file(f"{root}/frontend/src/pages/LoginPage.vue", "FRONTEND", "vue", "AGENT_CONTRACT_COMPLETION", "登录页业务实现。", self._auth_login_page_vue(), 25),
            self._contract_file(f"{root}/frontend/src/pages/RegisterPage.vue", "FRONTEND", "vue", "AGENT_CONTRACT_COMPLETION", "注册页业务实现。", self._auth_register_page_vue(), 26),
            self._contract_file(f"{root}/frontend/src/pages/UserHomePage.vue", "FRONTEND", "vue", "AGENT_CONTRACT_COMPLETION", "用户首页业务实现。", self._auth_user_home_page_vue(), 27),
            self._contract_file(f"{root}/backend/src/routes/index.js", "BACKEND", "javascript", "AGENT_CONTRACT_COMPLETION", "后端认证路由挂载入口。", self._auth_backend_routes_index_js(), 31),
            self._contract_file(f"{root}/backend/src/modules/auth-module/auth-module.routes.js", "BACKEND", "javascript", "AGENT_CONTRACT_COMPLETION", "Express 认证路由。", self._auth_module_routes_js(), 32),
            self._contract_file(f"{root}/backend/src/modules/auth-module/auth-module.service.js", "BACKEND", "javascript", "AGENT_CONTRACT_COMPLETION", "认证业务服务。", self._auth_module_service_js(), 33),
            self._contract_file(f"{root}/backend/src/db/seedData.js", "BACKEND", "javascript", "AGENT_CONTRACT_COMPLETION", "认证 seed 用户。", self._auth_seed_data_js(), 34),
            self._contract_file(f"{root}/database/schema.json", "DATABASE", "json", "AGENT_CONTRACT_COMPLETION", "认证数据契约。", self._auth_schema_json(), 41),
            self._contract_file(f"{root}/database/seed.json", "DATABASE", "json", "AGENT_CONTRACT_COMPLETION", "认证演示 seed data。", self._auth_seed_json(), 42),
            self._contract_file(f"{root}/tests/smoke.spec.js", "TEST", "javascript", "AGENT_CONTRACT_COMPLETION", "认证链路 smoke test。", self._auth_smoke_spec_js(), 51),
        ]

    def _contract_file(
        self,
        file_path: str,
        file_type: str,
        language: str,
        source: str,
        description: str,
        content: str,
        priority: int,
    ) -> DevelopmentCodeFilePayload:
        return DevelopmentCodeFilePayload(
            file_path=self._normalize_path(file_path),
            file_type=file_type,
            action="CREATE_OR_UPDATE",
            source=source,
            description=description,
            language=language,
            content=content.strip() + "\n",
            priority=priority,
        )

    def _auth_api_js(self) -> str:
        return """
const JSON_HEADERS = { 'Content-Type': 'application/json' }

export async function login(payload) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
}

export async function register(payload) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
}

export async function logout() {
  return request('/api/auth/logout', { method: 'POST' })
}

export async function getCurrentUser() {
  return request('/api/auth/me')
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: JSON_HEADERS,
    ...options,
  })

  const body = await response.json().catch(() => ({}))

  if (!response.ok || body.success === false) {
    throw new Error(body?.error?.message || '请求失败')
  }

  return body.data
}
"""

    def _auth_routes_js(self) -> str:
        return """
import LoginPage from '../pages/LoginPage.vue'
import RegisterPage from '../pages/RegisterPage.vue'
import UserHomePage from '../pages/UserHomePage.vue'

export const generatedRoutes = [
  { path: '/', redirect: '/auth/login', name: 'Root', component: LoginPage },
  { path: '/auth/login', name: 'LoginPage', title: '登录页', component: LoginPage },
  { path: '/auth/register', name: 'RegisterPage', title: '注册页', component: RegisterPage },
  { path: '/user/home', name: 'UserHomePage', title: '用户首页', component: UserHomePage },
]
"""

    def _auth_app_vue(self) -> str:
        return """
<template>
  <main class="auth-app-shell">
    <aside class="auth-sidebar">
      <strong>CodeMind Generated App</strong>
      <span>Node.js Fullstack</span>
      <nav class="auth-nav">
        <a href="/auth/login">登录</a>
        <a href="/auth/register">注册</a>
        <a href="/user/home">用户首页</a>
      </nav>
    </aside>
    <section class="auth-app-content">
      <component :is="activeComponent" />
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { generatedRoutes } from './router/routes.js'

const activeComponent = computed(() => {
  const pathname = window.location.pathname || '/'
  const matched = generatedRoutes.find((route) => route.path === pathname)

  if (matched?.redirect) {
    window.location.replace(matched.redirect)
    return generatedRoutes.find((route) => route.path === matched.redirect)?.component || generatedRoutes[1].component
  }

  return matched?.component || generatedRoutes.find((route) => route.path === '/auth/login')?.component
})
</script>
"""

    def _auth_app_css(self) -> str:
        return """
* { box-sizing: border-box; }
body {
  margin: 0;
  min-width: 320px;
  font-family: Inter, "Microsoft YaHei", Arial, sans-serif;
  color: #0f172a;
  background: linear-gradient(135deg, #eff6ff 0%, #f8fafc 45%, #e0f2fe 100%);
}
button, input { font: inherit; }
.auth-app-shell { min-height: 100vh; display: grid; grid-template-columns: 240px minmax(0, 1fr); }
.auth-sidebar { padding: 28px 24px; color: #fff; background: #0f172a; display: flex; flex-direction: column; gap: 10px; }
.auth-sidebar span { color: #cbd5e1; font-size: 13px; }
.auth-nav { display: grid; gap: 8px; margin-top: 20px; }
.auth-nav a { color: #dbeafe; text-decoration: none; font-weight: 800; }
.auth-app-content { min-height: 100vh; display: grid; place-items: center; padding: 40px; }
.auth-card, .auth-dashboard {
  width: min(460px, 100%);
  padding: 32px;
  border: 1px solid rgba(148, 163, 184, .35);
  border-radius: 28px;
  background: rgba(255,255,255,.92);
  box-shadow: 0 30px 70px rgba(15,23,42,.16);
}
.auth-dashboard { width: min(960px, 100%); }
.eyebrow { margin: 0 0 8px; color: #2563eb; font-size: 12px; font-weight: 900; letter-spacing: .08em; text-transform: uppercase; }
h1 { margin: 0; font-size: 30px; }
.subtle { margin: 10px 0 0; color: #64748b; line-height: 1.7; }
.form-grid { display: grid; gap: 14px; margin-top: 24px; }
.form-grid label { display: grid; gap: 8px; font-size: 14px; font-weight: 900; color: #334155; }
.form-grid input { width: 100%; border: 1px solid #cbd5e1; border-radius: 14px; padding: 13px 14px; background: #fff; }
.primary-action { width: 100%; border: 0; border-radius: 14px; padding: 13px 16px; color: #fff; background: linear-gradient(135deg, #2563eb, #0ea5e9); font-weight: 900; cursor: pointer; box-shadow: 0 14px 25px rgba(37, 99, 235, .22); }
.secondary-link { border: 0; padding: 0; background: transparent; color: #2563eb; font-weight: 900; cursor: pointer; }
.status-message { margin-top: 14px; color: #475569; }
.error-message { margin-top: 14px; color: #dc2626; font-weight: 800; }
.dashboard-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-top: 24px; }
.info-tile { padding: 18px; border: 1px solid #dbeafe; border-radius: 18px; background: #f8fafc; }
.info-tile span { display: block; color: #64748b; font-size: 13px; }
.info-tile strong { display: block; margin-top: 6px; font-size: 20px; }
@media (max-width: 820px) {
  .auth-app-shell { grid-template-columns: 1fr; }
  .auth-sidebar { display: none; }
  .auth-app-content { padding: 20px; }
  .dashboard-grid { grid-template-columns: 1fr; }
}
"""

    def _auth_login_page_vue(self) -> str:
        return """
<template>
  <section class="auth-card">
    <p class="eyebrow">Welcome Back</p>
    <h1>欢迎登录</h1>
    <p class="subtle">请输入账号与密码进入业务系统。演示账号 demo，密码 demo123。</p>

    <form class="form-grid" @submit.prevent="handleLogin">
      <label>账号<input v-model.trim="form.account" autocomplete="username" placeholder="请输入账号" /></label>
      <label>密码<input v-model="form.password" autocomplete="current-password" type="password" placeholder="请输入密码" /></label>
      <button class="primary-action" type="submit" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
    </form>

    <p v-if="message" class="status-message">{{ message }}</p>
    <p v-if="error" class="error-message">{{ error }}</p>
    <p class="status-message">没有账号？ <button class="secondary-link" type="button" @click="goRegister">去注册</button></p>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { login } from '../api/authApi.js'

const loading = ref(false)
const message = ref('')
const error = ref('')
const form = reactive({ account: 'demo', password: 'demo123' })

async function handleLogin() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await login({ account: form.account || 'demo', password: form.password || 'demo123' })
    localStorage.setItem('codemind.auth.user', JSON.stringify(result.user || result))
    message.value = '登录成功，正在进入用户首页。'
    window.location.href = '/user/home'
  } catch (err) {
    error.value = err?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

function goRegister() {
  window.location.href = '/auth/register'
}
</script>
"""

    def _auth_register_page_vue(self) -> str:
        return """
<template>
  <section class="auth-card">
    <p class="eyebrow">Create Account</p>
    <h1>注册账号</h1>
    <p class="subtle">填写账号、昵称和密码完成注册，注册成功后返回登录页。</p>

    <form class="form-grid" @submit.prevent="handleRegister">
      <label>账号<input v-model.trim="form.account" autocomplete="username" placeholder="请输入账号" /></label>
      <label>昵称<input v-model.trim="form.nickname" placeholder="请输入昵称" /></label>
      <label>密码<input v-model="form.password" type="password" autocomplete="new-password" placeholder="请输入密码" /></label>
      <label>确认密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" placeholder="请再次输入密码" /></label>
      <button class="primary-action" type="submit" :disabled="loading">{{ loading ? '注册中...' : '注册' }}</button>
    </form>

    <p v-if="message" class="status-message">{{ message }}</p>
    <p v-if="error" class="error-message">{{ error }}</p>
    <p class="status-message">已有账号？ <button class="secondary-link" type="button" @click="goLogin">返回登录</button></p>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { register } from '../api/authApi.js'

const loading = ref(false)
const message = ref('')
const error = ref('')
const form = reactive({ account: '', nickname: '', password: '', confirmPassword: '' })

async function handleRegister() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    if (!form.account) throw new Error('账号不能为空')
    if (!form.password) throw new Error('密码不能为空')
    if (form.password !== form.confirmPassword) throw new Error('两次输入的密码不一致')
    await register({ ...form, nickname: form.nickname || form.account })
    message.value = '注册成功，正在返回登录页。'
    window.location.href = '/auth/login'
  } catch (err) {
    error.value = err?.message || '注册失败'
  } finally {
    loading.value = false
  }
}

function goLogin() {
  window.location.href = '/auth/login'
}
</script>
"""

    def _auth_user_home_page_vue(self) -> str:
        return """
<template>
  <section class="auth-dashboard">
    <p class="eyebrow">Dashboard</p>
    <h1>用户首页</h1>
    <p class="subtle">集中展示当前用户的核心信息、快捷入口和登录状态。</p>

    <div class="dashboard-grid">
      <article class="info-tile"><span>当前账号</span><strong>{{ user.account || '-' }}</strong></article>
      <article class="info-tile"><span>用户昵称</span><strong>{{ user.nickname || '-' }}</strong></article>
      <article class="info-tile"><span>登录状态</span><strong>{{ authenticated ? '已登录' : '未登录' }}</strong></article>
    </div>

    <form class="form-grid" @submit.prevent="handleRefresh">
      <button class="primary-action" type="submit" :disabled="loading">{{ loading ? '刷新中...' : '刷新用户信息' }}</button>
      <button class="secondary-link" type="button" @click="handleLogout">退出登录</button>
    </form>

    <p v-if="message" class="status-message">{{ message }}</p>
    <p v-if="error" class="error-message">{{ error }}</p>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getCurrentUser, logout } from '../api/authApi.js'

const loading = ref(false)
const authenticated = ref(false)
const message = ref('')
const error = ref('')
const user = reactive({ account: '', nickname: '' })

onMounted(() => { handleRefresh() })

async function handleRefresh() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await getCurrentUser()
    const current = result.user || result
    user.account = current.account || 'demo'
    user.nickname = current.nickname || '演示用户'
    authenticated.value = true
    message.value = '用户信息已刷新。'
  } catch (err) {
    const cached = JSON.parse(localStorage.getItem('codemind.auth.user') || '{}')
    user.account = cached.account || 'demo'
    user.nickname = cached.nickname || '演示用户'
    authenticated.value = Boolean(user.account)
    error.value = err?.message || ''
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
  try {
    await logout()
  } finally {
    localStorage.removeItem('codemind.auth.user')
    window.location.href = '/auth/login'
  }
}
</script>
"""

    def _auth_backend_routes_index_js(self) -> str:
        return """
import { router as authRouter } from '../modules/auth-module/auth-module.routes.js'

export function registerGeneratedRoutes(app) {
  app.use('/api/auth', authRouter)
}
"""

    def _auth_module_routes_js(self) -> str:
        return """
import { Router } from 'express'
import { getCurrentUser, login, logout, register } from './auth-module.service.js'
import { sendOk } from '../../shared/response.js'

export const router = Router()

router.post('/login', async (req, res, next) => {
  try { sendOk(res, await login(req.body || {})) } catch (error) { next(error) }
})

router.post('/register', async (req, res, next) => {
  try { sendOk(res, await register(req.body || {})) } catch (error) { next(error) }
})

router.post('/logout', async (_req, res, next) => {
  try { sendOk(res, await logout()) } catch (error) { next(error) }
})

router.get('/me', async (_req, res, next) => {
  try { sendOk(res, await getCurrentUser()) } catch (error) { next(error) }
})
"""

    def _auth_module_service_js(self) -> str:
        return """
import { seedData } from '../../db/seedData.js'

const users = [...(seedData.authUsers || [])]
let currentSession = users.length > 0 ? toSafeUser(users[0]) : null

export async function register(payload = {}) {
  const account = String(payload.account || '').trim()
  const nickname = String(payload.nickname || account || '').trim()
  const password = String(payload.password || '').trim()
  const confirmPassword = String(payload.confirmPassword || password).trim()

  if (!account) throw badRequest('账号不能为空。')
  if (!password) throw badRequest('密码不能为空。')
  if (password !== confirmPassword) throw badRequest('两次输入的密码不一致。')
  if (users.some((user) => user.account === account)) throw badRequest('账号已存在。')

  const nextUser = { id: `user-${Date.now()}`, account, nickname: nickname || account, password, status: 'ACTIVE', createdAt: new Date().toISOString() }
  users.push(nextUser)
  return { user: toSafeUser(nextUser), message: '注册成功。' }
}

export async function login(payload = {}) {
  const account = String(payload.account || 'demo').trim()
  const password = String(payload.password || 'demo123').trim()
  const matched = users.find((user) => user.account === account && user.password === password)

  if (!matched) {
    const error = new Error('账号或密码错误。')
    error.status = 401
    throw error
  }

  currentSession = toSafeUser(matched)
  return { user: currentSession, token: `demo-token-${Date.now()}`, message: '登录成功。' }
}

export async function logout() {
  currentSession = null
  return { message: '已退出登录。' }
}

export async function getCurrentUser() {
  return { authenticated: Boolean(currentSession), user: currentSession || toSafeUser(users[0]) }
}

function toSafeUser(user) {
  if (!user) return null
  return { id: user.id, account: user.account, nickname: user.nickname, status: user.status || 'ACTIVE' }
}

function badRequest(message) {
  const error = new Error(message)
  error.status = 400
  return error
}
"""

    def _auth_seed_data_js(self) -> str:
        return """
export const seedData = {
  authUsers: [
    { id: 'user-demo-001', account: 'demo', nickname: '演示用户', password: 'demo123', status: 'ACTIVE', createdAt: '2026-07-30T00:00:00.000Z' },
  ],
  records: [],
}
"""

    def _auth_schema_json(self) -> str:
        return """
{
  "schemaVersion": "1.0",
  "entities": [
    {
      "name": "AuthUser",
      "description": "认证用户。",
      "fields": [
        { "name": "id", "type": "string", "required": true },
        { "name": "account", "type": "string", "required": true },
        { "name": "nickname", "type": "string", "required": true },
        { "name": "password", "type": "string", "required": true },
        { "name": "status", "type": "enum", "values": ["ACTIVE", "DISABLED"], "required": true }
      ]
    }
  ]
}
"""

    def _auth_seed_json(self) -> str:
        return """
{
  "authUsers": [
    { "id": "user-demo-001", "account": "demo", "nickname": "演示用户", "password": "demo123", "status": "ACTIVE" }
  ]
}
"""

    def _auth_smoke_spec_js(self) -> str:
        return """
const baseUrl = 'http://127.0.0.1:3101'

async function main() {
  const suffix = Date.now()
  const account = `demo_${suffix}`

  await assertOk(fetch(`${baseUrl}/api/health`), 'health should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, nickname: '自动验收用户', password: 'demo123', confirmPassword: 'demo123' }),
  }), 'register should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password: 'demo123' }),
  }), 'login should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/me`), 'me should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/logout`, { method: 'POST' }), 'logout should be ok')

  console.log('Auth smoke test passed.')
}

async function assertOk(promise, message) {
  const response = await promise
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`${message}: ${response.status} ${body}`)
  }
  return response
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
"""

    def _dedupe_files(
        self,
        files: list[DevelopmentCodeFilePayload],
    ) -> list[DevelopmentCodeFilePayload]:
        by_path: dict[str, DevelopmentCodeFilePayload] = {}
        for file in files:
            path = self._normalize_path(file.file_path)
            if not path:
                continue
            by_path[path] = DevelopmentCodeFilePayload(
                file_path=path,
                file_type=file.file_type,
                action=file.action,
                source=file.source,
                description=file.description,
                language=file.language,
                content=file.content,
                priority=file.priority,
            )

        return sorted(by_path.values(), key=lambda item: (item.priority, item.file_path))

    async def repair_nodejs_from_acceptance(
        self,
        request: DevelopmentNodejsRepairRequest,
    ) -> DevelopmentNodejsCodegenResponse:
        profile = self.model_router.resolve(agent_scope="development", llm_task="codegen")
        warnings: list[str] = []
        self._prepare_development_build_context(request, warnings)
        current_files = self._sanitize_files(
            request.current_files,
            request.generation_root,
            warnings,
        )

        if not current_files:
            return DevelopmentNodejsCodegenResponse(
                status="REPAIR_NOT_READY",
                generator_mode="REPAIR_CONTEXT_EMPTY",
                provider=profile.provider,
                model=profile.model,
                summary="没有收到可用于修复的当前代码文件，未生成修复提案。",
                warnings=["DEVELOPMENT_REPAIR_EMPTY_CURRENT_FILES", *warnings],
                files=[],
                usage={"provider": profile.provider, "model": profile.model, "success": False},
            )

        try:
            llm_result = await asyncio.wait_for(
                self.llm_service.generate_structured_json(
                    system_prompt=self._repair_system_prompt(),
                    user_prompt=self._repair_user_prompt(request, current_files),
                    output_schema_name="development_nodejs_repair_output",
                    output_json_schema=self._output_schema(),
                    validate_json=True,
                    agent_scope="development",
                    llm_task="codegen",
                ),
                timeout=300,
            )

            parsed = json.loads(llm_result.text)
            repair_files = self._sanitize_raw_file_maps(
                parsed.get("files"),
                request.generation_root,
                warnings,
            )
            summary = self._clean_text(parsed.get("summary")) or "Development Agent 已根据验收失败日志生成 Node.js 修复文件提案。"
            model = self._usage_value(llm_result.usage, "model") or profile.model
            provider = self._usage_value(llm_result.usage, "provider") or profile.provider
            warnings.extend(self._string_list(parsed.get("warnings")))
            warnings.append(f"AI_REPAIR_PROVIDER={provider};MODEL={model}")

            return DevelopmentNodejsCodegenResponse(
                status="AI_REPAIR_GENERATED" if repair_files else "AI_REPAIR_EMPTY",
                generator_mode="LLM_ACCEPTANCE_REPAIR_WITH_GUARDRAILS",
                provider=provider,
                model=model,
                summary=summary,
                warnings=self._dedupe(warnings),
                files=repair_files,
                usage=llm_result.usage or {},
                raw={"schema": "development_nodejs_repair_output"},
            )
        except Exception as exc:
            return DevelopmentNodejsCodegenResponse(
                status="REPAIR_FALLBACK_EMPTY",
                generator_mode="REPAIR_LLM_UNAVAILABLE",
                provider=profile.provider,
                model=profile.model,
                summary="开发 Agent 修复模型调用不可用，未生成自动修复文件。",
                warnings=[
                    "DEVELOPMENT_LLM_REPAIR_UNAVAILABLE",
                    f"{type(exc).__name__}: {str(exc)[:300]}",
                ],
                files=[],
                usage={"provider": profile.provider, "model": profile.model, "success": False},
            )

    # A9.10.14.4.3.15.4.8.3_MAPPING_AWARE_REPAIR_LOOP: make repair prompts understand prototype contract -> real fullstack binding failures.
    def _mapping_aware_repair_focus(
        self,
        request: DevelopmentNodejsRepairRequest,
        current_files: list[DevelopmentCodeFilePayload],
    ) -> dict[str, Any]:
        failed_payload = {
            "acceptanceSummary": request.acceptance_summary,
            "failedItems": request.failed_items[:16],
            "logTail": request.log_tail[-120:],
        }
        encoded_failure = json.dumps(failed_payload, ensure_ascii=False).upper()
        tokens = [
            "PROTOTYPE_FULLSTACK_BINDING_CONTRACT",
            "FULLSTACKBINDING",
            "GENERATEDRESOURCES",
            "ACTIONBINDINGS",
            "DATABINDINGS",
            "RUNUIACTION",
            "FETCHUIROWS",
            "GENERATED-RESOURCE",
            "APISEGMENT",
            "ACTIONID",
            "FIELDKEY",
        ]
        is_binding_failure = any(token in encoded_failure for token in tokens)

        normalized_files: list[dict[str, Any]] = []
        paths = [self._normalize_path(getattr(file, "file_path", "")) for file in current_files]
        required_suffixes = [
            "/backend/src/modules/generated/generatedResources.js",
            "/frontend/src/modules/generated/generatedResources.js",
            "/frontend/src/modules/generated/pages",
            "/frontend/src/modules/generated/api",
            "/backend/src/modules/generated/generated-resource.routes.js",
            "/backend/src/modules/generated/generated-resource.service.js",
            "/backend/src/modules/generated/generated-resource.repository.js",
            "/backend/src/routes/index.js",
            "/backend/src/db/localStore.js",
            "/backend/src/db/seedData.js",
            "/tests/smoke.spec.js",
            "/database/schema.json",
            "/database/seed.json",
        ]
        missing_high_signal_context = [
            suffix for suffix in required_suffixes
            if not any(path.endswith(suffix) or suffix.strip("/") in path for path in paths)
        ]

        for file in current_files:
            path = self._normalize_path(getattr(file, "file_path", ""))
            lower = path.lower()
            content = getattr(file, "content", "") or ""
            if not any(
                token in lower
                for token in [
                    "generatedresources",
                    "generated-resource",
                    "modules/generated/pages",
                    "modules/generated/api",
                    "routes/index.js",
                    "localstore.js",
                    "seeddata.js",
                    "smoke.spec.js",
                    "schema.json",
                    "seed.json",
                ]
            ):
                continue
            normalized_files.append(
                {
                    "file_path": path,
                    "file_type": getattr(file, "file_type", ""),
                    "signals": self._binding_signals(content),
                    "contentPreview": content[:2800],
                }
            )

        return {
            "enabled": True,
            "isBindingFailure": is_binding_failure,
            "repairGoal": "把 PAGE_UI_CONTRACT / MODULE_PROTOTYPE_SNAPSHOT 中的 mock action、mock data、route 和 field 真实化为 Vue handler、frontend api、Express route、service/repository/localStore 和 smoke test。",
            "strictRules": [
                "不要重画页面；必须保持 UI_CONTRACT runtime / componentTree / 文案 / actionId / fieldKey / pageStates。",
                "优先修复 fullstackBinding、actionBindings、dataBindings、apiSegment 与代码实现之间的断链。",
                "SUBMIT_FORM/CREATE/UPDATE 必须进入 POST/PUT payload；QUERY_DATA 必须走 GET；DELETE_ROW 必须走 DELETE；NAVIGATE 必须走前端路由。",
                "tests/smoke.spec.js 必须遍历 generatedResources 并覆盖非导航 actionBindings。",
                "backend generated-resource routes/service/repository 必须读写 localStore，不允许把主链路降级成前端 mock。",
            ],
            "expectedFiles": required_suffixes,
            "missingHighSignalContext": missing_high_signal_context,
            "highSignalCurrentFiles": normalized_files[:18],
        }

    def _binding_signals(self, content: str) -> dict[str, bool]:
        text = str(content or "")
        compact = text.replace("_", "").replace("-", "").lower()
        return {
            "hasFullstackBinding": "fullstackbinding" in compact,
            "hasActionBindings": "actionbindings" in compact,
            "hasDataBindings": "databindings" in compact,
            "hasRunUiAction": "runuiaction" in compact,
            "hasFetchUiRows": "fetchuirows" in compact,
            "hasGeneratedResources": "generatedresources" in compact,
            "hasApiSegment": "apisegment" in compact,
            "hasLocalStore": "localstore" in compact or "readstore" in compact or "writestore" in compact,
            "hasCrudRoutes": all(token in text for token in ["router.get", "router.post", "router.delete"])
                or all(token in text for token in ["app.get", "app.post", "app.delete"]),
            "hasSmokeTraversal": "verifyresource" in compact or "verifyactionbindings" in compact,
        }

    def _repair_system_prompt(self) -> str:
        return """
你是 CodeMind Studio 的 Development Repair Agent，负责根据 Node.js 生成项目的 npm install、npm run build、npm test 和运行健康检查失败日志，生成最小、安全、可审查的修复文件提案。

必须遵守：
1. 只能修复 generated/nodejs/{projectCode}/app 这个项目内的文件，不能输出绝对路径或 ../。
2. 只返回需要修复的完整文件内容，不要返回 diff，也不要返回 Markdown。
3. 不要返回 codemind.generated.json、node_modules、dist、build、.git 或日志文件。
4. 优先修复第一处阻断错误：语法错误、导入路径错误、缺失脚本、Express 路由错误、Vue 编译错误、测试断言错误。
5. 保持项目技术栈：Vite/Vue 前端 + Express 后端 + 本地数据层。
6. 不要引入 native 编译依赖；除非 package.json 明确需要，优先通过代码修复解决。
7. 返回 JSON，结构必须符合 schema。
8. 当 failedItems 或 logTail 出现 PROTOTYPE_FULLSTACK_BINDING_CONTRACT、fullstackBinding、generatedResources、actionBindings、dataBindings、runUiAction 或 fetchUiRows 时，必须按 mappingAwareRepairFocus 修复“原型合同真实化绑定”。
9. 不要重画页面，不要把页面改成另一套相似 UI；必须保留 PAGE_UI_CONTRACT runtime、componentTree、文案、actionId、fieldKey、pageStates 和 navigationTargets。
10. 修复方向必须是把 mock 真实化：SUBMIT_FORM/CREATE/UPDATE -> Vue handler -> api client -> Express POST/PUT -> service/repository/localStore；QUERY_DATA -> GET；DELETE_ROW -> DELETE；NAVIGATE -> route。
11. 优先返回这些断链文件的完整内容：generatedResources.js、frontend generated pages/api、backend generated-resource routes/service/repository、routes/index.js、localStore/seedData、tests/smoke.spec.js。
12. smoke test 必须验证 generatedResources 中每个资源和关键非导航 actionBinding，而不是只测 /api/health 或单个固定接口。
""".strip()

    def _repair_user_prompt(
        self,
        request: DevelopmentNodejsRepairRequest,
        current_files: list[DevelopmentCodeFilePayload],
    ) -> str:
        payload = {
            "projectCode": request.project_code,
            "targetStack": request.target_stack,
            "generationRoot": request.generation_root,
            "node": request.node,
            "remarks": request.remarks,
            "handoffContext": self._compact_context(request.handoff_context),
            "acceptanceSummary": request.acceptance_summary,
            "failedItems": request.failed_items[:12],
            "logTail": request.log_tail[-240:],
            "currentFiles": [file.model_dump() for file in current_files],
            "mappingAwareRepairFocus": self._mapping_aware_repair_focus(request, current_files),
        }
        return (
            "请根据验收失败信息和当前代码，生成最小修复文件列表。"
            "只返回需要创建或更新的文件，文件内容必须是完整文件内容。\n\n"
            + json.dumps(payload, ensure_ascii=False)
        )

    def _system_prompt(self) -> str:
        return """
你是 CodeMind Studio 的 Development Codegen Agent，负责把语言无关的 DEV_HANDOFF_PACKAGE、隐藏原型上下文、API_CONTRACT、DATA_MODEL、TEST_SPEC 和已确认 UI_CONTRACT 转换为 Node.js 全栈项目中的代码贡献。

必须遵守：
1. 运行单位是一个完整项目 generated/nodejs/{projectCode}/app，不允许为单个节点生成独立项目。
2. 前端必须请求 Node.js 后端接口，不允许把业务主链路写成前端 mock。
3. 后端必须访问本地数据层 localStore/seedData；seed data 只用于首次运行和测试。
4. 只能返回 JSON，不能返回 Markdown 解释。
5. 每个文件必须在 generation_root 目录下，不能使用绝对路径、../、.git、node_modules、target、dist 或 build。
6. 不要返回 codemind.generated.json；它由 Java 侧根据最终文件安全生成。
7. 不要破坏已有 package.json 的 dev/build/test/acceptance 主脚本。
8. 优先改进确定性模板中的页面、API、service、repository、validation 和测试，使它们更贴近需求资产。
9. UI_CONTRACT 是用户在需求 Agent 中确认过的页面视觉蓝图；生成页面必须保持其 layout、componentTree、componentId、fieldKey、actionId、pageStates、navigationTargets、文案层级和交互流程。
10. Mock Preview 与 Development Agent 页面必须同源：需求阶段使用 UI_CONTRACT.mockData / mockScenarios，开发阶段只把 mock 数据替换为真实 API、真实 service、真实 validation 和真实状态管理，不得重新想象页面结构。
11. 不允许为了实现方便重排字段、删改按钮、改变主视觉结构或丢弃 pageStates；确需优化只能在不破坏 UI_CONTRACT 的前提下做样式细化。
12. 不能引入需要 native 编译的依赖；第一版以 Vite/Vue + Express + 纯 JS localStore 为主。
""".strip()

    def _user_prompt(
        self,
        request: DevelopmentNodejsCodegenRequest,
        files: list[DevelopmentCodeFilePayload],
    ) -> str:
        compact_files = [file.model_dump() for file in files]
        payload = {
            "projectCode": request.project_code,
            "targetStack": request.target_stack,
            "generationRoot": request.generation_root,
            "node": request.node,
            "remarks": request.remarks,
            "handoffContext": self._compact_context(request.handoff_context),
            "templateFiles": compact_files,
        }
        return (
            "请基于下面的开发交接上下文和确定性模板，返回一个完整的 Node.js 代码贡献文件列表。"
            "可以改进文件内容、补充必要文件，但必须保留主干结构与运行脚本。\n\n"
            + json.dumps(payload, ensure_ascii=False)
        )

    def _output_schema(self) -> dict[str, Any]:
        return {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "summary": {"type": "string"},
                "warnings": {"type": "array", "items": {"type": "string"}},
                "files": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "additionalProperties": False,
                        "properties": {
                            "file_path": {"type": "string"},
                            "file_type": {"type": "string"},
                            "action": {"type": "string"},
                            "source": {"type": "string"},
                            "description": {"type": "string"},
                            "language": {"type": "string"},
                            "content": {"type": "string"},
                            "priority": {"type": "integer"},
                        },
                        "required": [
                            "file_path",
                            "file_type",
                            "action",
                            "source",
                            "description",
                            "language",
                            "content",
                            "priority",
                        ],
                    },
                },
            },
            "required": ["summary", "warnings", "files"],
        }

    def _compact_context(self, value: dict[str, Any]) -> dict[str, Any]:
        try:
            encoded = json.dumps(value or {}, ensure_ascii=False)
            if len(encoded) <= 80_000:
                return value or {}
            return {"truncated": True, "text": encoded[:80_000]}
        except Exception:
            return {"unserializable": True, "text": str(value)[:20_000]}

    def _sanitize_raw_file_maps(
        self,
        values: Any,
        generation_root: str,
        warnings: list[str],
    ) -> list[DevelopmentCodeFilePayload]:
        if not isinstance(values, list):
            warnings.append("AI_CODEGEN_FILES_NOT_LIST")
            return []

        files: list[DevelopmentCodeFilePayload] = []
        for item in values:
            if not isinstance(item, dict):
                warnings.append("AI_CODEGEN_SKIPPED_NON_OBJECT_FILE")
                continue
            try:
                files.append(DevelopmentCodeFilePayload(**item))
            except Exception as exc:
                warnings.append(f"AI_CODEGEN_SKIPPED_INVALID_FILE:{type(exc).__name__}")

        return self._sanitize_files(files, generation_root, warnings)

    def _sanitize_files(
        self,
        files: list[DevelopmentCodeFilePayload],
        generation_root: str,
        warnings: list[str],
    ) -> list[DevelopmentCodeFilePayload]:
        root = self._normalize_path(generation_root)
        result: list[DevelopmentCodeFilePayload] = []
        total_chars = 0
        seen: set[str] = set()

        for file in files:
            if len(result) >= _MAX_FILES:
                warnings.append("AI_CODEGEN_FILE_LIMIT_REACHED")
                break

            path = self._normalize_path(file.file_path)
            if not self._is_safe_generated_file(path, root):
                warnings.append(f"AI_CODEGEN_SKIPPED_UNSAFE_PATH:{file.file_path}")
                continue
            if path.endswith("/codemind.generated.json"):
                warnings.append("AI_CODEGEN_SKIPPED_MANIFEST_FILE")
                continue
            if path in seen:
                warnings.append(f"AI_CODEGEN_SKIPPED_DUPLICATE_FILE:{path}")
                continue

            content = file.content or ""
            if len(content) > _MAX_FILE_CHARS:
                warnings.append(f"AI_CODEGEN_TRUNCATED_LARGE_FILE:{path}")
                content = content[:_MAX_FILE_CHARS]
            total_chars += len(content)
            if total_chars > _MAX_TOTAL_CHARS:
                warnings.append("AI_CODEGEN_TOTAL_SIZE_LIMIT_REACHED")
                break

            seen.add(path)
            result.append(
                DevelopmentCodeFilePayload(
                    file_path=path,
                    file_type=self._safe_choice(file.file_type, _ALLOWED_FILE_TYPES, "UNKNOWN"),
                    action=self._safe_choice(file.action, _ALLOWED_ACTIONS, "CREATE_OR_UPDATE"),
                    source=self._clean_text(file.source) or "DEV_HANDOFF_PACKAGE",
                    description=self._clean_text(file.description),
                    language=self._clean_text(file.language) or "text",
                    content=content,
                    priority=int(file.priority or 100),
                )
            )

        return sorted(result, key=lambda item: (item.priority, item.file_path))

    def _merge_with_template(
        self,
        *,
        template_files: list[DevelopmentCodeFilePayload],
        llm_files: list[DevelopmentCodeFilePayload],
        warnings: list[str],
    ) -> list[DevelopmentCodeFilePayload]:
        by_path: dict[str, DevelopmentCodeFilePayload] = {file.file_path: file for file in template_files}
        ai_by_path = {file.file_path: file for file in llm_files}

        for path, ai_file in ai_by_path.items():
            if path in by_path:
                template = by_path[path]
                by_path[path] = DevelopmentCodeFilePayload(
                    file_path=template.file_path,
                    file_type=ai_file.file_type or template.file_type,
                    action=template.action if template.action == "CREATE_OR_KEEP" else (ai_file.action or template.action),
                    source=ai_file.source or template.source,
                    description=ai_file.description or template.description,
                    language=ai_file.language or template.language,
                    content=ai_file.content,
                    priority=template.priority,
                )
            else:
                by_path[path] = ai_file

        missing = [file.file_path for file in template_files if file.file_path not in ai_by_path]
        if missing:
            warnings.append(f"AI_CODEGEN_TEMPLATE_FILES_PRESERVED={len(missing)}")

        return sorted(by_path.values(), key=lambda item: (item.priority, item.file_path))

    def _is_safe_generated_file(self, file_path: str, root: str) -> bool:
        if not file_path or ".." in file_path.split("/"):
            return False
        lower = file_path.lower()
        blocked = ["/.git/", "/node_modules/", "/target/", "/dist/", "/build/", "/.venv/", "/venv/"]
        if any(item in f"/{lower}/" for item in blocked):
            return False
        if ":" in file_path or file_path.startswith("/") or file_path.startswith("~"):
            return False
        return file_path == root or file_path.startswith(root.rstrip("/") + "/")

    def _normalize_path(self, value: str | None) -> str:
        return str(value or "").replace("\\", "/").strip().strip("/")

    def _safe_choice(self, value: str | None, allowed: set[str], fallback: str) -> str:
        normalized = str(value or "").strip().upper()
        return normalized if normalized in allowed else fallback

    def _clean_text(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).replace("\r", " ").strip()

    def _string_list(self, value: Any) -> list[str]:
        if not isinstance(value, list):
            return []
        return [str(item).strip() for item in value if str(item).strip()]

    def _dedupe(self, values: list[str]) -> list[str]:
        result: list[str] = []
        seen: set[str] = set()
        for value in values:
            cleaned = str(value or "").strip()
            if cleaned and cleaned not in seen:
                seen.add(cleaned)
                result.append(cleaned)
        return result

    def _usage_value(self, usage: dict[str, Any], key: str) -> str | None:
        if key in usage:
            return str(usage.get(key))
        runtime = usage.get("runtime") if isinstance(usage, dict) else None
        if isinstance(runtime, dict) and key in runtime:
            return str(runtime.get(key))
        profile = usage.get("model_profile") if isinstance(usage, dict) else None
        if isinstance(profile, dict) and key in profile:
            return str(profile.get(key))
        return None