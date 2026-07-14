package com.codemind.platform.task;

public record AgentTaskErrorDiagnosis(
        String errorCode,
        String userMessage,
        String originalErrorCode,
        String originalErrorMessage,
        boolean retryable,
        String actionHint
) {
}