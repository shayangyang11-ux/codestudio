package com.codemind.platform.development;

public record DevelopmentRuntimeActionRequest(
        String targetStack,
        Boolean forceRestart
) {
}
