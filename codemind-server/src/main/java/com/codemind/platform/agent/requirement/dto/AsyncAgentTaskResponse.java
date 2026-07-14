package com.codemind.platform.agent.requirement.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AsyncAgentTaskResponse(
        UUID taskId,
        UUID projectId,
        String projectCode,
        UUID moduleId,
        String taskType,
        String status,
        LocalDateTime createdAt,
        boolean reused
) {
}