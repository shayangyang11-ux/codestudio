package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseBindingStatusResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String projectCode,
        String targetStack,
        String bindingStatus,
        boolean registered,
        boolean bindable,
        String storageRoot,
        String storageRootKey,
        String suggestedRelativeRootPath,
        String suggestedPlatformRootPath,
        DevelopmentCodebaseRegistryResponse codebaseRegistry,
        DevelopmentCodebaseBindingValidationResponse validation,
        List<String> blockers,
        List<String> warnings,
        String summary,
        LocalDateTime generatedAt
) {
}
