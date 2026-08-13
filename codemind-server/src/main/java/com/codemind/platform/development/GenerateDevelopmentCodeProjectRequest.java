package com.codemind.platform.development;

public record GenerateDevelopmentCodeProjectRequest(
        String planScope,
        String targetStack,
        String remarks
) {
}
