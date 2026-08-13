package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DevelopmentRuntimeLogsResponse(
        UUID projectId,
        UUID moduleId,
        String targetStack,
        String codebaseRoot,
        String runtimeStatus,
        String installStatus,
        int lineCount,
        boolean truncated,
        List<String> lines,
        int errorLineCount,
        int warningLineCount,
        List<String> errorLines,
        List<String> warningLines,
        List<String> diagnosticSuggestions,
        LocalDateTime generatedAt
) {
}
