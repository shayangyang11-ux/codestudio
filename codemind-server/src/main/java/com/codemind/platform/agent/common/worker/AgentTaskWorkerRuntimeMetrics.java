package com.codemind.platform.agent.common.worker;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AgentTaskWorkerRuntimeMetrics {

    private final AtomicLong pollCount = new AtomicLong();
    private final AtomicLong skippedPollCount = new AtomicLong();
    private final AtomicLong emptyPollCount = new AtomicLong();
    private final AtomicLong acquiredTaskCount = new AtomicLong();
    private final AtomicLong dispatchRejectedCount = new AtomicLong();
    private final AtomicLong executionSuccessCount = new AtomicLong();
    private final AtomicLong executionFailureCount = new AtomicLong();
    private final AtomicLong retryScheduledCount = new AtomicLong();
    private final AtomicLong dependencyUnblockedCount = new AtomicLong();
    private final AtomicLong dependencyStillBlockedCount = new AtomicLong();

    private volatile LocalDateTime lastPollAt;
    private volatile LocalDateTime lastAcquireAt;
    private volatile LocalDateTime lastSuccessAt;
    private volatile LocalDateTime lastFailureAt;

    private final AtomicLong moduleSerialDeferredCount = new AtomicLong();

    public void recordPoll() {
        pollCount.incrementAndGet();
        lastPollAt = LocalDateTime.now();
    }

    public void recordSkippedPoll() {
        skippedPollCount.incrementAndGet();
    }

    public void recordEmptyPoll() {
        emptyPollCount.incrementAndGet();
    }

    public void recordAcquiredTask() {
        acquiredTaskCount.incrementAndGet();
        lastAcquireAt = LocalDateTime.now();
    }

    public void recordDispatchRejected() {
        dispatchRejectedCount.incrementAndGet();
    }

    public void recordExecutionSuccess() {
        executionSuccessCount.incrementAndGet();
        lastSuccessAt = LocalDateTime.now();
    }

    public void recordExecutionFailure() {
        executionFailureCount.incrementAndGet();
        lastFailureAt = LocalDateTime.now();
    }

    public void recordRetryScheduled() {
        retryScheduledCount.incrementAndGet();
    }

    public long getPollCount() {
        return pollCount.get();
    }

    public long getSkippedPollCount() {
        return skippedPollCount.get();
    }

    public long getEmptyPollCount() {
        return emptyPollCount.get();
    }

    public long getAcquiredTaskCount() {
        return acquiredTaskCount.get();
    }

    public long getDispatchRejectedCount() {
        return dispatchRejectedCount.get();
    }

    public long getExecutionSuccessCount() {
        return executionSuccessCount.get();
    }

    public long getExecutionFailureCount() {
        return executionFailureCount.get();
    }

    public long getRetryScheduledCount() {
        return retryScheduledCount.get();
    }

    public LocalDateTime getLastPollAt() {
        return lastPollAt;
    }

    public LocalDateTime getLastAcquireAt() {
        return lastAcquireAt;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public LocalDateTime getLastFailureAt() {
        return lastFailureAt;
    }

    public void recordModuleSerialDeferred() {
        moduleSerialDeferredCount.incrementAndGet();
    }

    public long getModuleSerialDeferredCount() {
        return moduleSerialDeferredCount.get();
    }

    public void recordDependencyUnblocked() {
        dependencyUnblockedCount.incrementAndGet();
    }

    public void recordDependencyStillBlocked() {
        dependencyStillBlockedCount.incrementAndGet();
    }

    public long getDependencyUnblockedCount() {
        return dependencyUnblockedCount.get();
    }

    public long getDependencyStillBlockedCount() {
        return dependencyStillBlockedCount.get();
    }
}