package com.codemind.platform.assistant;

import java.util.List;

public record AssistantArtifactTaskDispatchResult(
        String reply,
        List<AssistantActionResponse> actions
) {
}