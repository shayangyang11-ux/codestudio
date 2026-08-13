from __future__ import annotations

import asyncio
import random
import time
from dataclasses import asdict
from functools import lru_cache
from typing import Any, Awaitable, Callable, TypeVar

import httpx

from app.core.config import get_settings
from app.runtime.llm_runtime_exceptions import (
    LlmProviderCircuitOpenError,
    LlmProviderHttpError,
    LlmRuntimeBusyError,
)
from app.runtime.llm_runtime_metrics import LlmRuntimeMetrics
from app.runtime.provider_circuit_breaker import ProviderCircuitBreaker
from app.services.llm_model_router import LlmModelRouter

T = TypeVar("T")


class LlmRuntimeManager:
    def __init__(self) -> None:
        self.settings = get_settings()

        self._semaphore = asyncio.Semaphore(
            max(1, self.settings.llm_runtime_max_concurrency)
        )

        self.metrics = LlmRuntimeMetrics()

        self._circuit_breakers: dict[str, ProviderCircuitBreaker] = {}

    async def execute(
        self,
        *,
        provider: str,
        model: str,
        operation_name: str,
        operation: Callable[[], Awaitable[T]],
    ) -> tuple[T, dict[str, Any]]:
        circuit = self._get_circuit_breaker(provider)

        if not circuit.allow_request():
            self.metrics.record_circuit_rejected()
            snapshot = circuit.snapshot()

            raise LlmProviderCircuitOpenError(
                f"LLM provider circuit is open. provider={provider}, "
                f"remaining_open_seconds={snapshot.remaining_open_seconds:.1f}"
            )

        runtime_started = time.monotonic()
        acquire_started = time.monotonic()

        try:
            await asyncio.wait_for(
                self._semaphore.acquire(),
                timeout=max(1, self.settings.llm_runtime_acquire_timeout_seconds),
            )
        except asyncio.TimeoutError as exc:
            self.metrics.record_busy_rejected()
            raise LlmRuntimeBusyError(
                "LLM runtime is busy. Failed to acquire concurrency capacity "
                f"within {self.settings.llm_runtime_acquire_timeout_seconds} seconds."
            ) from exc

        acquire_wait_ms = int((time.monotonic() - acquire_started) * 1000)
        attempts_used = 0
        last_error: BaseException | None = None

        self.metrics.record_request_started()

        try:
            max_attempts = max(1, self.settings.llm_runtime_max_attempts)

            for attempt in range(1, max_attempts + 1):
                attempts_used = attempt
                attempt_started = time.monotonic()

                try:
                    result = await operation()

                    circuit.record_success()

                    latency_ms = int((time.monotonic() - runtime_started) * 1000)
                    self.metrics.record_request_finished(
                        success=True,
                        latency_ms=latency_ms,
                    )

                    runtime_metadata = self._build_runtime_metadata(
                        provider=provider,
                        model=model,
                        operation_name=operation_name,
                        success=True,
                        attempts_used=attempts_used,
                        acquire_wait_ms=acquire_wait_ms,
                        total_latency_ms=latency_ms,
                        last_error=None,
                        circuit=circuit,
                    )

                    return result, runtime_metadata

                except Exception as exc:
                    last_error = exc
                    circuit.record_failure()

                    if attempt >= max_attempts or not self._is_retryable(exc):
                        latency_ms = int((time.monotonic() - runtime_started) * 1000)
                        self.metrics.record_request_finished(
                            success=False,
                            latency_ms=latency_ms,
                        )

                        raise

                    self.metrics.record_retry()

                    await asyncio.sleep(
                        self._retry_delay_seconds(
                            attempt=attempt,
                            attempt_latency_seconds=time.monotonic() - attempt_started,
                        )
                    )

            latency_ms = int((time.monotonic() - runtime_started) * 1000)
            self.metrics.record_request_finished(
                success=False,
                latency_ms=latency_ms,
            )

            if last_error is not None:
                raise last_error

            raise RuntimeError("LLM runtime failed without a captured exception.")

        except Exception:
            raise

        finally:
            self._semaphore.release()

    def build_failure_metadata(
        self,
        *,
        provider: str,
        model: str,
        operation_name: str,
        error: BaseException,
    ) -> dict[str, Any]:
        circuit = self._get_circuit_breaker(provider)

        return {
            "runtime": {
                "provider": provider,
                "model": model,
                "operation_name": operation_name,
                "success": False,
                "error_type": type(error).__name__,
                "error_message": str(error),
                "metrics": self.metrics.snapshot().to_dict(),
                "circuit": asdict(circuit.snapshot()),
            }
        }

    def status(self) -> dict[str, Any]:
        return {
            "provider": self.settings.llm_provider,
            "model": self.settings.llm_model,
            "runtime_policy": {
                "max_concurrency": max(1, self.settings.llm_runtime_max_concurrency),
                "acquire_timeout_seconds": max(1, self.settings.llm_runtime_acquire_timeout_seconds),
                "max_attempts": max(1, self.settings.llm_runtime_max_attempts),
                "retry_base_seconds": self.settings.llm_runtime_retry_base_seconds,
                "retry_max_seconds": self.settings.llm_runtime_retry_max_seconds,
                "circuit_failure_threshold": self.settings.llm_runtime_circuit_failure_threshold,
                "circuit_reset_seconds": self.settings.llm_runtime_circuit_reset_seconds,
                "request_timeout_seconds": self.settings.request_timeout_seconds,
                "max_output_tokens": self.settings.max_output_tokens,
            },
            "model_routing": LlmModelRouter().status(),
            "metrics": self.metrics.snapshot().to_dict(),
            "circuits": {
                provider: asdict(circuit.snapshot())
                for provider, circuit in self._circuit_breakers.items()
            },
        }

    def _build_runtime_metadata(
        self,
        *,
        provider: str,
        model: str,
        operation_name: str,
        success: bool,
        attempts_used: int,
        acquire_wait_ms: int,
        total_latency_ms: int,
        last_error: BaseException | None,
        circuit: ProviderCircuitBreaker,
    ) -> dict[str, Any]:
        return {
            "runtime": {
                "provider": provider,
                "model": model,
                "operation_name": operation_name,
                "success": success,
                "attempts_used": attempts_used,
                "max_attempts": max(1, self.settings.llm_runtime_max_attempts),
                "max_concurrency": max(1, self.settings.llm_runtime_max_concurrency),
                "acquire_wait_ms": acquire_wait_ms,
                "total_latency_ms": total_latency_ms,
                "last_error_type": None if last_error is None else type(last_error).__name__,
                "last_error_message": None if last_error is None else str(last_error),
                "metrics": self.metrics.snapshot().to_dict(),
                "circuit": asdict(circuit.snapshot()),
            }
        }

    def _get_circuit_breaker(
        self,
        provider: str,
    ) -> ProviderCircuitBreaker:
        if provider not in self._circuit_breakers:
            self._circuit_breakers[provider] = ProviderCircuitBreaker(
                provider=provider,
                failure_threshold=self.settings.llm_runtime_circuit_failure_threshold,
                reset_seconds=self.settings.llm_runtime_circuit_reset_seconds,
            )

        return self._circuit_breakers[provider]

    # A9.10.14.4.3.12.1_TOKEN_GUARD_AND_PRD_UI_SYNC_PATCH_LLM_RUNTIME
    def _is_retryable(
        self,
        exc: BaseException,
    ) -> bool:
        """Only retry provider/transport transient failures.

        Never retry Python post-processing, JSON parsing, validation, schema,
        or Agent code errors. Those failures often happen after a paid model
        response has already returned; retrying them simply burns more tokens.
        """
        if isinstance(exc, LlmProviderCircuitOpenError):
            return False

        if isinstance(exc, LlmRuntimeBusyError):
            return False

        if isinstance(exc, LlmProviderHttpError):
            return exc.status_code in {408, 409, 425, 429} or exc.status_code >= 500

        if isinstance(exc, (httpx.TimeoutException, httpx.NetworkError, httpx.TransportError)):
            return True

        return False

    def _retry_delay_seconds(
        self,
        *,
        attempt: int,
        attempt_latency_seconds: float,
    ) -> float:
        base = max(0.1, self.settings.llm_runtime_retry_base_seconds)
        max_delay = max(base, self.settings.llm_runtime_retry_max_seconds)

        exponential = base * (2 ** max(0, attempt - 1))
        jitter = random.uniform(0.0, min(1.0, base))

        return min(max_delay, exponential + jitter)


@lru_cache
def get_llm_runtime_manager() -> LlmRuntimeManager:
    return LlmRuntimeManager()
