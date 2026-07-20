package com.codemind.platform.development;

import java.util.List;

public record DevelopmentPlanApiItem(
        String method,
        String path,
        String purpose,
        List<String> sourceArtifactTypes,
        String implementationHint
) {
}
