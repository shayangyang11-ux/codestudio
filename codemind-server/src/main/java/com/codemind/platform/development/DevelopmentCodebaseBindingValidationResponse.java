package com.codemind.platform.development;

import java.util.List;

public record DevelopmentCodebaseBindingValidationResponse(
        String relativeRootPath,
        String platformRootPath,
        String absoluteRootPath,
        boolean directoryExists,
        boolean packageJsonExists,
        boolean manifestExists,
        boolean manifestReadable,
        boolean manifestContainsNodes,
        int manifestNodeCount,
        int fileCount,
        long totalBytes,
        List<String> blockers,
        List<String> warnings
) {
}
