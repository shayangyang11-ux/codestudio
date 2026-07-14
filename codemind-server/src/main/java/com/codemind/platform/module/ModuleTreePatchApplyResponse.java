package com.codemind.platform.module;

import java.util.List;

public record ModuleTreePatchApplyResponse(
        String description,
        String source,
        int operationCount,
        List<String> appliedOperations,
        List<ProjectModuleService.ModuleTreeNode> tree
) {
}