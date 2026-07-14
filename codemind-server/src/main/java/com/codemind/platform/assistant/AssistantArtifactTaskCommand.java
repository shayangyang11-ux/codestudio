package com.codemind.platform.assistant;

import com.codemind.platform.task.TaskType;

public record AssistantArtifactTaskCommand(
        boolean fullChain,
        TaskType taskType,
        String artifactType,
        String title,
        String requirementText,
        boolean overwriteExisting
) {
}