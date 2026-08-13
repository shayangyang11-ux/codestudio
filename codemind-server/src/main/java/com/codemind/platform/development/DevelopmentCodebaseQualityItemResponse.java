package com.codemind.platform.development;

import java.util.List;

public record DevelopmentCodebaseQualityItemResponse(
        String id,
        String category,
        String title,
        String status,
        boolean required,
        boolean passed,
        int scoreImpact,
        String summary,
        List<String> details,
        List<String> filePaths,
        String recommendation
) {
}
