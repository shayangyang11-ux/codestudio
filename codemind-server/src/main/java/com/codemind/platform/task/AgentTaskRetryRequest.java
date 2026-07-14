package com.codemind.platform.task;

public record AgentTaskRetryRequest(
        String reason,
        Integer maxAttempts
) {
}