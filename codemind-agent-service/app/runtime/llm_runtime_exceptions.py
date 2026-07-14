from __future__ import annotations


class LlmRuntimeError(RuntimeError):
    """Base exception for LLM runtime failures."""


class LlmRuntimeBusyError(LlmRuntimeError):
    """Raised when runtime cannot acquire concurrency capacity in time."""


class LlmProviderCircuitOpenError(LlmRuntimeError):
    """Raised when provider circuit breaker is open."""


class LlmProviderHttpError(LlmRuntimeError):
    def __init__(
        self,
        *,
        provider: str,
        status_code: int,
        response_text: str,
    ) -> None:
        self.provider = provider
        self.status_code = status_code
        self.response_text = response_text
        super().__init__(
            f"{provider} API call failed: HTTP {status_code}, {response_text}"
        )


class LlmApiKeyConfigurationError(LlmRuntimeError):
    """Raised when API key configuration is invalid."""