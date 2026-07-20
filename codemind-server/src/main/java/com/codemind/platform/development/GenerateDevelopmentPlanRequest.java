package com.codemind.platform.development;

public record GenerateDevelopmentPlanRequest(
        String planScope,
        String targetStack,
        String remarks
) {
}
