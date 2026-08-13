from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

import httpx

from app.core.config import get_settings
from app.runtime.llm_runtime_exceptions import (
    LlmApiKeyConfigurationError,
    LlmProviderHttpError,
)
from app.runtime.llm_runtime_manager import get_llm_runtime_manager
from app.services.llm_model_router import LlmModelProfile, LlmModelRouter


@dataclass
class LlmResult:
    text: str
    raw: dict[str, Any]
    usage: dict[str, Any]


class LlmService:
    """
    CodeMind Studio unified LLM gateway.

    All model calls must go through LlmRuntimeManager so that concurrency,
    retry, timeout, circuit breaker and runtime metrics are applied globally.
    """

    def __init__(
        self,
        *,
        agent_scope: str = "requirement",
        llm_task: str = "default",
    ) -> None:
        self.settings = get_settings()
        self.runtime_manager = get_llm_runtime_manager()
        self.model_router = LlmModelRouter()
        self.agent_scope = agent_scope
        self.llm_task = llm_task

    async def generate_structured_json(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
        output_schema_name: str,
        output_json_schema: dict[str, Any],
        validate_json: bool = True,
        agent_scope: str | None = None,
        llm_task: str | None = None,
    ) -> LlmResult:
        profile = self.model_router.resolve(
            agent_scope=agent_scope or self.agent_scope,
            llm_task=llm_task or self.llm_task,
        )

        # A9.10.14.1.1_FORCE_UI_CONTRACT_REGENERATE
        print(
            f"[A9.10.14.1.1] LLM profile provider={profile.provider} model={profile.model} scope={profile.agent_scope} task={profile.llm_task}",
            flush=True,
        )

        if profile.provider == "mock":
            return self._mock_generate_json(
                output_schema_name=output_schema_name,
                profile=profile,
            )

        self._validate_api_key(profile)

        async def operation() -> LlmResult:
            if profile.provider in {"deepseek", "qwen"}:
                return await self._call_chat_completions_api(
                    profile=profile,
                    system_prompt=system_prompt,
                    user_prompt=user_prompt,
                    output_schema_name=output_schema_name,
                    output_json_schema=output_json_schema,
                    validate_json=validate_json,
                )

            if profile.provider in {"openai", "openai_compatible"}:
                return await self._call_openai_responses_api(
                    profile=profile,
                    system_prompt=system_prompt,
                    user_prompt=user_prompt,
                    output_schema_name=output_schema_name,
                    output_json_schema=output_json_schema,
                    validate_json=validate_json,
                )

            raise ValueError(f"Unsupported LLM provider: {profile.provider}")

        result, runtime_metadata = await self.runtime_manager.execute(
            provider=profile.provider,
            model=profile.model,
            operation_name=f"{profile.agent_scope}.{profile.llm_task}.generate_structured_json",
            operation=operation,
        )

        result.usage = self._merge_usage_metadata(
            result.usage,
            runtime_metadata,
            profile,
        )

        return result

    def _mock_generate_json(
        self,
        *,
        output_schema_name: str,
        profile: LlmModelProfile,
    ) -> LlmResult:
        mock_output = {
            "modules": [
                {
                    "key": "user_center",
                    "parent_key": "",
                    "name": "用户中心",
                    "type": "DOMAIN_MODULE",
                    "description": "负责用户登录注册、用户资料管理和用户权限相关能力。",
                    "sort_order": 1,
                }
            ],
            "markdown": "# 项目模块树\n\n这是 mock 模式下的模块树输出。",
            "analysis_summary": "mock 模式用于本地连通性验证，不消耗真实 token。",
        }

        usage = {
            "input_tokens": 0,
            "output_tokens": 0,
            "total_tokens": 0,
            "runtime": {
                "provider": "mock",
                "model": profile.model,
                "success": True,
                "attempts_used": 0,
            },
        }

        usage.update(self._model_profile_usage(profile))

        return LlmResult(
            text=json.dumps(mock_output, ensure_ascii=False),
            raw={
                "provider": "mock",
                "model": profile.model,
                "output_schema_name": output_schema_name,
            },
            usage=usage,
        )

    async def _call_chat_completions_api(
        self,
        *,
        profile: LlmModelProfile,
        system_prompt: str,
        user_prompt: str,
        output_schema_name: str,
        output_json_schema: dict[str, Any],
        validate_json: bool = True,
    ) -> LlmResult:
        url = profile.base_url.rstrip("/") + "/chat/completions"

        schema_instruction = self._build_schema_instruction(
            output_schema_name=output_schema_name,
            output_json_schema=output_json_schema,
        )

        messages = [
            {
                "role": "system",
                "content": system_prompt + "\n\n" + schema_instruction,
            },
            {
                "role": "user",
                "content": user_prompt,
            },
        ]

        payload: dict[str, Any] = {
            "model": profile.model,
            "messages": messages,
            "response_format": {
                "type": "json_object",
            },
            "temperature": 0.2,
            "max_tokens": self.settings.max_output_tokens,
        }

        headers = {
            "Authorization": f"Bearer {profile.api_key}",
            "Content-Type": "application/json",
        }

        timeout = httpx.Timeout(self.settings.request_timeout_seconds)

        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                url,
                headers=headers,
                json=payload,
            )

        if response.status_code >= 400:
            raise LlmProviderHttpError(
                provider=profile.provider,
                status_code=response.status_code,
                response_text=response.text,
            )

        raw = response.json()
        text = self._extract_chat_completion_text(raw)

        if not text or not text.strip():
            raise RuntimeError(
                f"{profile.provider} Chat Completions API returned empty text. Raw response: {raw}"
            )

        normalized_text = self._normalize_json_text(text)
        if validate_json:
            self._assert_valid_json(normalized_text)

        usage = self._extract_usage(raw)
        usage.update(self._model_profile_usage(profile))

        return LlmResult(
            text=normalized_text,
            raw=raw,
            usage=usage,
        )

    async def _call_openai_responses_api(
        self,
        *,
        profile: LlmModelProfile,
        system_prompt: str,
        user_prompt: str,
        output_schema_name: str,
        output_json_schema: dict[str, Any],
        validate_json: bool = True,
    ) -> LlmResult:
        url = profile.base_url.rstrip("/") + "/responses"

        payload: dict[str, Any] = {
            "model": profile.model,
            "input": [
                {
                    "role": "system",
                    "content": system_prompt,
                },
                {
                    "role": "user",
                    "content": user_prompt,
                },
            ],
            "max_output_tokens": self.settings.max_output_tokens,
            "text": {
                "format": {
                    "type": "json_schema",
                    "name": output_schema_name,
                    "strict": True,
                    "schema": output_json_schema,
                }
            },
        }

        headers = {
            "Authorization": f"Bearer {profile.api_key}",
            "Content-Type": "application/json",
        }

        timeout = httpx.Timeout(self.settings.request_timeout_seconds)

        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(
                url,
                headers=headers,
                json=payload,
            )

        if response.status_code >= 400:
            raise LlmProviderHttpError(
                provider=profile.provider,
                status_code=response.status_code,
                response_text=response.text,
            )

        raw = response.json()
        text = self._extract_response_text(raw)

        if not text or not text.strip():
            raise RuntimeError(f"OpenAI Responses API returned empty text. Raw response: {raw}")

        normalized_text = self._normalize_json_text(text)
        if validate_json:
            self._assert_valid_json(normalized_text)

        usage = self._extract_usage(raw)
        usage.update(self._model_profile_usage(profile))

        return LlmResult(
            text=normalized_text,
            raw=raw,
            usage=usage,
        )

    def _build_schema_instruction(
        self,
        *,
        output_schema_name: str,
        output_json_schema: dict[str, Any],
    ) -> str:
        return "\n".join(
            [
                "你必须只输出一个合法 JSON 对象。",
                "不要输出 Markdown。",
                "不要输出 ```json 代码块。",
                "不要输出 JSON 之外的解释文字。",
                f"输出结构名称：{output_schema_name}",
                "输出 JSON 必须满足以下 JSON Schema 语义约束：",
                json.dumps(output_json_schema, ensure_ascii=False),
            ]
        )

    def _extract_chat_completion_text(self, raw: dict[str, Any]) -> str:
        choices = raw.get("choices", [])
        if not isinstance(choices, list) or not choices:
            return ""

        first_choice = choices[0]
        if not isinstance(first_choice, dict):
            return ""

        message = first_choice.get("message", {})
        if not isinstance(message, dict):
            return ""

        content = message.get("content")
        if isinstance(content, str):
            return content.strip()

        return ""

    def _extract_response_text(self, raw: dict[str, Any]) -> str:
        direct_output_text = raw.get("output_text")
        if isinstance(direct_output_text, str) and direct_output_text.strip():
            return direct_output_text

        output_items = raw.get("output", [])
        if not isinstance(output_items, list):
            return ""

        texts: list[str] = []

        for output_item in output_items:
            if not isinstance(output_item, dict):
                continue

            content_items = output_item.get("content", [])
            if not isinstance(content_items, list):
                continue

            for content_item in content_items:
                if not isinstance(content_item, dict):
                    continue

                if content_item.get("type") == "output_text":
                    text = content_item.get("text")
                    if isinstance(text, str):
                        texts.append(text)

        return "\n".join(texts).strip()

    def _normalize_json_text(self, text: str) -> str:
        cleaned = text.strip()

        if cleaned.startswith("```json"):
            cleaned = cleaned.removeprefix("```json").strip()

        if cleaned.startswith("```"):
            cleaned = cleaned.removeprefix("```").strip()

        if cleaned.endswith("```"):
            cleaned = cleaned.removesuffix("```").strip()

        return cleaned

    def _extract_usage(self, raw: dict[str, Any]) -> dict[str, Any]:
        usage = raw.get("usage")
        if isinstance(usage, dict):
            return usage

        return {
            "input_tokens": None,
            "output_tokens": None,
            "total_tokens": None,
        }

    def _assert_valid_json(self, text: str) -> None:
        try:
            json.loads(text)
        except Exception as exc:
            raise RuntimeError(f"LLM output is not valid JSON: {text}") from exc

    def _validate_api_key(self, profile: LlmModelProfile) -> None:
        api_key = profile.api_key or ""

        if not api_key or api_key.strip() in {"", "mock"}:
            raise LlmApiKeyConfigurationError(
                f"LLM API key is empty for provider={profile.provider}, "
                f"agent_scope={profile.agent_scope}, llm_task={profile.llm_task}. "
                "Configure the matching CODEMIND_* API key or use CODEMIND_LLM_PROVIDER=mock."
            )

        try:
            api_key.encode("ascii")
        except UnicodeEncodeError as exc:
            raise LlmApiKeyConfigurationError(
                f"LLM API key contains non-ASCII characters for provider={profile.provider}. "
                "Please replace placeholder text with a real API key."
            ) from exc

    def _model_profile_usage(self, profile: LlmModelProfile) -> dict[str, Any]:
        return {
            "model_profile": profile.public_dict(),
            "model_provider": profile.provider,
            "model_name": profile.model,
            "agent_scope": profile.agent_scope,
            "llm_task": profile.llm_task,
        }

    def _merge_usage_metadata(
        self,
        usage: dict[str, Any],
        runtime_metadata: dict[str, Any],
        profile: LlmModelProfile,
    ) -> dict[str, Any]:
        merged = dict(usage or {})
        merged.update(runtime_metadata)
        merged.update(self._model_profile_usage(profile))
        return merged
