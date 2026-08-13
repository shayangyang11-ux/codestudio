package com.codemind.platform.development;

public record DevelopmentCodebaseDeploymentApplyRequest(
        String targetStack,
        Boolean overwriteExisting,
        String remarks
) {
}
