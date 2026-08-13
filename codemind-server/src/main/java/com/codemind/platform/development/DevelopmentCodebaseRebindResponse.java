package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseRebindResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String projectCode,
        String targetStack,
        boolean rebound,
        String bindingStatus,
        String requestedRelativeRootPath,
        String requestedPlatformRootPath,
        DevelopmentCodebaseRegistryResponse codebaseRegistry,
        DevelopmentCodebaseBindingValidationResponse validation,
        List<String> blockers,
        List<String> warnings,
        String summary,
        LocalDateTime reboundAt
) {
}
