from __future__ import annotations

import time
from dataclasses import dataclass


@dataclass
class ProviderCircuitSnapshot:
    provider: str
    state: str
    failure_count: int
    opened_at: float | None
    reset_after_seconds: int
    remaining_open_seconds: float


class ProviderCircuitBreaker:
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"

    def __init__(
        self,
        *,
        provider: str,
        failure_threshold: int,
        reset_seconds: int,
    ) -> None:
        self.provider = provider
        self.failure_threshold = max(1, failure_threshold)
        self.reset_seconds = max(1, reset_seconds)

        self._state = self.CLOSED
        self._failure_count = 0
        self._opened_at: float | None = None

    def allow_request(self) -> bool:
        if self._state == self.CLOSED:
            return True

        if self._state == self.HALF_OPEN:
            return True

        if self._opened_at is None:
            self._state = self.HALF_OPEN
            return True

        elapsed = time.monotonic() - self._opened_at
        if elapsed >= self.reset_seconds:
            self._state = self.HALF_OPEN
            return True

        return False

    def record_success(self) -> None:
        self._state = self.CLOSED
        self._failure_count = 0
        self._opened_at = None

    def record_failure(self) -> None:
        self._failure_count += 1

        if self._state == self.HALF_OPEN:
            self._open()
            return

        if self._failure_count >= self.failure_threshold:
            self._open()

    def _open(self) -> None:
        self._state = self.OPEN
        self._opened_at = time.monotonic()

    def snapshot(self) -> ProviderCircuitSnapshot:
        remaining = 0.0

        if self._state == self.OPEN and self._opened_at is not None:
            elapsed = time.monotonic() - self._opened_at
            remaining = max(0.0, self.reset_seconds - elapsed)

        return ProviderCircuitSnapshot(
            provider=self.provider,
            state=self._state,
            failure_count=self._failure_count,
            opened_at=self._opened_at,
            reset_after_seconds=self.reset_seconds,
            remaining_open_seconds=remaining,
        )