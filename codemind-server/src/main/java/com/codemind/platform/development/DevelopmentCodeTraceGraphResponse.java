package com.codemind.platform.development;

import java.util.List;

public record DevelopmentCodeTraceGraphResponse(
        String schemaVersion,
        String selectedNodeId,
        String selectedNodeType,
        int nodeCount,
        int fileCount,
        int edgeCount,
        int selectedNodeFileCount,
        List<DevelopmentCodeTraceNodeResponse> nodes,
        List<DevelopmentCodeTraceFileResponse> files,
        List<DevelopmentCodeTraceEdgeResponse> edges,
        String summary
) {
}
