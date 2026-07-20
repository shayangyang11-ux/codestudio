package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentPlanResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String moduleType,
        String nodeType,
        boolean ready,
        String planStatus,
        String planChecksum,
        LocalDateTime generatedAt,
        String summary,
        List<String> principles,
        List<String> sourceArtifactTypes,
        List<DevelopmentPlanFileItem> frontendFiles,
        List<DevelopmentPlanFileItem> backendFiles,
        List<DevelopmentPlanApiItem> apiItems,
        List<DevelopmentPlanDatabaseItem> databaseItems,
        List<DevelopmentPlanStepItem> implementationSteps,
        List<DevelopmentPlanSourceMapping> sourceMappings,
        DevelopmentHandoffContextResponse handoffContext
) {
}
