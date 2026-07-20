package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentPhase4AcceptanceResponse(
        UUID projectId,
        UUID moduleId,
        String moduleKey,
        String moduleName,
        String moduleType,
        String nodeType,
        String phaseKey,
        String phaseName,
        boolean readyForNextPhase,
        String completionLevel,
        int completionPercent,
        LocalDateTime generatedAt,
        List<DevelopmentPhase4AcceptanceItem> items,
        List<String> blockers,
        List<String> nextPhases,
        String summaryText
) {
}
