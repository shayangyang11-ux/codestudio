package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DevelopmentCodegenTraceResponse(
        String traceId,
        String agentScope,
        String llmTask,
        String provider,
        String model,
        String generatorMode,
        String status,
        boolean aiGenerated,
        boolean fallbackUsed,
        int generatedFileCount,
        List<String> warnings,
        Map<String, Object> usage,
        LocalDateTime generatedAt
) {
}
