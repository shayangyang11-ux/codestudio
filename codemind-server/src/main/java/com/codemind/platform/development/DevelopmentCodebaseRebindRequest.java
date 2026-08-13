package com.codemind.platform.development;

public record DevelopmentCodebaseRebindRequest(
        String targetStack,
        String relativeRootPath,
        String platformRootPath,
        String remarks
) {
}
