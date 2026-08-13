package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentCodeProjectResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String moduleType,
        String nodeType,
        String targetStack,
        String targetStackLabel,
        String generatorMode,
        String proposalStatus,
        String proposalChecksum,
        LocalDateTime generatedAt,
        String generationRoot,
        String summary,
        List<String> runtimeCommands,
        List<String> sourceArtifactTypes,
        List<String> warnings,
        List<DevelopmentCodeFileResponse> files,
        DevelopmentPlanResponse developmentPlan,
        DevelopmentHandoffContextResponse handoffContext,
        DevelopmentCodegenTraceResponse codegenTrace
) {
}

