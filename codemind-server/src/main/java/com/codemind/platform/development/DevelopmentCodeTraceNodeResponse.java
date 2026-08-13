package com.codemind.platform.development;

public record DevelopmentCodeTraceNodeResponse(
        String nodeId,
        String nodeType,
        String nodeName,
        String nodeKey,
        String previewRoute,
        int ownedFileCount,
        int relatedFileCount,
        int sharedFileCount
) {
}
