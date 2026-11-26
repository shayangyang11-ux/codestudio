package com.codemind.platform.module;

import com.codemind.platform.artifact.ArtifactService;

import com.codemind.platform.project.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
public class ModuleTreePatchApplyService {

    private final ProjectModuleService projectModuleService;
    private final ProjectModuleRepository projectModuleRepository;
    private final ProjectAccessService projectAccessService;
    private final ArtifactService artifactService;

    public ModuleTreePatchApplyService(
            ProjectModuleService projectModuleService,
            ProjectModuleRepository projectModuleRepository,
            ProjectAccessService projectAccessService,
            ArtifactService artifactService
    ) {
        this.projectModuleService = projectModuleService;
        this.projectModuleRepository = projectModuleRepository;
        this.projectAccessService = projectAccessService;
        this.artifactService = artifactService;
    }

    @Transactional
    public ModuleTreePatchApplyResponse applyPatch(
            UUID workspaceId,
            String projectCode,
            ApplyModuleTreePatchRequest request
    ) {
        if (request == null || request.getOperations() == null || request.getOperations().isEmpty()) {
            throw new IllegalArgumentException("模块树 Patch 操作不能为空。");
        }

        ProjectAccessService.ProjectAccess project =
                projectAccessService.resolveAccessibleProject(
                        workspaceId,
                        projectCode
                );

        validateModuleTreeBaseline(
                request,
                project.projectId()
        );

        List<ProjectModuleService.ModuleTreeNode> currentTree =
                projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                        workspaceId,
                        projectCode
                );

        List<ModuleTreePatchOperationRequest> operations = normalizeOperationsBeforeApply(
                request,
                currentTree
        );

        if (operations.isEmpty()) {
            throw new IllegalArgumentException("没有可安全应用的模块树 Patch 操作。");
        }

        List<String> appliedOperations = new ArrayList<>();
        ProjectModuleMutationResponse latestResponse = null;
        UUID a91561LastCreatedFeatureId = null;

