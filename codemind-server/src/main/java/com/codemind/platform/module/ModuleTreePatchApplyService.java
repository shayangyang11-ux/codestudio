package com.codemind.platform.module;

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
            throw new IllegalArgumentException("模块树 Patch 操作不能为空。");
        }

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

        for (ModuleTreePatchOperationRequest operation : operations) {
            String op = normalizeOperationType(operation.getOp());

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
                    latestResponse = applyDeleteNode(
                            workspaceId,
                            projectCode,
                            operation
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

    private void normalizeAddNodeForApply(
            ModuleTreePatchOperationRequest operation,
            ApplyModuleTreePatchRequest request,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
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

    private String normalizeModuleType(
            String value,
            String moduleName
    ) {
        String raw = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);

        String normalized = switch (raw) {
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> "DOMAIN";
            case "MODULE", "BUSINESS_MODULE", "FEATURE_MODULE", "INTEGRATION_MODULE", "INFRA_MODULE" -> "MODULE";
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> "SUBMODULE";
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "FUNCTION_POINT", "ACTION", "OPERATION" -> "FEATURE";
            default -> "";
        };

        if (!normalized.isBlank()) {
            return normalized;
        }

        String name = safeText(moduleName);
        if (name.endsWith("页") || name.endsWith("页面")) {
            return "PAGE";
        }
        if (name.endsWith("功能") || name.endsWith("功能点")) {
            return "FEATURE";
        }
        if (name.endsWith("子模块")) {
            return "SUBMODULE";
        }
        if (name.endsWith("模块")) {
            return "MODULE";
        }

        return "";
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