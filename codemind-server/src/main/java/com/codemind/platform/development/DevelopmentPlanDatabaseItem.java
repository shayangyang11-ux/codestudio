package com.codemind.platform.development;

import java.util.List;

public record DevelopmentPlanDatabaseItem(
        String objectName,
        String changeType,
        List<String> sourceArtifactTypes,
        String description
) {
}
