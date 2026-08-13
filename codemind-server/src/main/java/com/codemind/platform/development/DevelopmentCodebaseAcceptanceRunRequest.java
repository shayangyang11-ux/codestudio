package com.codemind.platform.development;

public record DevelopmentCodebaseAcceptanceRunRequest(
        String targetStack,
        Boolean installDependencies,
        Boolean buildProject,
        Boolean runTests,
        Boolean checkRuntimeHealth
) {
}
