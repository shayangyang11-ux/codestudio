package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;

public record DevelopmentCodebaseSafetyCheckResponse(
        String checkStatus,
        boolean safeToApply,
        boolean allowOverwrite,
        int totalCount,
        int addedCount,
        int modifiedCount,
        int unchangedCount,
        int conflictCount,
        int skippedCount,
        List<String> blockers,
        List<String> warnings,
        List<DevelopmentCodebaseSafetyFileResponse> files,
        LocalDateTime checkedAt
) {
}
