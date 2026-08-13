package com.codemind.platform.development;

import java.util.List;

public record DevelopmentCodeTraceFileResponse(
        String filePath,
        String fileType,
        String language,
        String relationship,
        boolean exists,
        long bytes,
        List<String> affectedNodeIds,
        List<String> affectedNodes
) {
}
