from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any, Literal

from app.core.config import get_settings

AgentScope = Literal["requirement", "development", "style", "legacy"]
LlmTask = Literal["default", "codegen", "fast", "diagnosis"]


@dataclass(frozen=True)
class LlmModelProfile:
    agent_scope: str
    llm_task: str
    provider: str
    model: str
    api_key: str
    base_url: str
    protocol: str
    json_mode: bool
    free_quota_stop_enabled: bool
    source: str

    @property
    def api_key_configured(self) -> bool:
        return bool(self.api_key and self.api_key.strip() and self.api_key.strip() != "mock")

    def public_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload.pop("api_key", None)
        payload["apiKeyConfigured"] = self.api_key_configured
        payload["baseUrl"] = self.base_url
        payload["agentScope"] = self.agent_scope
        payload["llmTask"] = self.llm_task
        payload["freeQuotaStopEnabled"] = self.free_quota_stop_enabled
        return payload


class LlmModelRouter:
    """
    Agent-level model router.

    Requirement Agent keeps the stable existing model profile, while Development
    Agent can use a separate Qwen model pool for code generation, lightweight
    fixes, and diagnostics. The router only resolves configuration; actual model
    calls still go through LlmService + LlmRuntimeManager.
    """

    SUPPORTED_PROVIDERS = {"mock", "openai", "openai_compatible", "deepseek", "qwen"}

    def __init__(self) -> None:
        self.settings = get_settings()

    def resolve(
        self,
        *,
        agent_scope: str | None = None,
        llm_task: str | None = None,
    ) -> LlmModelProfile:
        scope = self._normalize_scope(agent_scope)
        task = self._normalize_task(llm_task)

        if scope == "development":
            return self._development_profile(task)

        if scope == "style":
            return self._legacy_profile(scope="style", task=task, source="legacy-style")

        if scope == "legacy":
            return self._legacy_profile(scope="legacy", task=task, source="legacy")

        return self._requirement_profile(task)

    def status(self) -> dict[str, Any]:
        requirement = self._requirement_profile("default")
        development_codegen = self._development_profile("codegen")
        development_fast = self._development_profile("fast")
        development_diagnosis = self._development_profile("diagnosis")
        legacy = self._legacy_profile(scope="legacy", task="default", source="legacy")

        return {
            "schemaVersion": "1.0",
            "routingMode": "AGENT_SCOPE_AND_TASK",
            "legacy": legacy.public_dict(),
            "requirement": {
                "default": requirement.public_dict(),
            },
            "development": {
                "codegen": development_codegen.public_dict(),
                "fast": development_fast.public_dict(),
                "diagnosis": development_diagnosis.public_dict(),
            },
            "policy": {
                "requirementAgentPurpose": "需求理解、模块树、需求资产、DEV_HANDOFF_PACKAGE。",
                "developmentAgentPurpose": "代码生成、代码修复、日志诊断、部署配置生成。",
                "developmentFreeQuotaStopEnabled": self.settings.development_free_quota_stop_enabled,
            },
        }

    def _requirement_profile(self, task: str) -> LlmModelProfile:
        provider = self.settings.requirement_llm_provider or self.settings.llm_provider
        model = self.settings.requirement_llm_model or self.settings.llm_model
        api_key = self.settings.requirement_llm_api_key or self.settings.llm_api_key
        base_url = self.settings.requirement_llm_base_url or self.settings.llm_base_url

        return self._build_profile(
            scope="requirement",
            task=task,
            provider=provider,
            model=model,
            api_key=api_key,
            base_url=base_url,
            source="requirement-profile" if self.settings.requirement_llm_provider else "legacy-fallback",
            free_quota_stop_enabled=False,
        )

    def _development_profile(self, task: str) -> LlmModelProfile:
        provider = self.settings.development_llm_provider or "qwen"
        model = self._development_model(task)
        if provider == "qwen":
            api_key = self.settings.development_llm_api_key or self.settings.qwen_llm_api_key or ""
            base_url = self.settings.development_llm_base_url or self.settings.qwen_llm_base_url
        else:
            api_key = self.settings.development_llm_api_key or self.settings.llm_api_key
            base_url = self.settings.development_llm_base_url or self.settings.llm_base_url

        return self._build_profile(
            scope="development",
            task=task,
            provider=provider,
            model=model,
            api_key=api_key or "",
            base_url=base_url,
            source="development-profile",
            free_quota_stop_enabled=self.settings.development_free_quota_stop_enabled,
        )

    def _legacy_profile(
        self,
        *,
        scope: str,
        task: str,
        source: str,
    ) -> LlmModelProfile:
        return self._build_profile(
            scope=scope,
            task=task,
            provider=self.settings.llm_provider,
            model=self.settings.llm_model,
            api_key=self.settings.llm_api_key,
            base_url=self.settings.llm_base_url,
            source=source,
            free_quota_stop_enabled=False,
        )

    def _build_profile(
        self,
        *,
        scope: str,
        task: str,
        provider: str,
        model: str,
        api_key: str,
        base_url: str,
        source: str,
        free_quota_stop_enabled: bool,
    ) -> LlmModelProfile:
        normalized_provider = (provider or "mock").strip().lower()
        if normalized_provider not in self.SUPPORTED_PROVIDERS:
            raise ValueError(f"Unsupported LLM provider: {provider}")

        protocol = "responses"
        json_mode = True

        if normalized_provider in {"deepseek", "qwen"}:
            protocol = "chat_completions"

        if normalized_provider == "mock":
            protocol = "mock"

        return LlmModelProfile(
            agent_scope=scope,
            llm_task=task,
            provider=normalized_provider,
            model=model or "mock-requirement-agent",
            api_key=api_key or "",
            base_url=(base_url or "").rstrip("/"),
            protocol=protocol,
            json_mode=json_mode,
            free_quota_stop_enabled=free_quota_stop_enabled,
            source=source,
        )

    def _development_model(self, task: str) -> str:
        if task == "fast":
            return self.settings.development_fast_model
        if task == "diagnosis":
            return self.settings.development_diagnosis_model
        return self.settings.development_codegen_model

    def _normalize_scope(self, value: str | None) -> str:
        normalized = (value or "requirement").strip().lower()
        if normalized in {"dev", "development_agent", "development-agent"}:
            return "development"
        if normalized in {"requirement_agent", "requirement-agent", "req"}:
            return "requirement"
        if normalized in {"style_agent", "style-agent"}:
            return "style"
        if normalized not in {"requirement", "development", "style", "legacy"}:
            return "requirement"
        return normalized

    def _normalize_task(self, value: str | None) -> str:
        normalized = (value or "default").strip().lower()
        if normalized in {"code", "codegen", "code_generation", "generate_code"}:
            return "codegen"
        if normalized in {"fix", "small_fix", "quick", "fast"}:
            return "fast"
        if normalized in {"diagnose", "diagnosis", "logs", "runtime"}:
            return "diagnosis"
        return "default"
