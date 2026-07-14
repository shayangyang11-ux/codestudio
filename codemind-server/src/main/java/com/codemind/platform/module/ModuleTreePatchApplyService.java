package com.codemind.platform.module;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ModuleTreePatchApplyService {

    private final ProjectModuleService projectModuleService;

    public ModuleTreePatchApplyService(
            ProjectModuleService projectModuleService
    ) {
        this.projectModuleService = projectModuleService;
    }

    @Transactional
    public ModuleTreePatchApplyResponse applyPatch(
            UUID workspaceId,
            String projectCode,
            ApplyModuleTreePatchRequest request
    ) {
        if (request == null || request.getOperations() == null || request.getOperations().isEmpty()) {
            throw new IllegalArgumentException("Tree Patch 操作不能为空。");
        }

        List<String> appliedOperations = new ArrayList<>();
        ProjectModuleMutationResponse latestResponse = null;

        for (ModuleTreePatchOperationRequest operation : request.getOperations()) {
            if (operation == null) {
                continue;
            }

            String op = normalizeOp(operation.getOp());

            switch (op) {
                case "ADD_NODE" -> {
                    latestResponse = applyAddNode(
                            workspaceId,
                            projectCode,
                            operation
                    );
                    appliedOperations.add("ADD_NODE: " + safeText(operation.getModuleName()));
                }

                case "UPDATE_NODE" -> {
                    latestResponse = applyUpdateNode(
                            workspaceId,
                            projectCode,
                            operation
                    );
                    appliedOperations.add("UPDATE_NODE: " + operation.getNodeId());
                }

                case "MOVE_NODE" -> {
                    latestResponse = applyMoveNode(
                            workspaceId,
                            projectCode,
                            operation
                    );
                    appliedOperations.add("MOVE_NODE: " + operation.getNodeId());
                }

                case "DELETE_NODE" -> {
                    latestResponse = applyDeleteNode(
                            workspaceId,
                            projectCode,
                            operation
                    );
                    appliedOperations.add("DELETE_NODE: " + operation.getNodeId());
                }

                default -> throw new IllegalArgumentException("不支持的 Tree Patch 操作：" + operation.getOp());
            }
        }

        List<ProjectModuleService.ModuleTreeNode> tree = latestResponse == null
                ? projectModuleService.getModuleTreeByWorkspaceAndProjectCode(workspaceId, projectCode)
                : latestResponse.tree();

        return new ModuleTreePatchApplyResponse(
                request.getDescription(),
                request.getSource(),
                request.getOperations().size(),
                appliedOperations,
                tree
        );
    }

    private ProjectModuleMutationResponse applyAddNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        if (!hasText(operation.getModuleName())) {
            throw new IllegalArgumentException("ADD_NODE 操作缺少 moduleName。");
        }

        if (!hasText(operation.getModuleType())) {
            throw new IllegalArgumentException("ADD_NODE 操作缺少 moduleType。");
        }

        CreateProjectModuleNodeRequest createRequest = new CreateProjectModuleNodeRequest();
        createRequest.setParentId(operation.getParentId());
        createRequest.setModuleName(operation.getModuleName());
        createRequest.setModuleKey(operation.getModuleKey());
        createRequest.setModuleType(operation.getModuleType());
        createRequest.setSortOrder(operation.getSortOrder());
        createRequest.setExtra(operation.getExtra());

        return projectModuleService.createModuleNode(
                workspaceId,
                projectCode,
                createRequest
        );
    }

    private ProjectModuleMutationResponse applyUpdateNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        UUID nodeId = requireNodeId(operation);

        UpdateProjectModuleNodeRequest updateRequest = new UpdateProjectModuleNodeRequest();
        updateRequest.setModuleName(operation.getModuleName());
        updateRequest.setModuleKey(operation.getModuleKey());
        updateRequest.setModuleType(operation.getModuleType());
        updateRequest.setSortOrder(operation.getSortOrder());
        updateRequest.setExtra(operation.getExtra());

        return projectModuleService.updateModuleNode(
                workspaceId,
                projectCode,
                nodeId,
                updateRequest
        );
    }

    private ProjectModuleMutationResponse applyMoveNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        UUID nodeId = requireNodeId(operation);

        MoveProjectModuleNodeRequest moveRequest = new MoveProjectModuleNodeRequest();
        moveRequest.setParentId(operation.getNewParentId());
        moveRequest.setSortOrder(operation.getSortOrder());

        return projectModuleService.moveModuleNode(
                workspaceId,
                projectCode,
                nodeId,
                moveRequest
        );
    }

    private ProjectModuleMutationResponse applyDeleteNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        UUID nodeId = requireNodeId(operation);

        return projectModuleService.deleteModuleNode(
                workspaceId,
                projectCode,
                nodeId
        );
    }

    private UUID requireNodeId(
            ModuleTreePatchOperationRequest operation
    ) {
        if (operation.getNodeId() == null) {
            throw new IllegalArgumentException(operation.getOp() + " 操作缺少 nodeId。");
        }

        return operation.getNodeId();
    }

    private String normalizeOp(String rawOp) {
        if (rawOp == null || rawOp.isBlank()) {
            throw new IllegalArgumentException("Tree Patch 操作类型不能为空。");
        }

        return rawOp.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        return value;
    }
}