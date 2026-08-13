from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


LlmProvider = Literal["mock", "openai", "openai_compatible", "deepseek", "qwen"]


class Settings(BaseSettings):
    app_name: str = "codemind-agent-service"
    environment: Literal["local", "dev", "test", "prod"] = "local"

    # Legacy default profile. Existing Requirement Agent calls still work with these.
    llm_provider: LlmProvider = "mock"
    llm_model: str = "mock-requirement-agent"
    llm_api_key: str = ""
    llm_base_url: str = "https://api.deepseek.com"

    # Requirement Agent model profile. Empty values fall back to the legacy profile above.
    requirement_llm_provider: LlmProvider | None = None
    requirement_llm_model: str | None = None
    requirement_llm_api_key: str | None = None
    requirement_llm_base_url: str | None = None

    # Development Agent model profile. This is used by future codegen/fix/diagnosis LLM calls
    # and displayed in the Dev Agent workspace before the LLM codegen endpoint is enabled.
    development_llm_provider: LlmProvider = "qwen"
    development_codegen_model: str = "qwen3.7-plus"
    development_fast_model: str = "qwen3.7-flash"
    development_diagnosis_model: str = "qwen3.7-flash"
    development_llm_api_key: str | None = None
    development_llm_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    development_free_quota_stop_enabled: bool = True

    # Optional provider-specific Qwen defaults. These make it easy to share one Qwen key
    # across Development Agent model tasks without duplicating values.
    qwen_llm_api_key: str | None = None
    qwen_llm_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    request_timeout_seconds: int = 120
    max_input_tokens: int = 12000
    max_output_tokens: int = 6000

    # LLM Runtime: concurrency, retry, timeout, circuit breaker
    llm_runtime_max_concurrency: int = 4
    llm_runtime_acquire_timeout_seconds: int = 30
    llm_runtime_max_attempts: int = 3
    llm_runtime_retry_base_seconds: float = 1.5
    llm_runtime_retry_max_seconds: float = 12.0
    llm_runtime_circuit_failure_threshold: int = 5
    llm_runtime_circuit_reset_seconds: int = 60

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="CODEMIND_",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
