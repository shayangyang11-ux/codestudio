package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodebaseRepairResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String projectCode,
        String targetStack,
        String repairStatus,
        boolean aiGenerated,
        boolean applied,
        String provider,
        String model,
        String generatorMode,
        String codebaseRoot,
        String absoluteCodebaseRoot,
        int failedItemCount,
        int repairFileCount,
        LocalDateTime generatedAt,
        String summary,
        List<String> blockers,
        List<String> warnings,
        List<String> nextActions,
        List<DevelopmentCodebaseAcceptanceItemResponse> failedItems,
        DevelopmentCodeProjectResponse repairCodeProject,
        DevelopmentCodeMaterializationResponse materialization,
        DevelopmentCodebaseAcceptanceResponse postRepairAcceptance
) {
}
