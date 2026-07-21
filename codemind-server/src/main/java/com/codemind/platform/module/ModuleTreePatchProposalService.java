package com.codemind.platform.module;

import com.codemind.platform.agent.common.client.PythonAgentClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ModuleTreePatchProposalService {

    private final ProjectModuleService projectModuleService;
    private final PythonAgentClient pythonAgentClient;

    public ModuleTreePatchProposalService(
            ProjectModuleService projectModuleService,
            PythonAgentClient pythonAgentClient
    ) {
        this.projectModuleService = projectModuleService;
        this.pythonAgentClient = pythonAgentClient;
    }

    public ModuleTreePatchProposalResponse propose(
            UUID workspaceId,
            String projectCode,
            ProposeModuleTreePatchRequest request
    ) {
        String instruction = normalizeText(
                request == null ? null : request.getInstruction()
        );

        UUID selectedNodeId = request == null ? null : request.getSelectedNodeId();

        if (instruction.isBlank()) {
            throw new IllegalArgumentException("模块树变更说明不能为空。");
        }

        List<ProjectModuleService.ModuleTreeNode> moduleTree =
                projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                        workspaceId,
                        projectCode
                );

        try {
            Object rawResponse = pythonAgentClient.proposeModuleTreePatch(
                    UUID.randomUUID(),
                    projectCode,
                    selectedNodeId,
                    instruction,
                    moduleTree
            );

            ModuleTreePatchProposalResponse normalized =
                    normalizeAgentProposalResponse(
                            rawResponse,
                            instruction,
                            selectedNodeId,
                            moduleTree
                    );

            if (hasExecutableOperations(normalized)) {
                return normalized;
            }

            return appendNoOperationGuidance(normalized);
        } catch (Exception ex) {
            return buildRuleFallbackProposal(
                    instruction,
                    selectedNodeId,
                    moduleTree,
                    "Python ModuleTreePatchAgent 调用失败：" + ex.getMessage()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private ModuleTreePatchProposalResponse normalizeAgentProposalResponse(
            Object rawResponse,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        Map<String, Object> rawMap = toMap(rawResponse);
        Map<String, Object> content = toMap(rawMap.get("content"));

        if (content.isEmpty()) {
            content = rawMap;
        }

        Map<String, Object> proposal = toMap(content.get("proposal"));

        if (proposal.isEmpty()) {
            proposal = new LinkedHashMap<>();
            proposal.put(
                    "description",
                    firstNonBlank(
                            textValue(content.get("description")),
                            "根据用户说明生成模块树变更提案"
                    )
            );
            proposal.put(
                    "source",
                    firstNonBlank(
                            textValue(content.get("source")),
                            "LANGGRAPH_MODULE_TREE_PATCH_AGENT"
                    )
            );
            proposal.put(
                    "operations",
                    normalizeOperations(
                            content.get("operations"),
                            instruction,
                            selectedNodeId,
                            moduleTree
                    )
            );
            proposal.put("impact", normalizeImpact(content.get("impact")));
        } else {
            proposal = new LinkedHashMap<>(proposal);
            proposal.put(
                    "operations",
                    normalizeOperations(
                            proposal.get("operations"),
                            instruction,
                            selectedNodeId,
                            moduleTree
                    )
            );
            proposal.put(
                    "impact",
                    normalizeImpact(proposal.get("impact"))
            );
        }

        String description = firstNonBlank(
                textValue(content.get("description")),
                textValue(proposal.get("description")),
                "根据用户说明生成模块树变更提案"
        );

        String source = firstNonBlank(
                textValue(content.get("source")),
                textValue(proposal.get("source")),
                "LANGGRAPH_MODULE_TREE_PATCH_AGENT"
        );

        proposal.put("description", description);
        proposal.put("source", source);
        proposal.putIfAbsent("requiresUserConfirmation", true);
        proposal.putIfAbsent("agentMode", "LANGGRAPH_PROPOSAL_ONLY");

        Map<String, Object> impact = normalizeImpact(proposal.get("impact"));
        proposal.put("impact", impact);

        List<String> validationNotes = asStringList(content.get("validationNotes"));
        validationNotes.addAll(asStringList(proposal.get("validationNotes")));
        validationNotes = dedupe(validationNotes);

        if (!validationNotes.isEmpty()) {
            proposal.put("validationNotes", validationNotes);
        }

        Object confidence = firstPresent(
                content.get("confidence"),
                proposal.get("confidence")
        );
        if (confidence != null) {
            proposal.put("confidence", confidence);
        }

        String markdown = firstNonBlank(
                textValue(rawMap.get("content_markdown")),
                textValue(content.get("markdown")),
                buildMarkdownSummary(
                        description,
                        operationsOf(proposal),
                        impact,
                        validationNotes
                )
        );

        return new ModuleTreePatchProposalResponse(
                description,
                source,
                proposal,
                markdown,
                true
        );
    }

    private ModuleTreePatchProposalResponse appendNoOperationGuidance(
            ModuleTreePatchProposalResponse response
    ) {
        Map<String, Object> proposal = new LinkedHashMap<>(response.proposal());
        Map<String, Object> impact = normalizeImpact(proposal.get("impact"));

        List<String> riskNotes = asStringList(impact.get("riskNotes"));
        riskNotes.add("当前提案没有可执行模块树操作，不会修改模块树；请补充说明要新增、移动、重命名还是删除哪个节点。");
        impact.put("riskNotes", dedupe(riskNotes));

        proposal.put("impact", impact);

        return new ModuleTreePatchProposalResponse(
                response.description(),
                response.source(),
                proposal,
                response.markdown(),
                response.requiresUserConfirmation()
        );
    }

    private boolean hasExecutableOperations(
            ModuleTreePatchProposalResponse response
    ) {
        return !operationsOf(response.proposal()).isEmpty();
    }

    private ModuleTreePatchProposalResponse buildRuleFallbackProposal(
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            String reason
    ) {
        Map<String, Object> operation = buildAddNodeFallbackOperation(
                instruction,
                selectedNodeId,
                moduleTree
        );

        List<Map<String, Object>> operations = new ArrayList<>();
        List<String> validationNotes = new ArrayList<>();

        if (operation.isEmpty()) {
            validationNotes.add("Java 兜底未识别到稳定的模块树操作。");
        } else {
            operations.add(operation);
            validationNotes.add("Java 兜底识别为新增节点提案，请确认父节点、节点类型和 moduleKey。");
        }

        List<String> riskNotes = new ArrayList<>();
        riskNotes.add(reason);
        riskNotes.add("这是兜底提案，不会自动应用，必须人工确认后才会修改模块树。");
        if (operation.isEmpty()) {
            riskNotes.add("建议继续补充说明，例如：给用户中心/登录注册子模块增加密码找回页。");
        }

        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("summary", operations.isEmpty()
                ? "没有识别到可执行模块树操作。"
                : "新增或调整模块树节点后，相关需求资产需要检查。"
        );
        impact.put("affectedNodeIds", selectedNodeId == null
                ? List.of()
                : List.of(selectedNodeId.toString())
        );
        impact.put("affectedArtifacts", inferAffectedArtifacts(operations));
        impact.put("riskNotes", riskNotes);

        String description = operations.isEmpty()
                ? "未生成可执行模块树变更提案"
                : "新增模块树节点：" + operation.getOrDefault("moduleName", "新节点");

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("description", description);
        proposal.put("source", "JAVA_RULE_FALLBACK_AFTER_MODULE_TREE_PATCH_AGENT_FAILURE");
        proposal.put("operations", operations);
        proposal.put("impact", impact);
        proposal.put("validationNotes", validationNotes);
        proposal.put("confidence", operations.isEmpty() ? 0.15 : 0.35);
        proposal.put("agentMode", "JAVA_RULE_FALLBACK_PROPOSAL_ONLY");

        String markdown = buildMarkdownSummary(
                description,
                operations,
                impact,
                validationNotes
        );

        return new ModuleTreePatchProposalResponse(
                description,
                "JAVA_RULE_FALLBACK_AFTER_MODULE_TREE_PATCH_AGENT_FAILURE",
                proposal,
                markdown,
                true
        );
    }

    private Map<String, Object> buildAddNodeFallbackOperation(
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        String text = normalizeText(instruction);

        if (!containsAny(text, List.of("新增", "增加", "添加", "创建", "新建", "加一个", "加个", "补一个", "补充"))) {
            return Map.of();
        }

        String moduleType = inferModuleType(text);
        String moduleName = inferModuleName(text, moduleType);

        if (moduleName.isBlank()) {
            return Map.of();
        }

        ProjectModuleService.ModuleTreeNode parentNode =
                resolveCompatibleParentForAdd(
                        text,
                        selectedNodeId,
                        moduleTree,
                        moduleName,
                        moduleType
                );

        UUID parentId = parentNode == null ? null : parentNode.getId();

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", "ADD_NODE");
        operation.put("parentId", parentId == null ? null : parentId.toString());
        operation.put("moduleName", moduleName);
        operation.put("moduleKey", generateModuleKey(moduleName, moduleType));
        operation.put("moduleType", moduleType);
        operation.put("extra", Map.of(
                "source", "JavaModuleTreePatchFallback",
                "originalInstruction", instruction
        ));
        return operation;
    }

    private ProjectModuleService.ModuleTreeNode findMentionedNode(
            String instruction,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            String targetModuleName
    ) {
        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flatten(moduleTree, flatNodes);

        String normalizedInstruction = normalizeForMatch(instruction);
        String normalizedTarget = normalizeForMatch(targetModuleName);

        ProjectModuleService.ModuleTreeNode best = null;
        int bestScore = 0;

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            String name = normalizeForMatch(node.getModuleName());
            String key = normalizeForMatch(node.getModuleKey());

            if (name.isBlank() || name.equals(normalizedTarget)) {
                continue;
            }

            int score = 0;

            if (normalizedInstruction.contains(name)) {
                score = Math.max(score, name.length());
            }

            if (!key.isBlank() && normalizedInstruction.contains(key)) {
                score = Math.max(score, key.length());
            }

            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }

        return best;
    }

    private void flatten(
            List<ProjectModuleService.ModuleTreeNode> nodes,
            List<ProjectModuleService.ModuleTreeNode> result
    ) {
        if (nodes == null) {
            return;
        }

        for (ProjectModuleService.ModuleTreeNode node : nodes) {
            if (node == null) {
                continue;
            }

            result.add(node);
            flatten(node.getChildren(), result);
        }
    }

    private String inferModuleType(
            String instruction
    ) {
        if (containsAny(instruction, List.of("功能点"))) {
            return "FEATURE";
        }

        if (containsAny(instruction, List.of("功能"))) {
            return "FEATURE";
        }

        if (containsAny(instruction, List.of("页面", "页", "路由"))) {
            return "PAGE";
        }

        if (containsAny(instruction, List.of("子模块"))) {
            return "SUBMODULE";
        }

        if (containsAny(instruction, List.of("模块"))) {
            return "MODULE";
        }

        return "PAGE";
    }

    private String inferModuleName(
            String instruction,
            String moduleType
    ) {
        String kindRegex = switch (moduleType) {
            case "FEATURE" -> "(功能点|功能)";
            case "SUBMODULE" -> "(子模块)";
            case "MODULE" -> "(模块)";
            default -> "(页面|页|路由)";
        };

        String regex = "(新增|增加|添加|创建|新建|加一个|加个|补一个|补充)(一个|个)?(.{2,40}?)" + kindRegex;
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(regex).matcher(instruction);

        if (matcher.find()) {
            String rawName = normalizeText(matcher.group(3));
            return appendNameSuffix(rawName, moduleType);
        }

        String giveRegex = "给.{1,40}?(新增|增加|添加|创建|新建|加一个|加个)(一个|个)?(.{2,40}?)" + kindRegex;
        java.util.regex.Matcher giveMatcher =
                java.util.regex.Pattern.compile(giveRegex).matcher(instruction);

        if (giveMatcher.find()) {
            String rawName = normalizeText(giveMatcher.group(3));
            return appendNameSuffix(rawName, moduleType);
        }

        return "";
    }

    private String appendNameSuffix(
            String rawName,
            String moduleType
    ) {
        String name = rawName
                .replaceAll("[，。,；;].*$", "")
                .replaceAll("\\s+", "")
                .trim();

        if (name.isBlank()) {
            return "";
        }

        if ("PAGE".equals(moduleType) && !name.endsWith("页") && !name.endsWith("页面")) {
            return name + "页";
        }

        if ("FEATURE".equals(moduleType) && !name.endsWith("功能") && !name.endsWith("功能点")) {
            return name + "功能";
        }

        if ("SUBMODULE".equals(moduleType) && !name.endsWith("子模块")) {
            return name + "子模块";
        }

        if ("MODULE".equals(moduleType) && !name.endsWith("模块")) {
            return name + "模块";
        }

        return name;
    }

    private List<Map<String, Object>> normalizeOperations(
            Object value
    ) {
        return normalizeOperations(
                value,
                "",
                null,
                List.of()
        );
    }

    private List<Map<String, Object>> normalizeOperations(
            Object value,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (!(value instanceof List<?> source)) {
            return result;
        }

        for (Object item : source) {
            Map<String, Object> raw = toMap(item);

            if (raw.isEmpty()) {
                continue;
            }

            String op = textValue(raw.get("op")).toUpperCase();

            if (!List.of("ADD_NODE", "UPDATE_NODE", "MOVE_NODE", "DELETE_NODE").contains(op)) {
                continue;
            }

            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("op", op);

            copyIfPresent(raw, operation, "nodeId");
            copyIfPresent(raw, operation, "parentId");
            copyIfPresent(raw, operation, "newParentId");
            copyIfPresent(raw, operation, "moduleName");
            copyIfPresent(raw, operation, "moduleKey");
            copyIfPresent(raw, operation, "moduleType");
            copyIfPresent(raw, operation, "sortOrder");

            Object extra = raw.get("extra");
            if (extra instanceof Map<?, ?>) {
                operation.put("extra", toMap(extra));
            }

            if ("ADD_NODE".equals(op)) {
                normalizeAddNodeParent(
                        operation,
                        instruction,
                        selectedNodeId,
                        moduleTree
                );
            }

            result.add(operation);
        }

        return result;
    }

    private void normalizeAddNodeParent(
            Map<String, Object> operation,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        String moduleName = textValue(operation.get("moduleName"));
        String moduleType = normalizeModuleTypeAlias(
                textValue(operation.get("moduleType")),
                moduleName
        );

        if (!moduleType.isBlank()) {
            operation.put("moduleType", moduleType);
        }

        if (moduleName.isBlank() || moduleType.isBlank()) {
            return;
        }

        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flatten(moduleTree, flatNodes);

        String originalParentId = textValue(operation.get("parentId"));
        ProjectModuleService.ModuleTreeNode originalParent =
                findNodeById(
                        flatNodes,
                        originalParentId
                );

        ProjectModuleService.ModuleTreeNode resolvedParent = null;

        if (originalParent != null) {
            resolvedParent = resolveCompatibleAncestorOrSelf(
                    originalParent,
                    flatNodes,
                    moduleType
            );
        }

        if (resolvedParent == null) {
            resolvedParent = resolveCompatibleParentForAdd(
                    instruction,
                    selectedNodeId,
                    moduleTree,
                    moduleName,
                    moduleType
            );
        }

        if (resolvedParent != null && resolvedParent.getId() != null) {
            operation.put("parentId", resolvedParent.getId().toString());

            if (!originalParentId.isBlank()
                    && !Objects.equals(originalParentId, resolvedParent.getId().toString())) {
                Map<String, Object> extra = toMap(operation.get("extra"));
                extra.put("javaResolvedParentId", resolvedParent.getId().toString());
                extra.put("javaResolvedParentName", resolvedParent.getModuleName());
                extra.put("javaResolvedParentType", resolvedParent.getModuleType());
                extra.put("originalParentId", originalParentId);
                operation.put("extra", extra);
            }

            return;
        }

        if (isAllowedRootChildType(moduleType)) {
            operation.remove("parentId");
        }
    }

    private ProjectModuleService.ModuleTreeNode resolveCompatibleParentForAdd(
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            String targetModuleName,
            String childModuleType
    ) {
        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flatten(moduleTree, flatNodes);

        ProjectModuleService.ModuleTreeNode explicitlyMentioned =
                findMentionedNode(
                        instruction,
                        moduleTree,
                        targetModuleName
                );

        ProjectModuleService.ModuleTreeNode compatibleMentioned =
                resolveCompatibleAncestorOrSelf(
                        explicitlyMentioned,
                        flatNodes,
                        childModuleType
                );

        if (compatibleMentioned != null) {
            return compatibleMentioned;
        }

        ProjectModuleService.ModuleTreeNode semanticParent =
                findSemanticParentForAdd(
                        instruction,
                        targetModuleName,
                        childModuleType,
                        flatNodes
                );

        if (semanticParent != null) {
            return semanticParent;
        }

        ProjectModuleService.ModuleTreeNode selectedNode =
                findNodeById(
                        flatNodes,
                        selectedNodeId == null ? null : selectedNodeId.toString()
                );

        ProjectModuleService.ModuleTreeNode compatibleSelected =
                resolveCompatibleAncestorOrSelf(
                        selectedNode,
                        flatNodes,
                        childModuleType
                );

        if (compatibleSelected != null) {
            return compatibleSelected;
        }

        return findFirstCompatibleParent(
                flatNodes,
                childModuleType
        );
    }

    private ProjectModuleService.ModuleTreeNode findSemanticParentForAdd(
            String instruction,
            String targetModuleName,
            String childModuleType,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        String normalizedInstruction = normalizeForMatch(instruction);
        String normalizedTarget = normalizeForMatch(targetModuleName);

        List<String> preferredNames = new ArrayList<>();

        if (normalizedInstruction.contains("用户中心") || normalizedInstruction.contains("用户")) {
            preferredNames.add("登录注册");
            preferredNames.add("用户中心");
            preferredNames.add("账号中心");
            preferredNames.add("用户管理");
        }

        if (normalizedTarget.contains("密码找回")
                || normalizedTarget.contains("找回密码")
                || normalizedTarget.contains("忘记密码")) {
            preferredNames.add("登录注册");
            preferredNames.add("账号中心");
            preferredNames.add("用户中心");
            preferredNames.add("用户管理");
            preferredNames.add("登录");
        }

        if (normalizedInstruction.contains("注册")) {
            preferredNames.add("登录注册");
            preferredNames.add("注册页");
            preferredNames.add("用户中心");
        }

        if (normalizedInstruction.contains("登录")) {
            preferredNames.add("登录注册");
            preferredNames.add("登录页");
            preferredNames.add("用户中心");
        }

        for (String preferredName : preferredNames) {
            ProjectModuleService.ModuleTreeNode matched =
                    findNodeByExactOrContainsName(
                            preferredName,
                            flatNodes
                    );

            ProjectModuleService.ModuleTreeNode compatible =
                    resolveCompatibleAncestorOrSelf(
                            matched,
                            flatNodes,
                            childModuleType
                    );

            if (compatible != null) {
                return compatible;
            }
        }

        return null;
    }

    private ProjectModuleService.ModuleTreeNode findNodeByExactOrContainsName(
            String expectedName,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        String normalizedExpected = normalizeForMatch(expectedName);

        if (normalizedExpected.isBlank() || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        return flatNodes.stream()
                .filter(Objects::nonNull)
                .map(node -> Map.entry(
                        semanticMatchScore(
                                normalizedExpected,
                                node
                        ),
                        node
                ))
                .filter(entry -> entry.getKey() > 0)
                .max((left, right) -> Integer.compare(left.getKey(), right.getKey()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private int semanticMatchScore(
            String normalizedExpected,
            ProjectModuleService.ModuleTreeNode node
    ) {
        int score = 0;

        for (String value : List.of(
                node.getModuleName(),
                node.getModuleKey(),
                node.getModulePath()
        )) {
            String normalizedValue = normalizeForMatch(value);

            if (normalizedValue.isBlank()) {
                continue;
            }

            if (normalizedValue.equals(normalizedExpected)) {
                score = Math.max(score, 1000 + normalizedValue.length());
            } else if (normalizedValue.contains(normalizedExpected)
                    || normalizedExpected.contains(normalizedValue)) {
                score = Math.max(score, normalizedValue.length());
            }
        }

        return score;
    }

    private ProjectModuleService.ModuleTreeNode resolveCompatibleAncestorOrSelf(
            ProjectModuleService.ModuleTreeNode node,
            List<ProjectModuleService.ModuleTreeNode> flatNodes,
            String childModuleType
    ) {
        ProjectModuleService.ModuleTreeNode current = node;

        while (current != null) {
            if (isAllowedChildType(
                    current,
                    childModuleType
            )) {
                return current;
            }

            current = findNodeById(
                    flatNodes,
                    current.getParentId() == null ? null : current.getParentId().toString()
            );
        }

        return null;
    }

    private ProjectModuleService.ModuleTreeNode findFirstCompatibleParent(
            List<ProjectModuleService.ModuleTreeNode> flatNodes,
            String childModuleType
    ) {
        if (flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        return flatNodes.stream()
                .filter(node -> isAllowedChildType(
                        node,
                        childModuleType
                ))
                .min(Comparator.comparingInt(node -> node.getLevelNo() == null ? Integer.MAX_VALUE : node.getLevelNo()))
                .orElse(null);
    }

    private ProjectModuleService.ModuleTreeNode findNodeById(
            List<ProjectModuleService.ModuleTreeNode> flatNodes,
            String nodeId
    ) {
        if (nodeId == null || nodeId.isBlank() || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            if (node != null
                    && node.getId() != null
                    && nodeId.equals(node.getId().toString())) {
                return node;
            }
        }

        return null;
    }

    private boolean isAllowedChildType(
            ProjectModuleService.ModuleTreeNode parent,
            String childModuleType
    ) {
        if (parent == null) {
            return isAllowedRootChildType(childModuleType);
        }

        String parentType = normalizeModuleTypeAlias(
                parent.getModuleType(),
                parent.getModuleName()
        );
        String childType = normalizeModuleTypeAlias(
                childModuleType,
                ""
        );

        return switch (parentType) {
            case "DOMAIN" -> "MODULE".equals(childType);
            case "MODULE" -> List.of("SUBMODULE", "PAGE").contains(childType);
            case "SUBMODULE" -> "PAGE".equals(childType);
            case "PAGE" -> "FEATURE".equals(childType);
            default -> false;
        };
    }

    private boolean isAllowedRootChildType(
            String childModuleType
    ) {
        String childType = normalizeModuleTypeAlias(
                childModuleType,
                ""
        );

        return List.of("DOMAIN", "MODULE").contains(childType);
    }

    private String normalizeModuleTypeAlias(
            String rawType,
            String moduleName
    ) {
        String value = rawType == null ? "" : rawType.trim().toUpperCase();

        if (value.isBlank()) {
            String name = normalizeText(moduleName);

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

        return switch (value) {
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> "DOMAIN";
            case "MODULE", "BUSINESS_MODULE", "FEATURE_MODULE", "INTEGRATION_MODULE", "INFRA_MODULE" -> "MODULE";
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> "SUBMODULE";
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION", "FUNCTION_POINT" -> "FEATURE";
            default -> value;
        };
    }


    private Map<String, Object> normalizeImpact(
            Object value
    ) {
        Map<String, Object> raw = toMap(value);
        Map<String, Object> impact = new LinkedHashMap<>();

        impact.put(
                "summary",
                firstNonBlank(
                        textValue(raw.get("summary")),
                        "模块树变更可能影响相关需求资产。"
                )
        );
        impact.put("affectedNodeIds", asStringList(raw.get("affectedNodeIds")));
        impact.put("affectedArtifacts", normalizeArtifactTypes(raw.get("affectedArtifacts")));
        impact.put("riskNotes", asStringList(raw.get("riskNotes")));
        return impact;
    }

    private List<String> inferAffectedArtifacts(
            List<Map<String, Object>> operations
    ) {
        List<String> result = new ArrayList<>();

        if (operations == null || operations.isEmpty()) {
            return result;
        }

        addUnique(result, "MODULE_PRD");

        for (Map<String, Object> operation : operations) {
            String moduleType = textValue(operation.get("moduleType")).toUpperCase();
            String op = textValue(operation.get("op")).toUpperCase();

            if ("PAGE".equals(moduleType)) {
                addUnique(result, "PAGE_SPEC");
                addUnique(result, "UI_CONTRACT");
                addUnique(result, "TEST_SPEC");
                addUnique(result, "DEV_HANDOFF_PACKAGE");
            } else if ("FEATURE".equals(moduleType)) {
                addUnique(result, "PAGE_SPEC");
                addUnique(result, "TEST_SPEC");
                addUnique(result, "UI_CONTRACT");
            } else {
                addUnique(result, "BUSINESS_FLOW");
                addUnique(result, "PAGE_SPEC");
                addUnique(result, "DATA_MODEL");
                addUnique(result, "API_CONTRACT");
                addUnique(result, "TEST_SPEC");
            }

            if ("MOVE_NODE".equals(op) || "DELETE_NODE".equals(op)) {
                addUnique(result, "BUSINESS_FLOW");
                addUnique(result, "DEV_HANDOFF_PACKAGE");
            }
        }

        return result;
    }

    private List<String> normalizeArtifactTypes(
            Object value
    ) {
        List<String> result = new ArrayList<>();

        for (String item : asStringList(value)) {
            String artifactType = item.trim().toUpperCase();

            if (List.of(
                    "MODULE_PRD",
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC",
                    "UI_CONTRACT",
                    "DEV_HANDOFF_PACKAGE"
            ).contains(artifactType)) {
                addUnique(result, artifactType);
            }
        }

        return result;
    }

    private String buildMarkdownSummary(
            String description,
            List<Map<String, Object>> operations,
            Map<String, Object> impact,
            List<String> validationNotes
    ) {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# 模块树变更提案\n\n");
        markdown.append("摘要：").append(description).append("\n\n");
        markdown.append("## 操作列表\n");

        if (operations == null || operations.isEmpty()) {
            markdown.append("- 无可执行操作。\n");
        } else {
            for (Map<String, Object> operation : operations) {
                markdown.append("- ")
                        .append(operation.getOrDefault("op", "-"))
                        .append("：")
                        .append(operation.getOrDefault("moduleName", operation.getOrDefault("nodeId", "-")))
                        .append("\n");
            }
        }

        markdown.append("\n## 影响范围\n");
        markdown.append("- ").append(textValue(impact.get("summary"))).append("\n");

        List<String> affectedArtifacts = asStringList(impact.get("affectedArtifacts"));

        if (!affectedArtifacts.isEmpty()) {
            markdown.append("\n### 可能影响资产\n");
            for (String artifact : affectedArtifacts) {
                markdown.append("- ").append(artifact).append("\n");
            }
        }

        List<String> riskNotes = asStringList(impact.get("riskNotes"));

        if (!riskNotes.isEmpty()) {
            markdown.append("\n## 风险提示\n");
            for (String note : riskNotes) {
                markdown.append("- ").append(note).append("\n");
            }
        }

        if (validationNotes != null && !validationNotes.isEmpty()) {
            markdown.append("\n## 规范化提示\n");
            for (String note : validationNotes) {
                markdown.append("- ").append(note).append("\n");
            }
        }

        markdown.append("\n> 该结果只是提案，必须由用户确认后才会应用到模块树。\n");
        return markdown.toString();
    }

    private List<Map<String, Object>> operationsOf(
            Map<String, Object> proposal
    ) {
        return normalizeOperations(proposal == null ? null : proposal.get("operations"));
    }

    private void copyIfPresent(
            Map<String, Object> source,
            Map<String, Object> target,
            String key
    ) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Object firstPresent(
            Object... values
    ) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(
            Object value
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!(value instanceof Map<?, ?> source)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        return result;
    }

    private List<String> asStringList(
            Object value
    ) {
        List<String> result = new ArrayList<>();

        if (value instanceof List<?> source) {
            for (Object item : source) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    addUnique(result, String.valueOf(item).trim());
                }
            }
        } else if (value != null && !String.valueOf(value).isBlank()) {
            addUnique(result, String.valueOf(value).trim());
        }

        return result;
    }

    private List<String> dedupe(
            List<String> source
    ) {
        List<String> result = new ArrayList<>();

        if (source == null) {
            return result;
        }

        for (String item : source) {
            addUnique(result, item);
        }

        return result;
    }

    private void addUnique(
            List<String> target,
            String value
    ) {
        if (value != null && !value.isBlank() && !target.contains(value)) {
            target.add(value);
        }
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return keywords.stream().anyMatch(text::contains);
    }

    private String normalizeText(
            String text
    ) {
        return text == null ? "" : text.trim();
    }

    private String normalizeForMatch(
            String text
    ) {
        return normalizeText(text)
                .replaceAll("\\s+", "")
                .toLowerCase();
    }

    private String textValue(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(
            String... values
    ) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String generateModuleKey(
            String moduleName,
            String moduleType
    ) {
        String text = moduleName
                .toLowerCase()
                .replace("用户中心", "user_center")
                .replace("用户", "user")
                .replace("密码找回", "password_recovery")
                .replace("找回密码", "password_recovery")
                .replace("登录注册", "auth")
                .replace("登录", "login")
                .replace("注册", "register")
                .replace("验证码", "verification_code")
                .replace("订单", "order")
                .replace("支付", "payment")
                .replace("退款", "refund")
                .replace("库存", "inventory")
                .replace("后台", "admin")
                .replace("管理", "management")
                .replace("列表", "list")
                .replace("详情", "detail")
                .replace("编辑", "edit")
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
            text = "node_" + Math.abs(moduleName.hashCode());
        }

        String suffix = switch (moduleType) {
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
}