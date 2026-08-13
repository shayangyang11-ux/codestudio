package com.codemind.platform.development;

import java.util.List;

public record DevelopmentCodebaseAcceptanceItemResponse(
        String id,
        String phase,
        String title,
        String status,
        boolean required,
        boolean passed,
        boolean commandExecuted,
        String command,
        Integer exitCode,
        Long durationMillis,
        String summary,
        List<String> details,
        List<String> logTail
) {
}
