from __future__ import annotations

import time
from dataclasses import dataclass, asdict
from threading import Lock
from typing import Any


@dataclass
class LlmRuntimeMetricsSnapshot:
    total_requests: int
    completed_requests: int
    failed_requests: int
    retried_requests: int
    rejected_by_busy: int
    rejected_by_circuit_open: int
    active_requests: int
    max_active_requests: int
    total_latency_ms: int
    last_success_at_epoch_ms: int | None
    last_failure_at_epoch_ms: int | None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class LlmRuntimeMetrics:
    def __init__(self) -> None:
        self._lock = Lock()

        self.total_requests = 0
        self.completed_requests = 0
        self.failed_requests = 0
        self.retried_requests = 0
        self.rejected_by_busy = 0
        self.rejected_by_circuit_open = 0
        self.active_requests = 0
        self.max_active_requests = 0
        self.total_latency_ms = 0
        self.last_success_at_epoch_ms: int | None = None
        self.last_failure_at_epoch_ms: int | None = None

    def record_request_started(self) -> None:
        with self._lock:
            self.total_requests += 1
            self.active_requests += 1
            self.max_active_requests = max(
                self.max_active_requests,
                self.active_requests,
            )

    def record_request_finished(
        self,
        *,
        success: bool,
        latency_ms: int,
    ) -> None:
        now_ms = int(time.time() * 1000)

        with self._lock:
            self.active_requests = max(0, self.active_requests - 1)
            self.total_latency_ms += max(0, latency_ms)

            if success:
                self.completed_requests += 1
                self.last_success_at_epoch_ms = now_ms
            else:
                self.failed_requests += 1
                self.last_failure_at_epoch_ms = now_ms

    def record_retry(self) -> None:
        with self._lock:
            self.retried_requests += 1

    def record_busy_rejected(self) -> None:
        with self._lock:
            self.rejected_by_busy += 1

    def record_circuit_rejected(self) -> None:
        with self._lock:
            self.rejected_by_circuit_open += 1

    def snapshot(self) -> LlmRuntimeMetricsSnapshot:
        with self._lock:
            return LlmRuntimeMetricsSnapshot(
                total_requests=self.total_requests,
                completed_requests=self.completed_requests,
                failed_requests=self.failed_requests,
                retried_requests=self.retried_requests,
                rejected_by_busy=self.rejected_by_busy,
                rejected_by_circuit_open=self.rejected_by_circuit_open,
                active_requests=self.active_requests,
                max_active_requests=self.max_active_requests,
                total_latency_ms=self.total_latency_ms,
                last_success_at_epoch_ms=self.last_success_at_epoch_ms,
                last_failure_at_epoch_ms=self.last_failure_at_epoch_ms,
            )