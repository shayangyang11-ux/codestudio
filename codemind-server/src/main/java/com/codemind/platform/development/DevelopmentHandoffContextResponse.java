package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DevelopmentHandoffContextResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String moduleType,
        String nodeType,
        LocalDateTime generatedAt,
        DevelopmentHandoffReadinessResponse readiness,
        List<DevelopmentHandoffArtifactPayload> artifacts,
        Map<String, Object> confirmedUiContract,
        Map<String, Object> themeProfileSnapshot
) {
}
