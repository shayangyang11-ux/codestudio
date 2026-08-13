package com.codemind.platform.development;

public record MaterializeDevelopmentCodeProjectRequest(
        String planScope,
        String targetStack,
        String remarks,
        Boolean allowOverwrite
) {
}
