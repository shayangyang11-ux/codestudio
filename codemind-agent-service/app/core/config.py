from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "codemind-agent-service"
    environment: Literal["local", "dev", "test", "prod"] = "local"

    llm_provider: Literal["mock", "openai", "openai_compatible", "deepseek"] = "mock"
    llm_model: str = "mock-requirement-agent"
    llm_api_key: str = "mock"
    llm_base_url: str = "https://api.deepseek.com"

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