        for (ModuleTreePatchOperationRequest operation : operations) {
            String op = normalizeOperationType(operation.getOp());

            switch (op) {
                case "ADD_NODE" -> {
                    String a91561AddType = normalizeModuleType(
                            operation.getModuleType(),
                            operation.getModuleName()
                    );

                    // A9.10.14.4.3.15.4.15.6.1_PATCH_APPLY_FINAL_MODULE_FEATURE_PAGE
                    // 同一提案内常见顺序是：先新增 FEATURE，再新增该 FEATURE 的 PAGE。
                    // normalizeOperationsBeforeApply 只能看见旧树，看不见刚创建的 FEATURE，
                    // 因此 PAGE 不能继续挂到最终 MODULE 下，必须转挂到本次刚创建的 FEATURE。
                    if ("PAGE".equals(a91561AddType) && a91561LastCreatedFeatureId != null) {
                        operation.setParentId(a91561LastCreatedFeatureId);
                    }

                    latestResponse = applyAddNode(
                            workspaceId,
                            projectCode,
                            operation
                    );

                    if (latestResponse != null && latestResponse.node() != null) {
                        String a91561CreatedType = normalizeModuleType(
                                latestResponse.node().getModuleType(),
                                latestResponse.node().getModuleName()
                        );

                        if ("FEATURE".equals(a91561CreatedType)
                                && latestResponse.node().getId() != null) {
                            a91561LastCreatedFeatureId = latestResponse.node().getId();

                            a91565SyncAddedFeatureToModulePrd(
                                    project.projectId(),
                                    latestResponse.node(),
                                    operation
                            );
                        }
                    }

                    appliedOperations.add("ADD_NODE: " + safeText(operation.getModuleName()));
                }
                case "UPDATE_NODE" -> {
                    latestResponse = applyUpdateNode(
                            workspaceId,
                            projectCode,
                            operation
                    );
                    appliedOperations.add("UPDATE_NODE: " + safeUuid(operation.getNodeId()));
                }
                case "MOVE_NODE" -> {
                    latestResponse = applyMoveNode(
                            workspaceId,
                            projectCode,
                            operation
                    );
                    appliedOperations.add("MOVE_NODE: " + safeUuid(operation.getNodeId()));
                }
                case "DELETE_NODE" -> {
                    ProjectModuleService.ModuleTreeNode a91565DeletedNode = findNodeById(
                            flatten(currentTree),
                            operation.getNodeId()
                    );

                    latestResponse = applyDeleteNode(
                            workspaceId,
                            projectCode,
                            operation
                    );

                    a91565SyncDeletedFeatureFromModulePrd(
                            project.projectId(),
                            a91565DeletedNode
                    );

                    appliedOperations.add("DELETE_NODE: " + safeUuid(operation.getNodeId()));
                }
                default -> throw new IllegalArgumentException("不支持的模块树 Patch 操作：" + safeText(operation.getOp()));
            }

            currentTree = latestResponse == null
                    ? projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                    workspaceId,
                    projectCode
            )
                    : latestResponse.tree();
        }

        List<ProjectModuleService.ModuleTreeNode> tree = latestResponse == null
                ? projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        )
                : latestResponse.tree();

        return new ModuleTreePatchApplyResponse(
                request.getDescription(),
                request.getSource(),
                appliedOperations.size(),
                appliedOperations,
                tree
        );
    }

    private void validateModuleTreeBaseline(
            ApplyModuleTreePatchRequest request,
            UUID projectId
    ) {
        String expectedHash = firstNonBlank(
                request == null ? null : request.getBaseModuleTreeHash(),
                firstOperationExtraText(request, "baseModuleTreeHash")
        );
        Integer expectedNodeCount = request == null ? null : request.getBaseModuleTreeNodeCount();

        if (expectedNodeCount == null) {
            expectedNodeCount = firstOperationExtraInteger(
                    request,
                    "baseModuleTreeNodeCount"
            );
        }

        if (!ModuleTreeBaselineSupport.hasBaseline(expectedHash, expectedNodeCount)) {
            return;
        }

        ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot current =
                ModuleTreeBaselineSupport.capture(
                        projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                );

        List<String> mismatches = new ArrayList<>();

        if (expectedHash != null && !expectedHash.isBlank()
                && !Objects.equals(expectedHash.trim(), current.hash())) {
            mismatches.add("moduleTreeHash");
        }

        if (expectedNodeCount != null && !Objects.equals(expectedNodeCount, current.nodeCount())) {
            mismatches.add("moduleTreeNodeCount");
        }

        if (!mismatches.isEmpty()) {
            throw new IllegalArgumentException(
                    ModuleTreeBaselineSupport.buildMismatchMessage(
                            "模块树已经发生变化，请重新生成模块树变更提案后再应用。",
                            expectedHash,
                            expectedNodeCount,
                            current
                    )
                            + "；不一致字段：" + String.join(", ", mismatches)
            );
        }
    }


    // A9.10.14.4.3.15.4.15.6.1_FIX_APPLY_ADD_NODE_RUNTIME_PARENT_GUARD
    private ProjectModuleMutationResponse applyAddNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        if (operation == null || !hasText(operation.getModuleName())) {
            throw new IllegalArgumentException("ADD_NODE 操作缺少 moduleName。");
        }

        if (!hasText(operation.getModuleType())) {
            operation.setModuleType(
                    normalizeModuleType(
                            operation.getModuleType(),
                            operation.getModuleName()
                    )
            );
        }

        String resolvedType = normalizeModuleType(
                operation.getModuleType(),
                operation.getModuleName()
        );

        resolvedType = a915616ResolveAddNodeTypeAndParentByRuntimeTree(
                workspaceId,
                projectCode,
                operation,
                resolvedType
        );

        operation.setModuleType(resolvedType);

        CreateProjectModuleNodeRequest createRequest = new CreateProjectModuleNodeRequest();
        String resolvedModuleName = a91562NormalizePatchNodeName(
                operation.getModuleName(),
                resolvedType
        );
        if (hasText(resolvedModuleName)) {
            operation.setModuleName(resolvedModuleName);
        }

        createRequest.setParentId(operation.getParentId());
        createRequest.setModuleName(hasText(resolvedModuleName) ? resolvedModuleName : operation.getModuleName());
        createRequest.setModuleKey(operation.getModuleKey());
        createRequest.setModuleType(resolvedType);
        createRequest.setSortOrder(operation.getSortOrder());

        Map<String, Object> extra = operation.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(operation.getExtra());
        extra.put("treePatchApplyRuntimeGuard", true);
        extra.put("treePatchApplyRuntimeGuardVersion", "A9.10.14.4.3.15.4.15.6.1");
        extra.put("treePatchApplyResolvedModuleType", resolvedType);
        createRequest.setExtra(extra);

        return projectModuleService.createModuleNode(
                workspaceId,
                projectCode,
                createRequest
        );
    }

    private String a915616ResolveAddNodeTypeAndParentByRuntimeTree(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation,
            String requestedType
    ) {
        if (operation == null) {
            return requestedType;
        }

        List<ProjectModuleService.ModuleTreeNode> currentTree =
                projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                        workspaceId,
                        projectCode
                );
        List<ProjectModuleService.ModuleTreeNode> flatNodes = flatten(currentTree);

        ProjectModuleService.ModuleTreeNode explicitParent =
                a91562ResolveExplicitMentionedParentForApply(
                        operation,
                        requestedType,
                        flatNodes
                );

        if (explicitParent != null && explicitParent.getId() != null) {
            operation.setParentId(explicitParent.getId());
        }

        if (operation.getParentId() == null) {
            return requestedType;
        }

        ProjectModuleService.ModuleTreeNode parent = findNodeById(
                flatNodes,
                operation.getParentId()
        );

        if (parent == null) {
            return requestedType;
        }

        String parentType = normalizeModuleType(
                parent.getModuleType(),
                parent.getModuleName()
        );

        if ("FEATURE".equals(parentType)) {
            return "PAGE";
        }

        if ("PAGE".equals(parentType)) {
            throw new IllegalArgumentException("页面下不支持继续新增业务节点。");
        }

        if (!"MODULE".equals(parentType)) {
            return requestedType;
        }

        if (!a915616IsFinalRequirementModuleNode(parent)) {
            return requestedType;
        }

        if ("PAGE".equals(requestedType)) {
            ProjectModuleService.ModuleTreeNode carrier = a915616FindFeatureCarrierForPage(
                    flatNodes,
                    parent.getId(),
                    operation.getModuleName()
            );

            if (carrier == null) {
                ProjectModuleMutationResponse carrierResponse = a915616CreateFeatureCarrierForPage(
                        workspaceId,
                        projectCode,
                        parent,
                        operation
                );

                carrier = carrierResponse == null ? null : carrierResponse.node();
            }

            if (carrier == null || carrier.getId() == null) {
                throw new IllegalArgumentException("页面节点缺少业务能力承载，无法应用模块树变更。");
            }

            operation.setParentId(carrier.getId());
            return "PAGE";
        }

        // 最终 MODULE 下只能新增业务能力。这里兜住历史提案里的 MODULE/SUBMODULE/中文类型。
        return "FEATURE";
    }

    private boolean a915616IsFinalRequirementModuleNode(
            ProjectModuleService.ModuleTreeNode node
    ) {
        if (node == null) {
            return false;
        }

        if (!"MODULE".equals(normalizeModuleType(node.getModuleType(), node.getModuleName()))) {
            return false;
        }

        Map<String, Object> extra = node.getExtra() == null
                ? Map.of()
                : node.getExtra();

        if (Boolean.TRUE.equals(extra.get("finalRequirementModule"))
                || Boolean.TRUE.equals(extra.get("hasModulePrd"))) {
            return true;
        }

        Object mode = extra.get("requirementMode");
        if (mode == null) {
            mode = extra.get("requirement_mode");
        }

        return mode != null && "REQUIREMENT_MODULE".equalsIgnoreCase(String.valueOf(mode));
    }

    // A9.10.14.4.3.15.4.15.6.6_EXACT_FEATURE_CARRIER_AND_PRD_SYNC
    private ProjectModuleService.ModuleTreeNode a915616FindFeatureCarrierForPage(
            List<ProjectModuleService.ModuleTreeNode> flatNodes,
            UUID finalModuleId,
            String pageName
    ) {
        if (flatNodes == null || flatNodes.isEmpty() || finalModuleId == null) {
            return null;
        }

        String target = a915616InferFeatureCarrierName(pageName);
        String normalizedTarget = a91566NormalizeCarrierName(target);

        if (!hasText(normalizedTarget)) {
            return null;
        }

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            if (node == null || node.getId() == null) {
                continue;
            }

            if (!finalModuleId.equals(node.getParentId())) {
                continue;
            }

            if (!"FEATURE".equals(normalizeModuleType(node.getModuleType(), node.getModuleName()))) {
                continue;
            }

            String normalizedName = a91566NormalizeCarrierName(node.getModuleName());

            // 只允许精确归一化匹配。
            // 订单取消 != 订单取消2，避免新增页面被错误挂到旧功能点下。
            if (normalizedName.equals(normalizedTarget)) {
                return node;
            }
        }

        return null;
    }



    private ProjectModuleMutationResponse a915616CreateFeatureCarrierForPage(
            UUID workspaceId,
            String projectCode,
            ProjectModuleService.ModuleTreeNode finalModule,
            ModuleTreePatchOperationRequest pageOperation
    ) {
        String featureName = a915616InferFeatureCarrierName(
                pageOperation == null ? "" : pageOperation.getModuleName()
        );

        CreateProjectModuleNodeRequest featureRequest = new CreateProjectModuleNodeRequest();
        featureRequest.setParentId(finalModule.getId());
        featureRequest.setModuleName(featureName);
        featureRequest.setModuleType("FEATURE");

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", "MODULE_TREE_PATCH_APPLY_RUNTIME_GUARD");
        extra.put("sourceType", "AI_COMPLETED");
        extra.put("confirmationStatus", "PENDING_CONFIRMATION");
        extra.put("generatedAsPageCarrier", true);
        extra.put("carrierForPageName", pageOperation == null ? "" : safeText(pageOperation.getModuleName()));
        extra.put("treePatchApplyRuntimeGuardVersion", "A9.10.14.4.3.15.4.15.6.1");
        featureRequest.setExtra(extra);

        ProjectModuleMutationResponse response = projectModuleService.createModuleNode(
                workspaceId,
                projectCode,
                featureRequest
        );

        try {
            ProjectAccessService.ProjectAccess project =
                    projectAccessService.resolveAccessibleProject(
                            workspaceId,
                            projectCode
                    );

            if (response != null && response.node() != null) {
                a91565SyncAddedFeatureToModulePrd(
                        project.projectId(),
                        response.node(),
                        pageOperation
                );
            }
        } catch (Exception ignored) {
            // carrier 创建成功后，PRD 同步失败不能打断 PAGE 创建；
            // 影响提示和后续同步链路继续兜底。
        }

        return response;
    }


    // A9.10.14.4.3.15.4.15.6.2_APPLY_TARGET_MODULE_RESOLVE_AND_PAGE_NAME_QUALITY
    private ProjectModuleService.ModuleTreeNode a91562ResolveExplicitMentionedParentForApply(
            ModuleTreePatchOperationRequest operation,
            String requestedType,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        if (operation == null || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        String instruction = a91562FirstNonBlank(
                a91561ExtraText(operation, "sourceInstruction"),
                a91561ExtraText(operation, "originalInstruction"),
                a91561ExtraText(operation, "instruction")
        );

        ProjectModuleService.ModuleTreeNode a91563StrictParent =
                a91563ResolveStrictMentionedTargetForApply(
                        instruction,
                        requestedType,
                        flatNodes
                );
        if (a91563StrictParent != null) {
            return a91563StrictParent;
        }

        String normalizedInstruction = a91562NormalizeForMatch(instruction);
        String normalizedChildName = a91562NormalizeForMatch(operation.getModuleName());

        if (normalizedInstruction.isBlank()) {
            return null;
        }

        ProjectModuleService.ModuleTreeNode best = null;
        int bestScore = 0;

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            if (node == null || node.getId() == null) {
                continue;
            }

            String nodeName = a91562NormalizeForMatch(node.getModuleName());
            String nodeKey = a91562NormalizeForMatch(node.getModuleKey());
            String nodeType = normalizeModuleType(node.getModuleType(), node.getModuleName());

            if (nodeName.isBlank() || nodeName.equals(normalizedChildName)) {
                continue;
            }

            int score = 0;
            if (normalizedInstruction.contains(nodeName)) {
                score = Math.max(score, nodeName.length() * 10);
            }
            if (!nodeKey.isBlank() && normalizedInstruction.contains(nodeKey)) {
                score = Math.max(score, nodeKey.length() * 8);
            }

            if (score <= 0) {
                continue;
            }

            if (!a91562CanAcceptChildForApply(node, requestedType)) {
                continue;
            }

            if ("MODULE".equals(nodeType)) {
                score += 30;
            }

            if (a915616IsFinalRequirementModuleNode(node)) {
                score += 50;
            }

            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }

        return best;
    }


    // A9.10.14.4.3.15.4.15.6.3_APPLY_STRICT_EXPLICIT_TARGET_MODULE_LOCK
    private ProjectModuleService.ModuleTreeNode a91563ResolveStrictMentionedTargetForApply(
            String instruction,
            String childType,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        String source = safeText(instruction).replaceAll("\\s+", "");
        if (source.isBlank() || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        String type = normalizeModuleType(childType, "");
        ProjectModuleService.ModuleTreeNode best = null;
        int bestScore = 0;

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            if (node == null || node.getId() == null) {
                continue;
            }

            if (!"MODULE".equals(normalizeModuleType(node.getModuleType(), node.getModuleName()))) {
                continue;
            }

            String nodeName = safeText(node.getModuleName()).replaceAll("\\s+", "");
            if (nodeName.isBlank()) {
                continue;
            }

            if (!source.contains(nodeName)) {
                continue;
            }

            if (!a91563CanAcceptStrictChildForApply(node, type)) {
                continue;
            }

            int score = nodeName.length() * 100;
            if (a915616IsFinalRequirementModuleNode(node)) {
                score += 10000;
            }
            if (source.contains("在" + nodeName)
                    || source.contains("给" + nodeName)
                    || source.contains(nodeName + "的")
                    || source.contains(nodeName + "模块")) {
                score += 5000;
            }

            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }

        return best;
    }

    private boolean a91563CanAcceptStrictChildForApply(
            ProjectModuleService.ModuleTreeNode parent,
            String childType
    ) {
        if (parent == null || childType == null || childType.isBlank()) {
            return false;
        }

        if (a915616IsFinalRequirementModuleNode(parent)) {
            return "FEATURE".equals(childType) || "PAGE".equals(childType);
        }

        return "MODULE".equals(childType) || "FEATURE".equals(childType);
    }


    private boolean a91562CanAcceptChildForApply(
            ProjectModuleService.ModuleTreeNode parent,
            String childType
    ) {
        if (parent == null) {
            return false;
        }

        String parentType = normalizeModuleType(parent.getModuleType(), parent.getModuleName());
        String type = normalizeModuleType(childType, "");

        if ("FEATURE".equals(parentType)) {
            return "PAGE".equals(type);
        }

        if ("PAGE".equals(parentType)) {
            return false;
        }

        if (!"MODULE".equals(parentType)) {
            return false;
        }

        if (a915616IsFinalRequirementModuleNode(parent)) {
            return "FEATURE".equals(type);
        }

        return "MODULE".equals(type) || "FEATURE".equals(type);
    }

    private String a91562NormalizePatchNodeName(
            String rawName,
            String moduleType
    ) {
        String name = safeText(rawName)
                .replaceAll("\\s+", "")
                .trim();

        if (name.isBlank()) {
            return "";
        }

        String type = normalizeModuleType(moduleType, name);

        if ("PAGE".equals(type)) {
            name = name
                    .replace("弹窗页面", "弹窗")
                    .replace("弹窗页", "弹窗")
                    .replace("弹框页面", "弹框")
                    .replace("弹框页", "弹框")
                    .replace("抽屉页面", "抽屉")
                    .replace("抽屉页", "抽屉")
                    .replace("窗口页面", "窗口")
                    .replace("窗口页", "窗口");
        }

        if ("FEATURE".equals(type)) {
            name = name
                    .replace("功能点功能", "功能点")
                    .replace("功能功能", "功能")
                    .replace("业务能力功能", "业务能力");
        }

        return name;
    }

    private String a91562NormalizeForMatch(String value) {
        return safeText(value)
                .replaceAll("\\s+", "")
                .replace("模块需求", "")
                .replace("模块", "")
                .replace("功能点", "")
                .replace("功能", "")
                .replace("页面", "")
                .replace("页", "")
                .toLowerCase(Locale.ROOT);
    }

    private String a91562FirstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String text = safeText(value);
            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }



    private String a91566NormalizeCarrierName(String value) {
        String text = safeText(value)
                .replaceAll("\\s+", "")
                .replace("确认弹窗", "")
                .replace("确认页面", "")
                .replace("确认页", "")
                .replace("弹窗", "")
                .replace("弹框", "")
                .replace("抽屉", "")
                .replace("窗口", "")
                .replace("页面", "")
                .replace("页", "")
                .trim();

        return text;
    }

    private String a915616InferFeatureCarrierName(String pageName) {
        String name = safeText(pageName)
                .replace("确认弹窗", "")
                .replace("确认页面", "")
                .replace("确认页", "")
                .replace("弹窗", "")
                .replace("弹框", "")
                .replace("抽屉", "")
                .replace("页面", "")
                .replace("页", "")
                .trim();

        if (!hasText(name)) {
            return "业务操作";
        }

        if (name.length() > 24) {
            return name.substring(0, 24);
        }

        return name;
    }





    private ProjectModuleMutationResponse applyUpdateNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        if (operation.getNodeId() == null) {
            throw new IllegalArgumentException("UPDATE_NODE 操作缺少 nodeId。");
        }

        UpdateProjectModuleNodeRequest updateRequest = new UpdateProjectModuleNodeRequest();
        updateRequest.setModuleName(operation.getModuleName());
        updateRequest.setModuleKey(operation.getModuleKey());
        updateRequest.setModuleType(operation.getModuleType());
        updateRequest.setSortOrder(operation.getSortOrder());
        updateRequest.setExtra(operation.getExtra());

        return projectModuleService.updateModuleNode(
                workspaceId,
                projectCode,
                operation.getNodeId(),
                updateRequest
        );
    }

    private ProjectModuleMutationResponse applyMoveNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        if (operation.getNodeId() == null) {
            throw new IllegalArgumentException("MOVE_NODE 操作缺少 nodeId。");
        }

        MoveProjectModuleNodeRequest moveRequest = new MoveProjectModuleNodeRequest();
        moveRequest.setParentId(operation.getNewParentId());
        moveRequest.setSortOrder(operation.getSortOrder());

        return projectModuleService.moveModuleNode(
                workspaceId,
                projectCode,
                operation.getNodeId(),
                moveRequest
        );
    }

    private ProjectModuleMutationResponse applyDeleteNode(
            UUID workspaceId,
            String projectCode,
            ModuleTreePatchOperationRequest operation
    ) {
        if (operation.getNodeId() == null) {
            throw new IllegalArgumentException("DELETE_NODE 操作缺少 nodeId。");
        }

        return projectModuleService.deleteModuleNode(
                workspaceId,
                projectCode,
                operation.getNodeId()
        );
    }


    // A9.10.14.4.3.15.4.15.6.5_FEATURE_PRD_FUNCTIONAL_SYNC
    private void a91565SyncAddedFeatureToModulePrd(
            UUID projectId,
            ProjectModuleService.ModuleTreeNode featureNode,
            ModuleTreePatchOperationRequest operation
    ) {
        if (projectId == null || featureNode == null || featureNode.getId() == null) {
            return;
        }

        if (!"FEATURE".equals(normalizeModuleType(featureNode.getModuleType(), featureNode.getModuleName()))) {
            return;
        }

        UUID ownerModuleId = featureNode.getParentId();
        if (ownerModuleId == null) {
            return;
        }

        try {
            var versions = artifactService.listModuleArtifactVersions(
                    projectId,
                    ownerModuleId,
                    "MODULE_PRD"
            );

            if (versions == null || versions.isEmpty()) {
                return;
            }

            var currentPrd = versions.get(0);
            Map<String, Object> content = a91565MutableContent(currentPrd.getContent());

            List<Map<String, Object>> functionalRequirements =
                    a91565MutableMapList(content.get("functional_requirements"));

            String featureName = safeText(featureNode.getModuleName());
            String featureKey = firstNonBlank(
                    safeText(featureNode.getModuleKey()),
                    a91565ExtraText(featureNode.getExtra(), "sourceRequirementKey"),
                    a91565FeatureKey(featureName)
            );

            if (!a91565ContainsRequirement(functionalRequirements, featureNode.getId(), featureKey, featureName)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("requirement_id", "fr_" + a91565FeatureKey(featureKey));
                item.put("requirementId", "fr_" + a91565FeatureKey(featureKey));
                item.put("requirement_key", featureKey);
                item.put("requirementKey", featureKey);
                item.put("title", featureName);
                item.put("name", featureName);
                item.put("requirement_name", featureName);
                item.put("feature_name", featureName);
                item.put("description", a91565BuildFeatureDescription(featureNode, operation));
                item.put("priority", "P1");
                item.put("type", "FUNCTIONAL_REQUIREMENT");
                item.put("sourceType", firstNonBlank(
                        a91565ExtraText(featureNode.getExtra(), "sourceType"),
                        a91565ExtraText(operation == null ? null : operation.getExtra(), "sourceType"),
                        "AI_COMPLETED"
                ));
                item.put("confirmationStatus", "CONFIRMED");
                item.put("treeNodeId", featureNode.getId().toString());
                item.put("treeNodeKey", safeText(featureNode.getModuleKey()));
                item.put("treeNodeType", "FEATURE");
                item.put("sourceInstruction", firstNonBlank(
                        a91565ExtraText(operation == null ? null : operation.getExtra(), "sourceInstruction"),
                        a91565ExtraText(operation == null ? null : operation.getExtra(), "originalInstruction"),
                        a91565ExtraText(featureNode.getExtra(), "sourceInstruction")
                ));
                item.put("syncSource", "MODULE_TREE_PATCH_APPLY");
                item.put("syncVersion", "A9.10.14.4.3.15.4.15.6.5");

                functionalRequirements.add(item);
            }

            content.put("functional_requirements", functionalRequirements);

            List<Map<String, Object>> structuredRequirements =
                    a91565MutableMapList(content.get("structured_requirements"));

            if (!a91565ContainsRequirement(structuredRequirements, featureNode.getId(), featureKey, featureName)) {
                Map<String, Object> structured = new LinkedHashMap<>();
                structured.put("requirementId", "fr_" + a91565FeatureKey(featureKey));
                structured.put("requirement_id", "fr_" + a91565FeatureKey(featureKey));
                structured.put("requirementKey", featureKey);
                structured.put("requirement_key", featureKey);
                structured.put("title", featureName);
                structured.put("type", "FUNCTIONAL_REQUIREMENT");
                structured.put("sourceSection", "functional_requirements");
                structured.put("source_section", "functional_requirements");
                structured.put("treeNodeId", featureNode.getId().toString());
                structured.put("treeNodeKey", safeText(featureNode.getModuleKey()));
                structured.put("sourceType", "AI_COMPLETED");
                structured.put("confirmationStatus", "CONFIRMED");
                structured.put("syncSource", "MODULE_TREE_PATCH_APPLY");
                structured.put("syncVersion", "A9.10.14.4.3.15.4.15.6.5");
                structuredRequirements.add(structured);
            }

            content.put("structured_requirements", structuredRequirements);
            content.put("moduleTreeFeatureSyncAt", LocalDateTime.now().toString());
            content.put("moduleTreeFeatureSyncVersion", "A9.10.14.4.3.15.4.15.6.5");

            artifactService.saveArtifact(
                    projectId,
                    ownerModuleId,
                    currentPrd.getTaskId(),
                    "MODULE_PRD",
                    firstNonBlank(safeText(currentPrd.getTitle()), "模块需求"),
                    content,
                    currentPrd.getContentMarkdown(),
                    "MODULE_TREE_PATCH_APPLY"
            );
        } catch (Exception ignored) {
            // PRD 同步失败不能打断模块树应用；影响提示仍由 ProjectModuleImpactService 兜底。
        }
    }

    private void a91565SyncDeletedFeatureFromModulePrd(
            UUID projectId,
            ProjectModuleService.ModuleTreeNode deletedNode
    ) {
        if (projectId == null || deletedNode == null || deletedNode.getId() == null) {
            return;
        }

        if (!"FEATURE".equals(normalizeModuleType(deletedNode.getModuleType(), deletedNode.getModuleName()))) {
            return;
        }

        UUID ownerModuleId = deletedNode.getParentId();
        if (ownerModuleId == null) {
            return;
        }

        try {
            var versions = artifactService.listModuleArtifactVersions(
                    projectId,
                    ownerModuleId,
                    "MODULE_PRD"
            );

            if (versions == null || versions.isEmpty()) {
                return;
            }

            var currentPrd = versions.get(0);
            Map<String, Object> content = a91565MutableContent(currentPrd.getContent());

            String featureName = safeText(deletedNode.getModuleName());
            String featureKey = firstNonBlank(
                    safeText(deletedNode.getModuleKey()),
                    a91565ExtraText(deletedNode.getExtra(), "sourceRequirementKey"),
                    a91565FeatureKey(featureName)
            );

            List<Map<String, Object>> functionalRequirements =
                    a91565RemoveRequirement(
                            a91565MutableMapList(content.get("functional_requirements")),
                            deletedNode.getId(),
                            featureKey,
                            featureName
                    );
            content.put("functional_requirements", functionalRequirements);

            List<Map<String, Object>> structuredRequirements =
                    a91565RemoveRequirement(
                            a91565MutableMapList(content.get("structured_requirements")),
                            deletedNode.getId(),
                            featureKey,
                            featureName
                    );
            content.put("structured_requirements", structuredRequirements);

            content.put("moduleTreeFeatureDeleteSyncAt", LocalDateTime.now().toString());
            content.put("moduleTreeFeatureSyncVersion", "A9.10.14.4.3.15.4.15.6.5");

            artifactService.saveArtifact(
                    projectId,
                    ownerModuleId,
                    currentPrd.getTaskId(),
                    "MODULE_PRD",
                    firstNonBlank(safeText(currentPrd.getTitle()), "模块需求"),
                    content,
                    currentPrd.getContentMarkdown(),
                    "MODULE_TREE_PATCH_APPLY"
            );
        } catch (Exception ignored) {
            // PRD 同步失败不能打断模块树删除；影响提示仍由 ProjectModuleImpactService 兜底。
        }
    }

    private Map<String, Object> a91565MutableContent(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }

        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> a91565MutableMapList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (!(raw instanceof Iterable<?> iterable)) {
            return result;
        }

        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }

            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copied.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            result.add(copied);
        }

        return result;
    }

    private List<Map<String, Object>> a91565RemoveRequirement(
            List<Map<String, Object>> items,
            UUID treeNodeId,
            String requirementKey,
            String title
    ) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> item : items) {
            if (!a91565MatchesRequirement(item, treeNodeId, requirementKey, title)) {
                result.add(item);
            }
        }

        return result;
    }

    private boolean a91565ContainsRequirement(
            List<Map<String, Object>> items,
            UUID treeNodeId,
            String requirementKey,
            String title
    ) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        for (Map<String, Object> item : items) {
            if (a91565MatchesRequirement(item, treeNodeId, requirementKey, title)) {
                return true;
            }
        }

        return false;
    }

    private boolean a91565MatchesRequirement(
            Map<String, Object> item,
            UUID treeNodeId,
            String requirementKey,
            String title
    ) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        String nodeId = treeNodeId == null ? "" : treeNodeId.toString();
        if (hasText(nodeId) && nodeId.equals(safeText(item.get("treeNodeId")))) {
            return true;
        }

        String key = safeText(requirementKey);
        if (hasText(key)
                && (
                key.equals(safeText(item.get("requirement_key")))
                        || key.equals(safeText(item.get("requirementKey")))
                        || key.equals(safeText(item.get("feature_key")))
                        || key.equals(safeText(item.get("featureKey")))
                        || key.equals(safeText(item.get("treeNodeKey")))
        )) {
            return true;
        }

        String name = safeText(title);
        return hasText(name)
                && (
                name.equals(safeText(item.get("title")))
                        || name.equals(safeText(item.get("name")))
                        || name.equals(safeText(item.get("requirement_name")))
                        || name.equals(safeText(item.get("requirementName")))
                        || name.equals(safeText(item.get("feature_name")))
                        || name.equals(safeText(item.get("featureName")))
        );
    }

    private String a91565BuildFeatureDescription(
            ProjectModuleService.ModuleTreeNode featureNode,
            ModuleTreePatchOperationRequest operation
    ) {
        String instruction = firstNonBlank(
                a91565ExtraText(operation == null ? null : operation.getExtra(), "sourceInstruction"),
                a91565ExtraText(operation == null ? null : operation.getExtra(), "originalInstruction"),
                a91565ExtraText(featureNode == null ? null : featureNode.getExtra(), "sourceInstruction")
        );

        if (hasText(instruction)) {
            return instruction;
        }

        String featureName = featureNode == null ? "" : safeText(featureNode.getModuleName());
        if (hasText(featureName)) {
            return "支持" + featureName + "相关业务处理，并与当前模块的状态、权限和数据对象保持一致。";
        }

        return "支持新增业务能力，并与当前模块的状态、权限和数据对象保持一致。";
    }

    private String a91565ExtraText(
            Map<String, Object> extra,
            String key
    ) {
        if (extra == null || key == null) {
            return "";
        }

        Object value = extra.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String a91565FeatureKey(String value) {
        String raw = safeText(value).toLowerCase(Locale.ROOT);
        String key = raw.replaceAll("[^0-9a-zA-Z\\u4e00-\\u9fff_\\-]+", "_");
        key = key.replaceAll("_+", "_").replaceAll("^_+|_+$", "");

        if (key.isBlank()) {
            return "feature_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        return key;
    }


    private String firstOperationExtraText(
            ApplyModuleTreePatchRequest request,
            String key
    ) {
        if (request == null || request.getOperations() == null || key == null) {
            return "";
        }

        for (ModuleTreePatchOperationRequest operation : request.getOperations()) {
            if (operation == null || operation.getExtra() == null) {
                continue;
            }

            Object value = operation.getExtra().get(key);

            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }

        return "";
    }

    private Integer firstOperationExtraInteger(
            ApplyModuleTreePatchRequest request,
            String key
    ) {
        String value = firstOperationExtraText(
                request,
                key
        );

        if (value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }


    // A9.10.14.4.3.15.4.15.6.5_HOTFIX_COMPILE_OBJECT_TEXT
    private String safeText(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value).trim();
    }

    private String firstNonBlank(
            String... values
    ) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }


    private List<ModuleTreePatchOperationRequest> normalizeOperationsBeforeApply(
            ApplyModuleTreePatchRequest request,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        List<ModuleTreePatchOperationRequest> rawOperations = request.getOperations() == null
                ? List.of()
                : request.getOperations()
                .stream()
                .filter(Objects::nonNull)
                .toList();

        if (rawOperations.isEmpty()) {
            return List.of();
        }

        /*
         * Phase 6-C mini hard gate:
         * A conversational instruction like “给用户中心增加安全设置页” should apply as
         * one PAGE node. Some LLM proposals over-expand it into SUBMODULE + PAGE +
         * FEATURE children. The apply endpoint must not trust that cached proposal
         * blindly, because old assistant cards and history can still post stale ops.
         */
        if (shouldCollapseGeneratedPageBundle(rawOperations)) {
            ModuleTreePatchOperationRequest pageOperation = buildCanonicalPageOperation(
                    request,
                    rawOperations,
                    moduleTree
            );

            if (pageOperation == null) {
                return List.of();
            }

            return List.of(pageOperation);
        }

        List<ModuleTreePatchOperationRequest> result = new ArrayList<>();

        for (ModuleTreePatchOperationRequest operation : rawOperations) {
            String op = normalizeOperationType(operation.getOp());

            if (op.isBlank()) {
                continue;
            }

            ModuleTreePatchOperationRequest copy = copyOperation(operation);
            copy.setOp(op);

            if ("ADD_NODE".equals(op)) {
                normalizeAddNodeForApply(
                        copy,
                        request,
                        moduleTree
                );
            }

            result.add(copy);
        }

        return result;
    }

    private boolean shouldCollapseGeneratedPageBundle(
            List<ModuleTreePatchOperationRequest> operations
    ) {
        int addNodeCount = 0;
        int pageCount = 0;
        int submoduleCount = 0;
        int featureCount = 0;

        for (ModuleTreePatchOperationRequest operation : operations) {
            if (!"ADD_NODE".equals(normalizeOperationType(operation.getOp()))) {
                return false;
            }

            addNodeCount++;

            String moduleType = normalizeModuleType(
                    operation.getModuleType(),
                    operation.getModuleName()
            );

            if ("PAGE".equals(moduleType)) {
                pageCount++;
            } else if ("SUBMODULE".equals(moduleType)) {
                submoduleCount++;
            } else if ("FEATURE".equals(moduleType)) {
                featureCount++;
            }
        }

        return addNodeCount > 1
                && pageCount == 1
                && (submoduleCount > 0 || featureCount > 0);
    }

    private ModuleTreePatchOperationRequest buildCanonicalPageOperation(
            ApplyModuleTreePatchRequest request,
            List<ModuleTreePatchOperationRequest> operations,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        ModuleTreePatchOperationRequest pageSource = operations.stream()
                .filter(operation -> "ADD_NODE".equals(normalizeOperationType(operation.getOp())))
                .filter(operation -> "PAGE".equals(normalizeModuleType(
                        operation.getModuleType(),
                        operation.getModuleName()
                )))
                .findFirst()
                .orElse(null);

        if (pageSource == null || !hasText(pageSource.getModuleName())) {
            return null;
        }

        ModuleTreePatchOperationRequest canonical = copyOperation(pageSource);
        canonical.setOp("ADD_NODE");
        canonical.setModuleType("PAGE");
        canonical.setModuleName(ensureSuffix(
                pageSource.getModuleName(),
                "PAGE"
        ));

        if (!hasText(canonical.getModuleKey())) {
            canonical.setModuleKey(generateModuleKey(
                    canonical.getModuleName(),
                    "PAGE"
            ));
        }

        UUID parentId = resolveParentIdForAdd(
                canonical.getParentId(),
                "PAGE",
                canonical.getModuleName(),
                request,
                operations,
                moduleTree
        );

        if (parentId == null) {
            throw new IllegalArgumentException("无法定位可创建页面节点的父节点。请确认用户中心下是否存在模块或子模块，例如“登录注册”。");
        }

        canonical.setParentId(parentId);
        canonical.setExtra(mergeExtra(
                canonical.getExtra(),
                Map.of(
                        "source", "ApplyServiceCanonicalPageBundleNormalizer",
                        "normalizationReason", "应用阶段发现提案包含 SUBMODULE/PAGE/FEATURE 组合，Phase 6-C mini 已收敛为单个 PAGE 节点，避免非法层级和临时节点引用。"
                )
        ));

        return canonical;
    }


    private void a91561HydrateAddNodeTypeFromExtra(
            ModuleTreePatchOperationRequest operation
    ) {
        if (operation == null) {
            return;
        }

        String explicitType = safeText(operation.getModuleType());
        String extraType = firstNonBlank(
                a91561ExtraText(operation, "moduleType"),
                a91561ExtraText(operation, "nodeType"),
                a91561ExtraText(operation, "type"),
                a91561ExtraText(operation, "targetType"),
                a91561ExtraText(operation, "requirementNodeType"),
                a91561ExtraText(operation, "treeNodeType")
        );

        String normalized = normalizeModuleType(
                firstNonBlank(explicitType, extraType),
                operation.getModuleName()
        );

        if (hasText(normalized)) {
            operation.setModuleType(normalized);
        }
    }

    private String a91561ExtraText(
            ModuleTreePatchOperationRequest operation,
            String key
    ) {
        if (operation == null || operation.getExtra() == null || key == null) {
            return "";
        }

        Object value = operation.getExtra().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void normalizeAddNodeForApply(
            ModuleTreePatchOperationRequest operation,
            ApplyModuleTreePatchRequest request,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        // A9.10.14.4.3.15.4.15.6.1_PATCH_APPLY_FINAL_MODULE_FEATURE_PAGE
        a91561HydrateAddNodeTypeFromExtra(operation);

        String moduleType = normalizeModuleType(
                operation.getModuleType(),
                operation.getModuleName()
        );

        if (!moduleType.isBlank()) {
            operation.setModuleType(moduleType);
        }

        if (hasText(operation.getModuleName())) {
            operation.setModuleName(ensureSuffix(
                    operation.getModuleName(),
                    moduleType
            ));
        }

        if (!hasText(operation.getModuleKey()) && hasText(operation.getModuleName())) {
            operation.setModuleKey(generateModuleKey(
                    operation.getModuleName(),
                    moduleType
            ));
        }

        if (!hasText(operation.getModuleName()) || !hasText(moduleType)) {
            return;
        }

        UUID parentId = resolveParentIdForAdd(
                operation.getParentId(),
                moduleType,
                operation.getModuleName(),
                request,
                request.getOperations() == null ? List.of() : request.getOperations(),
                moduleTree
        );

        if (parentId != null) {
            operation.setParentId(parentId);
            return;
        }

        if (isAllowedRootChildType(moduleType)) {
            operation.setParentId(null);
            return;
        }

        throw new IllegalArgumentException(
                "无法定位可创建“" + operation.getModuleName() + "”的合法父节点。"
                        + " " + moduleType + " 不能直接创建在根节点或不兼容父节点下。"
        );
    }

    private UUID resolveParentIdForAdd(
            UUID originalParentId,
            String childModuleType,
            String childModuleName,
            ApplyModuleTreePatchRequest request,
            List<ModuleTreePatchOperationRequest> operations,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        List<ProjectModuleService.ModuleTreeNode> flatNodes = flatten(moduleTree);

        ProjectModuleService.ModuleTreeNode originalParent = findNodeById(
                flatNodes,
                originalParentId
        );

        ProjectModuleService.ModuleTreeNode resolved = resolveCompatibleParentAroundAnchor(
                originalParent,
                childModuleType,
                childModuleName,
                flatNodes
        );

        if (resolved != null && resolved.getId() != null) {
            return resolved.getId();
        }

        for (ModuleTreePatchOperationRequest operation : operations) {
            ProjectModuleService.ModuleTreeNode operationParent = findNodeById(
                    flatNodes,
                    operation.getParentId()
            );

            resolved = resolveCompatibleParentAroundAnchor(
                    operationParent,
                    childModuleType,
                    childModuleName,
                    flatNodes
            );

            if (resolved != null && resolved.getId() != null) {
                return resolved.getId();
            }
        }

        String contextText = normalizeForMatch(
                safeText(request.getDescription()) + " " + safeText(request.getSource()) + " " + safeText(childModuleName)
        );

        resolved = findSemanticCompatibleParent(
                contextText,
                childModuleType,
                childModuleName,
                flatNodes
        );

        if (resolved != null && resolved.getId() != null) {
            return resolved.getId();
        }

        resolved = flatNodes.stream()
                .filter(node -> isAllowedChildType(node, childModuleType))
                .findFirst()
                .orElse(null);

        return resolved == null ? null : resolved.getId();
    }

    private ProjectModuleService.ModuleTreeNode resolveCompatibleParentAroundAnchor(
            ProjectModuleService.ModuleTreeNode anchor,
            String childModuleType,
            String childModuleName,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        if (anchor == null) {
            return null;
        }

        if (isAllowedChildType(anchor, childModuleType)) {
            return anchor;
        }

        ProjectModuleService.ModuleTreeNode ancestor = findCompatibleAncestor(
                anchor,
                childModuleType,
                flatNodes
        );

        if (ancestor != null) {
            return ancestor;
        }

        return findCompatibleDescendant(
                anchor,
                childModuleType,
                childModuleName,
                flatNodes
        );
    }

    private ProjectModuleService.ModuleTreeNode findCompatibleAncestor(
            ProjectModuleService.ModuleTreeNode node,
            String childModuleType,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        UUID parentId = node == null ? null : node.getParentId();

        while (parentId != null) {
            ProjectModuleService.ModuleTreeNode parent = findNodeById(
                    flatNodes,
                    parentId
            );

            if (parent == null) {
                return null;
            }

            if (isAllowedChildType(parent, childModuleType)) {
                return parent;
            }

            parentId = parent.getParentId();
        }

        return null;
    }

    private ProjectModuleService.ModuleTreeNode findCompatibleDescendant(
            ProjectModuleService.ModuleTreeNode anchor,
            String childModuleType,
            String childModuleName,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        if (anchor == null || anchor.getId() == null) {
            return null;
        }

        List<ProjectModuleService.ModuleTreeNode> candidates = flatNodes.stream()
                .filter(node -> node != null && node.getId() != null)
                .filter(node -> isDescendantOf(node, anchor.getId(), flatNodes))
                .filter(node -> isAllowedChildType(node, childModuleType))
                .sorted(Comparator.comparingInt(node -> -semanticScore(
                        node,
                        childModuleName
                )))
                .toList();

        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private ProjectModuleService.ModuleTreeNode findSemanticCompatibleParent(
            String normalizedContextText,
            String childModuleType,
            String childModuleName,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        return flatNodes.stream()
                .filter(node -> isAllowedChildType(node, childModuleType))
                .filter(node -> semanticScore(node, normalizedContextText + childModuleName) > 0)
                .max(Comparator.comparingInt(node -> semanticScore(
                        node,
                        normalizedContextText + childModuleName
                )))
                .orElse(null);
    }

    private int semanticScore(
            ProjectModuleService.ModuleTreeNode node,
            String text
    ) {
        String normalizedText = normalizeForMatch(text);
        String name = normalizeForMatch(node.getModuleName());
        String key = normalizeForMatch(node.getModuleKey());
        String path = normalizeForMatch(node.getModulePath());

        int score = 0;

        for (String value : List.of(name, key, path)) {
            if (value.isBlank()) {
                continue;
            }

            if (normalizedText.contains(value)) {
                score = Math.max(score, 100 + value.length());
            }
        }

        if (normalizedText.contains("安全") && name.contains("安全")) {
            score = Math.max(score, 80);
        }

        if (normalizedText.contains("密码") && name.contains("登录")) {
            score = Math.max(score, 70);
        }

        if (normalizedText.contains("用户") && (name.contains("用户") || path.contains("用户"))) {
            score = Math.max(score, 60);
        }

        if (name.contains("登录注册") || name.contains("账号")) {
            score = Math.max(score, 40);
        }

        return score;
    }

    private boolean isDescendantOf(
            ProjectModuleService.ModuleTreeNode node,
            UUID ancestorId,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        UUID currentParentId = node.getParentId();

        while (currentParentId != null) {
            if (currentParentId.equals(ancestorId)) {
                return true;
            }

            ProjectModuleService.ModuleTreeNode parent = findNodeById(
                    flatNodes,
                    currentParentId
            );

            if (parent == null) {
                return false;
            }

            currentParentId = parent.getParentId();
        }

        return false;
    }

    private boolean isAllowedChildType(
            ProjectModuleService.ModuleTreeNode parent,
            String childType
    ) {
        if (parent == null || childType == null || childType.isBlank()) {
            return false;
        }

        String parentType = normalizeModuleType(
                parent.getModuleType(),
                parent.getModuleName()
        );
        String normalizedChildType = normalizeModuleType(
                childType,
                null
        );

        return switch (parentType) {
            case "DOMAIN" -> "MODULE".equals(normalizedChildType);
            case "MODULE" -> Set.of("SUBMODULE", "PAGE").contains(normalizedChildType);
            case "SUBMODULE" -> "PAGE".equals(normalizedChildType);
            case "PAGE" -> "FEATURE".equals(normalizedChildType);
            default -> false;
        };
    }

    private boolean isAllowedRootChildType(
            String childType
    ) {
        return Set.of("DOMAIN", "MODULE").contains(normalizeModuleType(
                childType,
                null
        ));
    }

    private List<ProjectModuleService.ModuleTreeNode> flatten(
            List<ProjectModuleService.ModuleTreeNode> tree
    ) {
        List<ProjectModuleService.ModuleTreeNode> result = new ArrayList<>();
        flattenInto(
                tree,
                result
        );
        return result;
    }

    private void flattenInto(
            List<ProjectModuleService.ModuleTreeNode> nodes,
            List<ProjectModuleService.ModuleTreeNode> result
    ) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        for (ProjectModuleService.ModuleTreeNode node : nodes) {
            if (node == null) {
                continue;
            }

            result.add(node);
            flattenInto(
                    node.getChildren(),
                    result
            );
        }
    }

    private ProjectModuleService.ModuleTreeNode findNodeById(
            List<ProjectModuleService.ModuleTreeNode> nodes,
            UUID nodeId
    ) {
        if (nodeId == null || nodes == null || nodes.isEmpty()) {
            return null;
        }

        return nodes.stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElse(null);
    }

    private ModuleTreePatchOperationRequest copyOperation(
            ModuleTreePatchOperationRequest source
    ) {
        ModuleTreePatchOperationRequest copy = new ModuleTreePatchOperationRequest();

        if (source == null) {
            return copy;
        }

        copy.setOp(normalizeOperationType(source.getOp()));
        copy.setNodeId(source.getNodeId());
        copy.setParentId(source.getParentId());
        copy.setNewParentId(source.getNewParentId());
        copy.setModuleName(source.getModuleName());
        copy.setModuleKey(source.getModuleKey());
        copy.setModuleType(source.getModuleType());
        copy.setSortOrder(source.getSortOrder());
        copy.setExtra(source.getExtra() == null ? null : new LinkedHashMap<>(source.getExtra()));
        return copy;
    }

    private Map<String, Object> mergeExtra(
            Map<String, Object> original,
            Map<String, Object> patch
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (original != null) {
            result.putAll(original);
        }

        if (patch != null) {
            result.putAll(patch);
        }

        return result;
    }

    private String normalizeOperationType(
            String value
    ) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    // A9.10.14.4.3.15.4.15.6.1_HOTFIX_CHINESE_NODE_TYPE_ALIAS_APPLY
    private String normalizeModuleType(
            String rawType,
            String moduleName
    ) {
        String raw = rawType == null ? "" : rawType.trim();
        String compact = raw.replaceAll("\\s+", "");
        String value = compact.toUpperCase(Locale.ROOT);

        if (List.of(
                "FEATURE",
                "FUNCTION",
                "FUNCTION_POINT",
                "FEATURE_POINT",
                "BUSINESS_FEATURE",
                "BUSINESS_FUNCTION",
                "BUSINESS_CAPABILITY",
                "BUSINESS_ABILITY",
                "CAPABILITY",
                "ABILITY",
                "FUNCTIONAL_REQUIREMENT",
                "ACTION",
                "OPERATION"
        ).contains(value)
                || List.of(
                "功能",
                "功能点",
                "业务功能",
                "业务能力",
                "能力",
                "操作",
                "动作",
                "功能需求",
                "业务需求"
        ).contains(compact)) {
            return "FEATURE";
        }

        if (List.of(
                "PAGE",
                "PAGE_MODULE",
                "PAGE_REQUIREMENT",
                "SCREEN",
                "VIEW",
                "DIALOG",
                "MODAL",
                "POPUP",
                "DRAWER",
                "WINDOW",
                "FORM_PAGE",
                "LIST_PAGE",
                "DETAIL_PAGE",
                "DASHBOARD_PAGE",
                "SETTINGS_PAGE"
        ).contains(value)
                || List.of(
                "页面",
                "页",
                "页面需求",
                "弹窗",
                "弹框",
                "抽屉",
                "窗口",
                "表单页",
                "列表页",
                "详情页",
                "看板页",
                "设置页"
        ).contains(compact)) {
            return "PAGE";
        }

        if (List.of(
                "DOMAIN",
                "DOMAIN_MODULE",
                "BUSINESS_DOMAIN",
                "MODULE",
                "BUSINESS_MODULE",
                "FEATURE_MODULE",
                "INTEGRATION_MODULE",
                "INFRA_MODULE",
                "SUBMODULE",
                "SUB_MODULE",
                "SCENE",
                "BUSINESS_SCENE"
        ).contains(value)
                || List.of(
                "模块",
                "子模块",
                "业务模块",
                "领域",
                "业务域",
                "场景"
        ).contains(compact)) {
            return "MODULE";
        }

        String name = moduleName == null ? "" : moduleName.trim();

        if (name.endsWith("页")
                || name.endsWith("页面")
                || name.endsWith("弹窗")
                || name.endsWith("弹框")
                || name.endsWith("抽屉")
                || name.contains("页面")
                || name.contains("列表页")
                || name.contains("详情页")) {
            return "PAGE";
        }

        if (name.endsWith("功能")
                || name.endsWith("功能点")
                || name.contains("功能点")
                || name.contains("业务能力")) {
            return "FEATURE";
        }

        return raw.isBlank() ? "MODULE" : "MODULE";
    }

    private String ensureSuffix(
            String rawName,
            String moduleType
    ) {
        String name = safeText(rawName).replaceAll("\\s+", "");
        String type = normalizeModuleType(
                moduleType,
                name
        );

        if (name.isBlank()) {
            return name;
        }

        if ("PAGE".equals(type) && !name.endsWith("页") && !name.endsWith("页面")) {
            return name + "页";
        }

        if ("FEATURE".equals(type) && !name.endsWith("功能") && !name.endsWith("功能点")) {
            return name + "功能";
        }

        if ("SUBMODULE".equals(type) && !name.endsWith("子模块")) {
            return name + "子模块";
        }

        if ("MODULE".equals(type) && !name.endsWith("模块")) {
            return name + "模块";
        }

        return name;
    }

    private String generateModuleKey(
            String moduleName,
            String moduleType
    ) {
        String text = safeText(moduleName)
                .toLowerCase(Locale.ROOT)
                .replace("用户中心", "user_center")
                .replace("用户", "user")
                .replace("安全设置", "security_settings")
                .replace("安全", "security")
                .replace("密码找回", "password_recovery")
                .replace("找回密码", "password_recovery")
                .replace("登录注册", "auth")
                .replace("登录", "login")
                .replace("注册", "register")
                .replace("验证码", "verification_code")
                .replace("页面", "page")
                .replace("页", "page")
                .replace("功能点", "feature")
                .replace("功能", "feature")
                .replace("子模块", "submodule")
                .replace("模块", "module");

        text = text.replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (text.isBlank()) {
            text = "node_" + Math.abs(safeText(moduleName).hashCode());
        }

        String suffix = switch (normalizeModuleType(
                moduleType,
                moduleName
        )) {
            case "PAGE" -> "page";
            case "FEATURE" -> "feature";
            case "MODULE" -> "module";
            case "SUBMODULE" -> "submodule";
            case "DOMAIN" -> "domain";
            default -> "";
        };

        if (!suffix.isBlank() && !text.endsWith("_" + suffix) && !text.equals(suffix)) {
            text = text + "_" + suffix;
        }

        return text.length() > 80 ? text.substring(0, 80) : text;
    }

    private String normalizeForMatch(
            String text
    ) {
        return safeText(text)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean hasText(
            String value
    ) {
        return value != null && !value.isBlank();
    }

    private String safeText(
            String value
    ) {
        return value == null ? "" : value.trim();
    }

    private String safeUuid(
            UUID value
    ) {
        return value == null ? "-" : value.toString();
    }
}