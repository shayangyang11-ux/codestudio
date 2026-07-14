package com.codemind.platform.module;

import java.util.List;

public record ProjectModuleMutationResponse(
        ProjectModuleService.ModuleTreeNode node,
        List<ProjectModuleService.ModuleTreeNode> tree
) {
}