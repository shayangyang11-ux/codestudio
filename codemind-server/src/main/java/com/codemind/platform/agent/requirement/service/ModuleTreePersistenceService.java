package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.requirement.dto.GenerateModuleTreeRequest;
import com.codemind.platform.artifact.ArtifactService;
import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.audit.AuditService;
import com.codemind.platform.agent.common.gateway.AgentGatewayService;
import com.codemind.platform.context.ContextPackageService;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.memory.MemoryWriterService;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.module.ProjectModuleService;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import com.codemind.platform.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.*;

@Service
public class ModuleTreePersistenceService {

    private final ProjectAccessService projectAccessService;
    private final ProjectModuleRepository projectModuleRepository;
    private final ProjectModuleService projectModuleService;
    private final AgentGatewayService agentGatewayService;
    private final AgentTaskService agentTaskService;
    private final ArtifactService artifactService;
    private final ContextPackageService contextPackageService;
    private final AuditService auditService;
    private final MemoryWriterService memoryWriterService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public ModuleTreePersistenceService(
            ProjectAccessService projectAccessService,
            ProjectModuleRepository projectModuleRepository,
            ProjectModuleService projectModuleService,
            AgentGatewayService agentGatewayService,
            AgentTaskService agentTaskService,
            ArtifactService artifactService,
            ContextPackageService contextPackageService,
            AuditService auditService,
            MemoryWriterService memoryWriterService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider
    ) {
        this.projectAccessService = projectAccessService;
        this.projectModuleRepository = projectModuleRepository;
        this.projectModuleService = projectModuleService;
        this.agentGatewayService = agentGatewayService;
        this.agentTaskService = agentTaskService;
        this.artifactService = artifactService;
        this.contextPackageService = contextPackageService;
        this.auditService = auditService;
        this.memoryWriterService = memoryWriterService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public Map<String, Object> generateAndPersistModuleTree(
            UUID workspaceId,
            String projectCode,
            GenerateModuleTreeRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        return generateAndPersistModuleTreeWithProject(
                project,
                workspaceId,
                projectCode,
                request
        );
    }

    public Map<String, Object> generateAndPersistModuleTree(
            String projectCode,
            GenerateModuleTreeRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntityByCode(
                projectCode
        );

        return generateAndPersistModuleTreeWithProject(
                project,
                null,
                projectCode,
                request
        );
    }

    private Map<String, Object> generateAndPersistModuleTreeWithProject(
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            GenerateModuleTreeRequest request
    ) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        Map<String, Object> requestPayload = buildRequestPayload(
                project,
                workspaceId,
                request
        );

        AgentTaskEntity task = agentTaskService.createRunningTask(
                project.getId(),
                null,
                TaskType.GENERATE_MODULE_TREE,
                requestPayload,
                currentUser.username()
        );

        try {
            Map<String, Object> contextPackage = contextPackageService.buildAndLogContextPackage(
                    task,
                    project,
                    null,
                    TaskType.GENERATE_MODULE_TREE,
                    requestPayload
            );

            Object response = agentGatewayService.generateModuleTree(
                    task,
                    project,
                    request,
                    contextPackage
            );

            Map<String, Object> responseEnvelope = toMap(response);
            Map<String, Object> artifactContent = extractArtifactContent(responseEnvelope);
            String contentMarkdown = extractMarkdown(responseEnvelope, artifactContent);

            ArtifactVersionEntity artifact = transactionTemplate.execute(status -> {
                ArtifactVersionEntity savedArtifact = artifactService.saveArtifact(
                        project.getId(),
                        null,
                        task.getId(),
                        "MODULE_TREE",
                        "项目模块树",
                        artifactContent,
                        contentMarkdown,
                        currentUser.username()
                );

                                rebuildProjectModules(
                        project.getId(),
                        savedArtifact.getId(),
                        task.getId(),
                        artifactContent,
                        currentUser.username()
                );

                memoryWriterService.writeModuleTreeMemory(
                        project,
                        task,
                        savedArtifact,
                        artifactContent
                );

                agentTaskService.markSuccess(
                        task.getId(),
                        responseEnvelope,
                        savedArtifact.getId()
                );

                auditService.record(
                        project.getId(),
                        "USER",
                        currentUser.username(),
                        "GENERATE_MODULE_TREE",
                        "ARTIFACT",
                        savedArtifact.getId(),
                        null,
                        Map.of(
                                "artifact_id", savedArtifact.getId().toString(),
                                "artifact_type", "MODULE_TREE",
                                "version_no", savedArtifact.getVersionNo()
                        ),
                        Map.of(
                                "task_id", task.getId().toString(),
                                "project_code", projectCode,
                                "workspace_id", workspaceId == null ? "" : workspaceId.toString()
                        )
                );

                return savedArtifact;
            });

            if (artifact == null) {
                throw new IllegalStateException("Failed to save MODULE_TREE artifact.");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("task_id", task.getId());
            result.put("artifact_id", artifact.getId());
            result.put("artifact_type", artifact.getArtifactType());
            result.put("version_no", artifact.getVersionNo());
            result.put("content", artifactContent);

            return result;

        } catch (Exception e) {
            agentTaskService.markFailed(
                    task.getId(),
                    "GENERATE_MODULE_TREE_FAILED",
                    e.getMessage()
            );

            auditService.record(
                    project.getId(),
                    "USER",
                    currentUser.username(),
                    "GENERATE_MODULE_TREE_FAILED",
                    "AGENT_TASK",
                    task.getId(),
                    null,
                    Map.of(
                            "status", "FAILED",
                            "error_message", e.getMessage() == null ? "" : e.getMessage()
                    ),
                    Map.of(
                            "project_code", projectCode,
                            "workspace_id", workspaceId == null ? "" : workspaceId.toString(),
                            "task_id", task.getId().toString()
                    )
            );

            throw e;
        }
    }

    private Map<String, Object> buildRequestPayload(
            ProjectEntity project,
            UUID workspaceId,
            GenerateModuleTreeRequest request
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project_id", project.getId().toString());
        payload.put("project_code", project.getProjectCode());
        payload.put("task_type", TaskType.GENERATE_MODULE_TREE.name());
        payload.put("requirement_text", request.getRequirementText());
        payload.put("modeling_mode", request.getModelingMode());

        if (workspaceId != null) {
            payload.put("workspace_id", workspaceId.toString());
        }

        return payload;
    }

    private void rebuildProjectModules(
            UUID projectId,
            UUID sourceArtifactId,
            UUID taskId,
            Map<String,
            Object> responseMap,
            String operator
    ) {
        projectModuleRepository.deleteByProjectId(projectId);

        List<?> modules = extractModules(responseMap);

        Map<String, Integer> usedModuleKeys = new HashMap<>();

        int index = 0;
        for (Object item : modules) {
                        saveModuleRecursive(
                    projectId,
                    null,
                    sourceArtifactId,
                    taskId,
                    item,
                    1,
                    index,
                    "",
                    usedModuleKeys,
                    operator
            );
            index++;
        }

        projectModuleRepository.flush();
    }

    private void saveModuleRecursive(
            UUID projectId,
            UUID parentId,
            UUID sourceArtifactId,
            UUID taskId,
            Object rawNode,
            int level,
            int sortOrder,
            String parentPath,
            Map<String,
            Integer> usedModuleKeys,
            String operator
    ) {
        Map<String, Object> node = toMap(rawNode);

        String moduleName = readString(node, "moduleName", "name", "title");
        if (moduleName == null || moduleName.isBlank()) {
            moduleName = "未命名模块";
        }

        String modulePath = parentPath.isBlank()
                ? moduleName
                : parentPath + "/" + moduleName;

        String rawModuleKey = readString(node, "moduleKey", "key", "id");
        String moduleKey = normalizeModuleKey(rawModuleKey, modulePath, usedModuleKeys);

        ProjectModuleEntity entity = new ProjectModuleEntity();
        entity.setProjectId(projectId);
        entity.setParentId(parentId);
        entity.setModuleKey(moduleKey);
        entity.setModuleName(moduleName);
        String moduleType = normalizeModuleType(
                readString(node, "moduleType", "type", "nodeType")
        );

        node.put("moduleType", moduleType);
        node.put("type", moduleType);

        if ("PAGE".equals(moduleType)) {
            node.put("canGenerateUiContract", true);
        } else {
            node.put("canGenerateUiContract", false);
        }

        node.put("requirementMode", resolveGeneratedRequirementMode(moduleType, node));

        a9155ApplyGeneratedBlueprintTraceToNode(node, moduleType);

        entity.setModuleType(moduleType);
        entity.setModulePath(modulePath);
        entity.setLevelNo(level);
        entity.setSortOrder(sortOrder);
        entity.setStatus("ACTIVE");
        entity.setSourceArtifactId(sourceArtifactId);
        entity.setExtra(node);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);

        entity = projectModuleRepository.save(entity);

        a9155BackfillSyntheticModulePrdFromGeneratedBlueprint(
                projectId,
                entity,
                sourceArtifactId,
                taskId,
                node,
                operator
        );

        Object childrenObj = node.get("children");
        if (childrenObj instanceof List<?> children) {
            int childIndex = 0;
            for (Object child : children) {
                                saveModuleRecursive(
                        projectId,
                        entity.getId(),
                        sourceArtifactId,
                        taskId,
                        child,
                        level + 1,
                        childIndex,
                        modulePath,
                        usedModuleKeys,
                        operator
                );
                childIndex++;
            }
        }
    }



    // A9.10.14.4.3.15.4.15.5.3_PRD_QUALITY_BACKFILL_V4
    // The first backfill only made MODULE_PRD non-empty. This version builds a
    // full user-facing PRD from moduleRequirementMarkdown + generated child tree:
    // explanation tab gets background/roles/rules/exceptions/acceptance/custom
    // sections; prototype tab gets functions/pages/data/states/permissions with
    // trace metadata. No extra LLM call is made here.

    // A9.10.14.4.3.15.4.15.5.4.2_FIX_BACKFILL_HELPER_PLACEMENT
    // Backfill MODULE_PRD for terminal MODULEs, then materialize FEATURE/PAGE
    // from MODULE_PRD structured content. MODULE tree generation itself must not
    // be the semantic source of FEATURE/PAGE.
    private void a9155BackfillSyntheticModulePrdFromGeneratedBlueprint(
            UUID projectId,
            ProjectModuleEntity module,
            UUID sourceArtifactId,
            UUID taskId,
            Map<String, Object> node,
            String operator
    ) {
        if (module == null || node == null) {
            return;
        }

        if (!"MODULE".equals(normalizeModuleType(module.getModuleType()))) {
            return;
        }

        String markdown = a9155ExtractGeneratedModuleRequirementMarkdown(node);
        if (markdown == null || markdown.isBlank()) {
            return;
        }

        Object childrenObj = node.get("children");
        List<?> children = childrenObj instanceof List<?> list ? list : List.of();

        // A MODULE with child MODULEs is still a grouping/draft module.
        // It must not be finalized into MODULE_PRD yet.
        if (a91554ContainsDirectGeneratedModule(children)) {
            return;
        }

        boolean hasDirectGeneratedFeatureOrPage = a9155ContainsDirectGeneratedFeatureOrPage(children);

        Map<String, Object> content = a9155BuildSyntheticModulePrdContent(
                module,
                node,
                markdown,
                children,
                sourceArtifactId
        );
        String contentMarkdown = a91553BuildSyntheticModulePrdMarkdown(content, markdown);

        artifactService.saveArtifact(
                projectId,
                module.getId(),
                taskId,
                "MODULE_PRD",
                module.getModuleName() + " 模块需求",
                content,
                contentMarkdown,
                operator
        );

        Map<String, Object> extra = module.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(module.getExtra());
        extra.put("hasModulePrd", true);
        extra.put("finalRequirementModule", true);
        extra.put("modulePrdBackfilledFromModuleTree", true);
        extra.put("modulePrdBackfillSource", "MODULE_TREE_FULL_BLUEPRINT");
        extra.put("modulePrdQualityBackfillVersion", "A9.10.14.4.3.15.4.15.5.4.2");
        extra.put("modulePrdBackfillSourceArtifactId", sourceArtifactId == null ? "" : sourceArtifactId.toString());
        module.setExtra(extra);
        projectModuleRepository.save(module);

        if (!hasDirectGeneratedFeatureOrPage) {
            a91554MaterializeFeaturePageFromBackfilledModulePrd(
                    projectId,
                    module.getId(),
                    content,
                    operator
            );
        }
    }

    private boolean a91554ContainsDirectGeneratedModule(List<?> children) {
        if (children == null || children.isEmpty()) {
            return false;
        }

        for (Object raw : children) {
            Map<String, Object> child = toMap(raw);
            String type = normalizeModuleType(readString(child, "moduleType", "type", "nodeType"));
            if ("MODULE".equals(type)) {
                return true;
            }
        }

        return false;
    }

    private void a91554MaterializeFeaturePageFromBackfilledModulePrd(
            UUID projectId,
            UUID moduleId,
            Map<String, Object> modulePrdContent,
            String operator
    ) {
        Map<String, Object> materializationResult = new LinkedHashMap<>();

        try {
            materializationResult = projectModuleService.completeRequirementModuleChildrenFromModulePrd(
                    projectId,
                    moduleId,
                    modulePrdContent == null ? new LinkedHashMap<>() : modulePrdContent,
                    operator
            );

            a91554RecordPrdMaterializationResult(
                    moduleId,
                    materializationResult,
                    null
            );
        } catch (Exception error) {
            materializationResult.put("source", "A9.10.14.4.3.15.4.15.5.4.2_FIX_BACKFILL_HELPER_PLACEMENT");
            materializationResult.put("skipped", true);
            materializationResult.put("reason", "AUTO_MATERIALIZE_FROM_BACKFILLED_MODULE_PRD_FAILED");
            materializationResult.put("errorType", error.getClass().getSimpleName());
            materializationResult.put("errorMessage", error.getMessage() == null ? "" : error.getMessage());

            a91554RecordPrdMaterializationResult(
                    moduleId,
                    materializationResult,
                    error
            );
        }
    }

    private void a91554RecordPrdMaterializationResult(
            UUID moduleId,
            Map<String, Object> materializationResult,
            Exception error
    ) {
        if (moduleId == null) {
            return;
        }

        try {
            ProjectModuleEntity module = projectModuleRepository.findById(moduleId).orElse(null);
            if (module == null) {
                return;
            }

            Map<String, Object> extra = module.getExtra() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(module.getExtra());

            Map<String, Object> safeResult = materializationResult == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(materializationResult);

            boolean skipped = Boolean.TRUE.equals(safeResult.get("skipped"));
            extra.put("modulePrdFeaturePageMaterializationAttempted", true);
            extra.put("modulePrdFeaturePageMaterialized", !skipped);
            extra.put("modulePrdFeaturePageMaterializationResult", safeResult);
            extra.put("modulePrdFeaturePageMaterializationSource", "MODULE_PRD");
            extra.put("modulePrdFeaturePageMaterializationVersion", "A9.10.14.4.3.15.4.15.5.4.2");

            if (error != null) {
                extra.put("modulePrdFeaturePageMaterializationErrorType", error.getClass().getSimpleName());
                extra.put("modulePrdFeaturePageMaterializationErrorMessage", error.getMessage() == null ? "" : error.getMessage());
            }

            module.setExtra(extra);
            projectModuleRepository.save(module);
        } catch (Exception ignored) {
            // Diagnostic metadata must not break module tree persistence.
        }
    }

    private Map<String, Object> a9155BuildSyntheticModulePrdContent(
            ProjectModuleEntity module,
            Map<String, Object> node,
            String markdown,
            List<?> children,
            UUID sourceArtifactId
    ) {
        Map<String, String> sections = a91553ParseMarkdownSections(markdown);
        List<Map<String, Object>> pageRequirements = a91553BuildSyntheticPageRequirements(children, sections);
        List<Map<String, Object>> functionalRequirements = a91553BuildSyntheticFunctionalRequirements(children, sections, pageRequirements);
        List<Map<String, Object>> dataRequirements = a91553BuildSyntheticDataRequirements(module, sections, pageRequirements);
        List<Map<String, Object>> userRoles = a91553BuildSyntheticUserRoles(sections);
        List<Map<String, Object>> stateRules = a91553BuildSyntheticStateRules(sections);
        List<Map<String, Object>> permissionRequirements = a91553BuildSyntheticPermissionRequirements(sections, userRoles);
        List<Map<String, Object>> businessRules = a91553BuildSyntheticBusinessRules(sections, stateRules);
        List<Map<String, Object>> exceptionScenarios = a91553BuildSyntheticExceptionScenarios(sections);
        List<Map<String, Object>> acceptanceCriteria = a91553BuildSyntheticAcceptanceCriteria(sections, functionalRequirements, pageRequirements);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("prd_contract_version", "2.1");
        content.put("module_basic_info", a91553BuildSyntheticModuleBasicInfo(module, node, sections));
        content.put("business_background", a91553BuildSyntheticBusinessBackground(module, node, sections));
        content.put("user_roles", userRoles);
        content.put("functional_requirements", functionalRequirements);
        content.put("business_rules", businessRules);
        content.put("state_rules", stateRules);
        content.put("page_requirements", pageRequirements);
        content.put("data_requirements", dataRequirements);
        content.put("permission_requirements", permissionRequirements);
        content.put("exception_scenarios", exceptionScenarios);
        content.put("acceptance_criteria", acceptanceCriteria);
        content.put("non_functional_requirements", a91553BuildSyntheticNonFunctionalRequirements(module));
        content.put("custom_sections", a91553BuildSyntheticCustomSections(sections));
        content.put("development_notes", a91553ListOf("完整模块树回灌生成的 MODULE_PRD，后续应以 MODULE_PRD 为语义源头继续生成 PAGE_SPEC、UI_CONTRACT 和开发交接材料。"));
        content.put("test_notes", a91553ListOf("围绕主流程、状态流转、权限边界、异常场景和页面关键操作设计测试用例。"));
        content.put("analysis_summary", "已从完整模块树的模块需求上下文和页面/功能子树回灌为结构化 MODULE_PRD；来源保留为 MODULE_TREE_FULL_BLUEPRINT，确认状态为 PENDING_CONFIRMATION。");

        Map<String, Object> sourceOutline = new LinkedHashMap<>();
        sourceOutline.put("sourceType", "MODULE_TREE_FULL_BLUEPRINT");
        sourceOutline.put("source_type", "MODULE_TREE_FULL_BLUEPRINT");
        sourceOutline.put("sourceArtifactId", sourceArtifactId == null ? "" : sourceArtifactId.toString());
        sourceOutline.put("source_artifact_id", sourceArtifactId == null ? "" : sourceArtifactId.toString());
        sourceOutline.put("moduleNodeKey", readString(node, "moduleKey", "key", "id"));
        sourceOutline.put("module_node_key", readString(node, "moduleKey", "key", "id"));
        sourceOutline.put("moduleName", module.getModuleName());
        sourceOutline.put("module_name", module.getModuleName());
        sourceOutline.put("qualityBackfillVersion", "A9.10.14.4.3.15.4.15.5.3_V4");
        content.put("source_outline", sourceOutline);

        Map<String, Object> syncPolicy = new LinkedHashMap<>();
        syncPolicy.put("semanticSource", "MODULE_PRD");
        syncPolicy.put("semantic_source", "MODULE_PRD");
        syncPolicy.put("materializedFrom", "MODULE_TREE_FULL_BLUEPRINT");
        syncPolicy.put("materialized_from", "MODULE_TREE_FULL_BLUEPRINT");
        syncPolicy.put("confirmationStatus", "PENDING_CONFIRMATION");
        syncPolicy.put("confirmation_status", "PENDING_CONFIRMATION");
        syncPolicy.put("sourceType", "AI_COMPLETED");
        syncPolicy.put("source_type", "AI_COMPLETED");
        content.put("semantic_sync_policy", syncPolicy);

        content.put("structured_requirements", a91553BuildStructuredRequirements(content));
        content.put("navigation_requirements", a91553BuildNavigationRequirements(pageRequirements));
        content.put("traceability_map", a9155BuildSyntheticTraceabilityMap(content));

        return content;
    }

    private Map<String, Object> a91553BuildSyntheticModuleBasicInfo(
            ProjectModuleEntity module,
            Map<String, Object> node,
            Map<String, String> sections
    ) {
        Map<String, Object> basic = new LinkedHashMap<>();
        String moduleKey = a9155FirstNonBlank(module.getModuleKey(), readString(node, "moduleKey", "key", "id"));
        String goal = a91553CompactText(a9155FirstNonBlank(sections.get("业务目标"), readString(node, "description")));
        String boundary = a91553CompactText(a9155FirstNonBlank(sections.get("业务范围"), sections.get("模块定位"), readString(node, "description")));

        basic.put("module_name", module.getModuleName());
        basic.put("module_key", moduleKey);
        basic.put("module_type", "业务需求模块");
        basic.put("module_position", module.getModulePath());
        basic.put("module_goal", goal);
        basic.put("responsibility_boundary", boundary);
        basic.put("sourceType", "AI_COMPLETED");
        basic.put("source_type", "AI_COMPLETED");
        basic.put("confirmationStatus", "PENDING_CONFIRMATION");
        basic.put("confirmation_status", "PENDING_CONFIRMATION");
        return basic;
    }

    private String a91553BuildSyntheticBusinessBackground(
            ProjectModuleEntity module,
            Map<String, Object> node,
            Map<String, String> sections
    ) {
        List<String> parts = new ArrayList<>();
        a91553AddIfPresent(parts, sections.get("模块定位"));
        a91553AddIfPresent(parts, sections.get("业务目标"));
        a91553AddIfPresent(parts, sections.get("业务范围"));
        a91553AddIfPresent(parts, sections.get("主流程"));
        if (parts.isEmpty()) {
            a91553AddIfPresent(parts, readString(node, "description"));
        }
        if (parts.isEmpty()) {
            parts.add("末级需求模块：" + module.getModuleName() + "。请结合左侧模块树继续完善业务背景、目标、范围和流程。 ");
        }
        return a91553CompactParagraphs(parts);
    }

    private List<Map<String, Object>> a91553BuildSyntheticUserRoles(Map<String, String> sections) {
        List<String> items = a9155BulletItems(sections.get("使用角色"));
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (String item : items) {
            String[] parts = item.split("[:：]", 2);
            String roleName = parts.length > 0 ? parts[0].trim() : item.trim();
            String responsibility = parts.length > 1 ? parts[1].trim() : "参与当前模块的业务处理、查询和协同。";
            Map<String, Object> row = a91553BaseRecord("ROLE-" + index, roleName, responsibility);
            row.put("role_name", roleName);
            row.put("responsibility", responsibility);
            row.put("responsibilities", responsibility);
            row.put("scope", "当前模块");
            result.add(row);
            index++;
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticFunctionalRequirements(
            List<?> children,
            Map<String, String> sections,
            List<Map<String, Object>> pageRequirements
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String item : a9155BulletItems(a9155FirstNonBlank(
                sections.get("功能点需求"),
                sections.get("功能需求"),
                sections.get("核心功能")
        ))) {
            a91553AddUnique(names, item);
        }
        for (Map<String, Object> child : a91553CollectNodesByType(children, "FEATURE")) {
            a91553AddUnique(names, a9155FirstNonBlank(readString(child, "moduleName", "name", "title"), readString(child, "moduleKey", "key", "id")));
        }
        if (names.isEmpty()) {
            for (Map<String, Object> page : pageRequirements) {
                String pageName = readString(page, "page_name", "title", "name");
                String featureName = pageName.replace("页面", "").replace("页", "").replace("弹窗", "");
                if (!featureName.isBlank()) {
                    a91553AddUnique(names, featureName);
                }
            }
        }

        int index = 1;
        for (String name : names) {
            String key = a91553StableKey("fr_", name, index);
            Map<String, Object> row = a91553BaseRecord("FR-" + a9155SanitizeTraceKey(key), name, a91553FunctionalDescription(name, pageRequirements));
            row.put("requirement_id", "FR-" + a9155SanitizeTraceKey(key));
            row.put("requirement_key", key);
            row.put("requirement_name", name);
            row.put("title", name);
            row.put("name", name);
            row.put("priority", "P1");
            row.put("user_value", "支撑当前模块主流程闭环，减少人工分散记录和跨角色沟通成本。");
            row.put("acceptance", a91553ListOf("能够完成" + name + "的新增、查询、查看或状态处理", "关键字段、状态和权限符合模块需求边界"));
            row.put("related_pages", a91553RelatedPages(name, pageRequirements));
            row.put("treeNodeKey", key);
            row.put("tree_node_key", key);
            row.put("targetNodeType", "FEATURE");
            row.put("target_node_type", "FEATURE");
            result.add(row);
            index++;
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticPageRequirements(
            List<?> children,
            Map<String, String> sections
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> pageNodes = a91553CollectNodesByType(children, "PAGE");
        int index = 1;
        for (Map<String, Object> node : pageNodes) {
            String nodeKey = a9155FirstNonBlank(readString(node, "moduleKey", "key", "id"), "page_" + index);
            String title = a9155FirstNonBlank(readString(node, "moduleName", "name", "title"), "页面" + index);
            String pageKind = a9155FirstNonBlank(readString(node, "page_kind", "pageKind"), a91553InferPageType(title));
            String entity = a9155FirstNonBlank(readString(node, "primary_entity", "primaryEntity"), a91553InferEntityFromName(title));

            Map<String, Object> item = a91553BaseRecord("PG-" + a9155SanitizeTraceKey(nodeKey), title, a9155FirstNonBlank(readString(node, "description"), a91553PageDescription(title)));
            item.put("page_id", "PG-" + a9155SanitizeTraceKey(nodeKey));
            item.put("page_key", nodeKey);
            item.put("title", title);
            item.put("name", title);
            item.put("page_name", title);
            item.put("page_type", pageKind);
            item.put("route_path", a9155FirstNonBlank(readString(node, "route_path", "routePath"), "/" + a9155SanitizeTraceKey(nodeKey).replace('_', '-')));
            item.put("layout_type", a9155FirstNonBlank(readString(node, "layout_type", "layoutType"), a91553InferLayoutType(pageKind, title)));
            item.put("primary_entity", entity);
            item.put("purpose", a91553PageDescription(title));
            item.put("main_actions", a91553InferPageActions(title, pageKind));
            item.put("filter_fields", a91553InferFilterFields(title, entity));
            item.put("table_columns", a91553InferTableColumns(title, entity));
            item.put("form_fields", a91553InferFormFields(title, entity));
            item.put("detail_sections", a91553InferDetailSections(title, entity));
            item.put("navigation_targets", a91553InferNavigationTargets(title, pageNodes));
            item.put("treeNodeKey", nodeKey);
            item.put("tree_node_key", nodeKey);
            item.put("targetNodeType", "PAGE");
            item.put("target_node_type", "PAGE");
            result.add(item);
            index++;
        }
        if (result.isEmpty()) {
            a91554AddPageRequirementsFromMarkdown(result, sections);
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticDataRequirements(
            ProjectModuleEntity module,
            Map<String, String> sections,
            List<Map<String, Object>> pages
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> objects = a9155BulletItems(sections.get("业务对象"));
        if (objects.isEmpty()) {
            for (Map<String, Object> page : pages) {
                a91553AddUnique(objects, a9155FirstNonBlank(readString(page, "primary_entity"), module.getModuleName()));
            }
        }
        int index = 1;
        for (String objectText : objects) {
            String entityName = objectText;
            String entityKey = "";
            int left = objectText.indexOf('（');
            int right = objectText.indexOf('）');
            if (left >= 0 && right > left) {
                entityName = objectText.substring(0, left).trim();
                entityKey = objectText.substring(left + 1, right).trim();
            }
            if (entityKey.isBlank()) {
                entityKey = a91553StableKey("entity_", entityName, index);
            }
            Map<String, Object> row = a91553BaseRecord("DR-" + index, entityName, "当前模块需要维护或引用的业务数据对象。 ");
            row.put("entity_name", entityName);
            row.put("entity_key", entityKey);
            row.put("important_fields", a91553InferImportantFields(module.getModuleName(), entityName, entityKey));
            row.put("constraints", a91553ListOf("关键编号不可重复", "状态字段需符合模块状态规则", "金额、数量、日期等字段需要基础校验"));
            row.put("relationships", a91553InferRelationships(entityName, pages));
            result.add(row);
            index++;
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticBusinessRules(
            Map<String, String> sections,
            List<Map<String, Object>> stateRules
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (String item : a9155BulletItems(sections.get("业务规则"))) {
            Map<String, Object> row = a91553BaseRecord("BR-" + index, item, item);
            row.put("rule_name", a91553RuleTitle(item, index));
            row.put("trigger_condition", "业务操作触发");
            row.put("expected_result", item);
            result.add(row);
            index++;
        }
        if (result.isEmpty() && !stateRules.isEmpty()) {
            Map<String, Object> row = a91553BaseRecord("BR-STATE", "状态合法流转", "模块状态变更必须符合状态规则，不允许越权或越级流转。 ");
            row.put("rule_name", "状态合法流转");
            row.put("trigger_condition", "状态更新、提交确认、异常处理");
            row.put("expected_result", "状态更新后业务对象处于可追踪、可审计状态");
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticStateRules(Map<String, String> sections) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (String item : a9155BulletItems(sections.get("状态规则"))) {
            Map<String, Object> row = a91553BaseRecord("SR-" + index, a91553RuleTitle(item, index), item);
            String[] parts = item.split("[:：]", 2);
            row.put("state_name", parts.length > 0 ? parts[0].trim() : "状态规则" + index);
            row.put("description", parts.length > 1 ? parts[1].trim() : item);
            row.put("states", parts.length > 1 ? a91553SplitByComma(parts[1]) : a91553SplitByComma(item));
            row.put("entry_condition", "完成前置业务操作或人工确认");
            row.put("exit_condition", "进入下一业务节点或关闭流程");
            result.add(row);
            index++;
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticPermissionRequirements(
            Map<String, String> sections,
            List<Map<String, Object>> roles
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (String item : a9155BulletItems(sections.get("权限边界"))) {
            Map<String, Object> row = a91553BaseRecord("PR-" + index, a91553RuleTitle(item, index), item);
            row.put("permission_name", a91553RuleTitle(item, index));
            row.put("permission_key", "perm_" + index);
            row.put("roles", a91553InferRolesFromText(item, roles));
            row.put("actions", a91553InferActionsFromText(item));
            row.put("scope", "当前模块及其下级页面");
            result.add(row);
            index++;
        }
        if (result.isEmpty()) {
            for (Map<String, Object> role : roles) {
                String roleName = readString(role, "role_name", "title", "name");
                Map<String, Object> row = a91553BaseRecord("PR-" + index, roleName + "权限", roleName + "可按职责访问当前模块相关页面和数据。 ");
                row.put("permission_name", roleName + "权限");
                row.put("permission_key", "perm_" + a9155SanitizeTraceKey(roleName));
                row.put("roles", a91553ListOf(roleName));
                row.put("actions", a91553ListOf("查看", "处理", "维护"));
                row.put("scope", "当前模块");
                result.add(row);
                index++;
            }
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticExceptionScenarios(Map<String, String> sections) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (String item : a9155BulletItems(sections.get("异常场景"))) {
            Map<String, Object> row = a91553BaseRecord("EX-" + index, a91553RuleTitle(item, index), item);
            row.put("scenario_name", a91553RuleTitle(item, index));
            row.put("trigger_condition", item);
            row.put("handling_strategy", "提示原因、阻断错误提交，并保留可追踪的处理记录。 ");
            result.add(row);
            index++;
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticAcceptanceCriteria(
            Map<String, String> sections,
            List<Map<String, Object>> functions,
            List<Map<String, Object>> pages
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (String item : a9155BulletItems(sections.get("验收要点"))) {
            Map<String, Object> row = a91553BaseRecord("AC-" + index, a91553RuleTitle(item, index), item);
            row.put("criterion_name", a91553RuleTitle(item, index));
            row.put("verification_method", "按主流程进行端到端验收");
            row.put("expected_result", item);
            result.add(row);
            index++;
        }
        if (result.isEmpty()) {
            Map<String, Object> row = a91553BaseRecord("AC-FLOW", "主流程闭环", "核心功能、页面、状态和权限能够支撑当前模块业务闭环。 ");
            row.put("criterion_name", "主流程闭环");
            row.put("verification_method", "使用角色按页面路径完成新增、查询、详情、状态更新和异常处理");
            row.put("expected_result", "所有 P1 功能可完成，页面信息一致，状态与权限校验生效");
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticNonFunctionalRequirements(ProjectModuleEntity module) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> usability = a91553BaseRecord("NFR-USABILITY", "可用性", "列表、详情、表单和弹窗操作路径清晰，字段提示明确。 ");
        usability.put("nfr_name", "可用性");
        usability.put("target", "一线业务角色可独立完成核心操作");
        usability.put("verification_method", "角色验收和页面走查");
        result.add(usability);

        Map<String, Object> audit = a91553BaseRecord("NFR-AUDIT", "可追溯性", "关键创建、编辑、状态更新、异常处理需要保留操作记录。 ");
        audit.put("nfr_name", "可追溯性");
        audit.put("target", "关键变更可追踪到角色、时间和业务对象");
        audit.put("verification_method", "检查操作日志和状态历史");
        result.add(audit);
        return result;
    }

    private List<Map<String, Object>> a91553BuildSyntheticCustomSections(Map<String, String> sections) {
        List<Map<String, Object>> result = new ArrayList<>();
        a91553AddCustomSection(result, "main_flow", "主流程", sections.get("主流程"));
        a91553AddCustomSection(result, "business_objects", "业务对象", sections.get("业务对象"));
        a91553AddCustomSection(result, "open_questions", "待确认事项", "请确认当前完整模块树中 AI 补全的功能、页面、状态、权限和字段是否符合真实业务边界。 ");
        return result;
    }

    private void a91553AddCustomSection(
            List<Map<String, Object>> result,
            String key,
            String title,
            String content
    ) {
        if (content == null || content.isBlank()) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("section_key", key);
        row.put("sectionKey", key);
        row.put("title", title);
        row.put("content", content.trim());
        row.put("markdown", content.trim());
        row.put("visibility", "USER_VISIBLE");
        row.put("source", "MODULE_TREE_FULL_BLUEPRINT");
        row.put("impact_policy", "DOCUMENT_ONLY");
        row.put("impactPolicy", "DOCUMENT_ONLY");
        row.put("sourceType", "AI_COMPLETED");
        row.put("confirmationStatus", "PENDING_CONFIRMATION");
        result.add(row);
    }

    private List<Map<String, Object>> a91553BuildStructuredRequirements(Map<String, Object> content) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object functional = content.get("functional_requirements");
        if (functional instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> item = toMap(raw);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("requirementId", readString(item, "requirement_id", "requirementId"));
                row.put("requirementKey", readString(item, "requirement_key", "requirementKey"));
                row.put("title", readString(item, "title", "name", "requirement_name"));
                row.put("type", "FUNCTIONAL_REQUIREMENT");
                row.put("sourceType", "AI_COMPLETED");
                row.put("confirmationStatus", "PENDING_CONFIRMATION");
                result.add(row);
            }
        }
        Object pages = content.get("page_requirements");
        if (pages instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> item = toMap(raw);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("requirementId", readString(item, "page_id", "pageId"));
                row.put("requirementKey", readString(item, "page_key", "pageKey"));
                row.put("title", readString(item, "title", "page_name", "name"));
                row.put("type", "PAGE_REQUIREMENT");
                row.put("sourceType", "AI_COMPLETED");
                row.put("confirmationStatus", "PENDING_CONFIRMATION");
                result.add(row);
            }
        }
        return result;
    }

    private List<Map<String, Object>> a91553BuildNavigationRequirements(List<Map<String, Object>> pages) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> page : pages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("navigationId", "NAV-" + index);
            row.put("sourcePage", readString(page, "page_name", "title"));
            row.put("routePath", readString(page, "route_path", "routePath"));
            row.put("description", "当前页面纳入模块导航和原型入口。 ");
            row.put("sourceType", "AI_COMPLETED");
            row.put("confirmationStatus", "PENDING_CONFIRMATION");
            result.add(row);
            index++;
        }
        return result;
    }

    private Map<String, Object> a9155BuildSyntheticTraceabilityMap(Map<String, Object> content) {
        Map<String, Object> trace = new LinkedHashMap<>();
        List<Map<String, Object>> requirements = new ArrayList<>();

        Object functional = content.get("functional_requirements");
        if (functional instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> item = toMap(raw);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("requirementId", a9155FirstNonBlank(readString(item, "requirement_id", "requirementId"), ""));
                row.put("requirementKey", a9155FirstNonBlank(readString(item, "requirement_key", "requirementKey"), ""));
                row.put("requirementTitle", a9155FirstNonBlank(readString(item, "title", "name"), ""));
                row.put("targetNodeType", "FEATURE");
                row.put("targetNodeKey", a9155FirstNonBlank(readString(item, "treeNodeKey", "tree_node_key", "requirement_key"), ""));
                row.put("sourceType", "AI_COMPLETED");
                row.put("confirmationStatus", "PENDING_CONFIRMATION");
                requirements.add(row);
            }
        }

        Object pages = content.get("page_requirements");
        if (pages instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> item = toMap(raw);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("requirementId", a9155FirstNonBlank(readString(item, "page_id", "pageId"), ""));
                row.put("requirementKey", a9155FirstNonBlank(readString(item, "page_key", "pageKey"), ""));
                row.put("requirementTitle", a9155FirstNonBlank(readString(item, "title", "name", "page_name"), ""));
                row.put("targetNodeType", "PAGE");
                row.put("targetNodeKey", a9155FirstNonBlank(readString(item, "treeNodeKey", "tree_node_key", "page_key"), ""));
                row.put("sourceType", "AI_COMPLETED");
                row.put("confirmationStatus", "PENDING_CONFIRMATION");
                requirements.add(row);
            }
        }

        trace.put("requirements", requirements);
        trace.put("sourceType", "MODULE_TREE_FULL_BLUEPRINT");
        trace.put("confirmationStatus", "PENDING_CONFIRMATION");
        trace.put("qualityBackfillVersion", "A9.10.14.4.3.15.4.15.5.3_V4");
        return trace;
    }

    private void a9155ApplyGeneratedBlueprintTraceToNode(
            Map<String, Object> node,
            String moduleType
    ) {
        if (node == null) {
            return;
        }

        String normalizedType = normalizeModuleType(moduleType);
        if (!"FEATURE".equals(normalizedType) && !"PAGE".equals(normalizedType)) {
            return;
        }

        String nodeKey = a9155FirstNonBlank(readString(node, "moduleKey", "key", "id"), normalizedType.toLowerCase(Locale.ROOT) + "_" + a9155ResultHash(node));
        String nodeName = a9155FirstNonBlank(readString(node, "moduleName", "name", "title"), nodeKey);
        String requirementId = ("PAGE".equals(normalizedType) ? "PG-" : "FR-") + a9155SanitizeTraceKey(nodeKey);

        node.putIfAbsent("sourceRequirementId", requirementId);
        node.putIfAbsent("sourceRequirementKey", nodeKey);
        node.putIfAbsent("sourceRequirementTitle", nodeName);
        node.putIfAbsent("sourceType", "AI_COMPLETED");
        node.putIfAbsent("confirmationStatus", "PENDING_CONFIRMATION");
        node.putIfAbsent("sourceEvidence", "MODULE_TREE_FULL_BLUEPRINT");

        Map<String, Object> prdTrace = new LinkedHashMap<>();
        prdTrace.put("requirementId", requirementId);
        prdTrace.put("requirementKey", nodeKey);
        prdTrace.put("requirementTitle", nodeName);
        prdTrace.put("sourceType", "AI_COMPLETED");
        prdTrace.put("confirmationStatus", "PENDING_CONFIRMATION");
        prdTrace.put("sourceEvidence", "MODULE_TREE_FULL_BLUEPRINT");
        prdTrace.put("targetNodeType", normalizedType);
        prdTrace.put("targetNodeKey", nodeKey);

        node.putIfAbsent("prdTrace", prdTrace);
        node.putIfAbsent("prd_trace", prdTrace);
    }

    private String a9155ExtractGeneratedModuleRequirementMarkdown(Map<String, Object> node) {
        if (node == null) {
            return "";
        }

        Object context = node.get("requirement_context");
        if (!(context instanceof Map<?, ?>)) {
            context = node.get("requirementContext");
        }

        if (context instanceof Map<?, ?> contextMap) {
            Object markdown = contextMap.get("moduleRequirementMarkdown");
            if (markdown == null) {
                markdown = contextMap.get("module_requirement_markdown");
            }
            if (markdown != null) {
                return String.valueOf(markdown);
            }
        }

        Object markdown = node.get("moduleRequirementMarkdown");
        if (markdown == null) {
            markdown = node.get("module_requirement_markdown");
        }

        return markdown == null ? "" : String.valueOf(markdown);
    }

    private boolean a9155ContainsDirectGeneratedFeatureOrPage(List<?> children) {
        for (Object raw : children) {
            Map<String, Object> child = toMap(raw);
            String type = normalizeModuleType(readString(child, "moduleType", "type", "nodeType"));
            if ("FEATURE".equals(type) || "PAGE".equals(type)) {
                return true;
            }
        }
        return false;
    }

    // A9.10.14.4.3.15.4.15.5.4.1_PRD_SOURCED_FULL_TREE_JAVA
    private void a91554AddPageRequirementsFromMarkdown(
            List<Map<String, Object>> result,
            Map<String, String> sections
    ) {
        String text = a9155FirstNonBlank(
                sections.get("页面规划"),
                sections.get("页面方向"),
                sections.get("页面与操作设计"),
                sections.get("页面需求")
        );
        int index = result.size() + 1;
        for (String item : a9155BulletItems(text)) {
            String title = item.split("[:：-]", 2)[0].trim();
            if (title.isBlank()) {
                continue;
            }
            String nodeKey = a91553StableKey("page_", title, index);
            String pageKind = a91553InferPageType(title);
            String entity = a91553InferEntityFromName(title);
            Map<String, Object> row = a91553BaseRecord(
                    "PG-" + a9155SanitizeTraceKey(nodeKey),
                    title,
                    item
            );
            row.put("page_id", "PG-" + a9155SanitizeTraceKey(nodeKey));
            row.put("page_key", nodeKey);
            row.put("title", title);
            row.put("name", title);
            row.put("page_name", title);
            row.put("page_type", pageKind);
            row.put("route_path", "/" + a9155SanitizeTraceKey(nodeKey).replace('_', '-'));
            row.put("layout_type", a91553InferLayoutType(pageKind, title));
            row.put("primary_entity", entity);
            row.put("purpose", a91553PageDescription(title));
            row.put("main_actions", a91553InferPageActions(title, pageKind));
            row.put("filter_fields", a91553InferFilterFields(title, entity));
            row.put("table_columns", a91553InferTableColumns(title, entity));
            row.put("form_fields", a91553InferFormFields(title, entity));
            row.put("detail_sections", a91553InferDetailSections(title, entity));
            row.put("navigation_targets", a91553ListOf());
            row.put("treeNodeKey", nodeKey);
            row.put("tree_node_key", nodeKey);
            row.put("targetNodeType", "PAGE");
            row.put("target_node_type", "PAGE");
            result.add(row);
            index++;
        }
    }

    private String a9155MarkdownSection(String markdown, String heading) {
        return a91553ParseMarkdownSections(markdown).getOrDefault(heading, "");
    }

    private List<Map<String, Object>> a9155BulletRecords(
            String text,
            String titleKey,
            String descriptionKey
    ) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (String item : a9155BulletItems(text)) {
            Map<String, Object> record = a91553BaseRecord("REC-" + records.size(), item, item);
            String[] parts = item.split("[:：]", 2);
            record.put(titleKey, parts.length > 0 ? parts[0].trim() : item);
            record.put(descriptionKey, parts.length > 1 ? parts[1].trim() : item);
            records.add(record);
        }
        return records;
    }

    // A9.10.14.4.3.15.4.15.5.3_HOTFIX_BULLET_ITEMS_MUTABLE_EMPTY
    private List<String> a9155BulletItems(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String cleaned = line == null ? "" : line.trim();
            cleaned = cleaned.replaceFirst("^[-*•]\\s*", "").replaceFirst("^\\d+[.、]\\s*", "").trim();
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }

        return result;
    }

    private String a9155CompactText(String value) {
        return String.join("；", a9155BulletItems(value));
    }

    private String a9155FirstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String a9155SanitizeTraceKey(String value) {
        String result = value == null ? "" : value.trim();
        result = result.replaceAll("[^a-zA-Z0-9_\\-]", "_").replaceAll("_+", "_");
        return result.isBlank() ? "generated" : result;
    }

    private String a9155ResultHash(Object value) {
        return Integer.toHexString(String.valueOf(value).hashCode());
    }

    private Map<String, String> a91553ParseMarkdownSections(String markdown) {
        Map<String, String> result = new LinkedHashMap<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }
        String current = "";
        StringBuilder body = new StringBuilder();
        for (String raw : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.matches("^#{1,6}\\s+.*")) {
                if (!current.isBlank()) {
                    result.put(current, body.toString().trim());
                }
                current = trimmed.replaceFirst("^#{1,6}\\s+", "").trim();
                body = new StringBuilder();
                continue;
            }
            if (!current.isBlank()) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(raw);
            }
        }
        if (!current.isBlank()) {
            result.put(current, body.toString().trim());
        }
        return result;
    }

    private List<Map<String, Object>> a91553CollectNodesByType(List<?> children, String targetType) {
        List<Map<String, Object>> result = new ArrayList<>();
        a91553CollectNodesByTypeRecursive(children, targetType, result);
        return result;
    }

    private void a91553CollectNodesByTypeRecursive(
            List<?> children,
            String targetType,
            List<Map<String, Object>> result
    ) {
        if (children == null) {
            return;
        }
        for (Object raw : children) {
            Map<String, Object> child = toMap(raw);
            String type = normalizeModuleType(readString(child, "moduleType", "type", "nodeType"));
            if (targetType.equals(type)) {
                result.add(child);
            }
            Object nested = child.get("children");
            if (nested instanceof List<?> nestedChildren) {
                a91553CollectNodesByTypeRecursive(nestedChildren, targetType, result);
            }
        }
    }

    private Map<String, Object> a91553BaseRecord(String id, String title, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id == null ? "" : id);
        row.put("title", title == null ? "" : title.trim());
        row.put("name", title == null ? "" : title.trim());
        row.put("description", description == null ? "" : description.trim());
        row.put("sourceType", "AI_COMPLETED");
        row.put("source_type", "AI_COMPLETED");
        row.put("confirmationStatus", "PENDING_CONFIRMATION");
        row.put("confirmation_status", "PENDING_CONFIRMATION");
        row.put("sourceEvidence", "MODULE_TREE_FULL_BLUEPRINT");
        row.put("source_evidence", "MODULE_TREE_FULL_BLUEPRINT");
        return row;
    }

    private List<String> a91553ListOf(String... values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private void a91553AddIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value.trim());
        }
    }

    private void a91553AddUnique(List<String> list, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim();
        for (String existing : list) {
            if (existing.equals(normalized)) {
                return;
            }
        }
        list.add(normalized);
    }

    private String a91553CompactParagraphs(List<String> parts) {
        List<String> compact = new ArrayList<>();
        for (String part : parts) {
            String text = a91553CompactText(part);
            if (!text.isBlank()) {
                compact.add(text);
            }
        }
        return String.join("\n\n", compact);
    }

    private String a91553CompactText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        List<String> items = a9155BulletItems(value);
        if (!items.isEmpty()) {
            return String.join("；", items);
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String a91553StableKey(String prefix, String name, int index) {
        String sanitized = a9155SanitizeTraceKey(name);
        if (sanitized.isBlank() || "generated".equals(sanitized)) {
            sanitized = String.valueOf(index);
        }
        return prefix + sanitized.toLowerCase(Locale.ROOT);
    }

    private String a91553FunctionalDescription(String name, List<Map<String, Object>> pages) {
        List<String> related = a91553RelatedPages(name, pages);
        if (related.isEmpty()) {
            return "支持" + name + "相关业务处理，并与当前模块的状态、权限和数据对象保持一致。";
        }
        return "支持" + name + "相关业务处理；关联页面：" + String.join("、", related) + "。";
    }

    private List<String> a91553RelatedPages(String featureName, List<Map<String, Object>> pages) {
        List<String> result = new ArrayList<>();
        String normalizedFeature = featureName == null ? "" : featureName.replace("管理", "").replace("查询", "").replace("查看", "");
        for (Map<String, Object> page : pages) {
            String pageName = readString(page, "page_name", "title", "name");
            if (pageName.isBlank()) {
                continue;
            }
            if (normalizedFeature.isBlank() || pageName.contains(normalizedFeature) || normalizedFeature.contains(pageName.replace("页", "").replace("页面", "").replace("弹窗", ""))) {
                result.add(pageName);
            }
        }
        if (result.isEmpty() && pages.size() == 1) {
            result.add(readString(pages.get(0), "page_name", "title", "name"));
        }
        return result;
    }

    private String a91553InferPageType(String title) {
        String name = title == null ? "" : title;
        if (name.contains("列表") || name.contains("查询")) return "LIST_PAGE";
        if (name.contains("详情")) return "DETAIL_PAGE";
        if (name.contains("新建") || name.contains("创建") || name.contains("编辑") || name.contains("登记") || name.contains("更新") || name.contains("弹窗")) return "FORM_PAGE";
        if (name.contains("看板") || name.contains("统计")) return "DASHBOARD_PAGE";
        return "PAGE";
    }

    private String a91553InferLayoutType(String pageKind, String title) {
        String kind = pageKind == null ? "" : pageKind;
        if (kind.contains("LIST")) return "table-list";
        if (kind.contains("DETAIL")) return "detail-tabs";
        if (kind.contains("FORM")) return "form-detail";
        if (kind.contains("DASHBOARD")) return "dashboard";
        return "business-page";
    }

    private String a91553InferEntityFromName(String title) {
        String name = title == null ? "" : title;
        if (name.contains("出口订单") || name.contains("订单")) return "ExportOrder";
        if (name.contains("询盘")) return "Inquiry";
        if (name.contains("报价")) return "Quotation";
        if (name.contains("客户")) return "Customer";
        if (name.contains("商品")) return "Product";
        if (name.contains("合同")) return "Contract";
        if (name.contains("采购")) return "PurchaseOrder";
        if (name.contains("库存") || name.contains("入库") || name.contains("出库")) return "Inventory";
        if (name.contains("收款") || name.contains("应收") || name.contains("核销")) return "Receivable";
        return "BusinessObject";
    }

    private String a91553PageDescription(String title) {
        String name = title == null ? "当前页面" : title;
        if (name.contains("列表") || name.contains("查询")) return "集中展示和筛选" + name.replace("页", "") + "相关记录，支持进入详情或发起业务操作。";
        if (name.contains("详情")) return "查看业务对象详情、状态、关联信息和操作历史。";
        if (name.contains("新建") || name.contains("创建")) return "录入并提交新的业务记录，完成字段校验和保存。";
        if (name.contains("更新") || name.contains("弹窗")) return "在当前业务上下文中完成局部状态、异常或补充信息处理。";
        return "支撑当前模块业务处理的页面入口。";
    }

    private List<String> a91553InferPageActions(String title, String pageKind) {
        String name = title == null ? "" : title;
        if (name.contains("列表") || name.contains("查询")) return a91553ListOf("查询", "筛选", "重置", "查看详情", "新建");
        if (name.contains("详情")) return a91553ListOf("返回列表", "编辑", "状态更新", "查看操作日志");
        if (name.contains("新建") || name.contains("创建") || name.contains("登记")) return a91553ListOf("保存草稿", "提交", "取消");
        if (name.contains("更新")) return a91553ListOf("确认更新", "取消", "记录备注");
        if (name.contains("异常")) return a91553ListOf("新增异常", "提交异常", "关闭");
        return a91553ListOf("查看", "保存", "提交");
    }

    private List<String> a91553InferFilterFields(String title, String entity) {
        if (title != null && (title.contains("列表") || title.contains("查询"))) {
            return a91553ListOf("关键词", "状态", "负责人", "日期范围");
        }
        return List.of();
    }

    private List<String> a91553InferTableColumns(String title, String entity) {
        if (title == null || (!title.contains("列表") && !title.contains("查询"))) {
            return List.of();
        }
        if ("ExportOrder".equals(entity)) return a91553ListOf("订单编号", "客户", "销售负责人", "订单日期", "币种", "订单金额", "订单状态", "预计交期", "操作");
        if ("Inquiry".equals(entity)) return a91553ListOf("询盘编号", "客户", "销售负责人", "询盘日期", "询盘状态", "最近跟进", "操作");
        if ("Quotation".equals(entity)) return a91553ListOf("报价单号", "客户", "关联询盘", "报价金额", "报价状态", "有效期", "操作");
        return a91553ListOf("编号", "名称", "状态", "负责人", "更新时间", "操作");
    }

    private List<String> a91553InferFormFields(String title, String entity) {
        if (title == null || !(title.contains("新建") || title.contains("创建") || title.contains("编辑") || title.contains("登记") || title.contains("更新") || title.contains("异常") || title.contains("弹窗"))) {
            return List.of();
        }
        return a91553InferImportantFields(title, entity, entity);
    }

    private List<String> a91553InferDetailSections(String title, String entity) {
        if (title != null && title.contains("详情")) {
            return a91553ListOf("基础信息", "明细信息", "状态记录", "关联单据", "操作日志");
        }
        return List.of();
    }

    private List<String> a91553InferNavigationTargets(String title, List<Map<String, Object>> pageNodes) {
        List<String> result = new ArrayList<>();
        String name = title == null ? "" : title;
        for (Map<String, Object> node : pageNodes) {
            String pageName = readString(node, "moduleName", "name", "title");
            if (!pageName.isBlank() && !pageName.equals(name) && result.size() < 4) {
                result.add(pageName);
            }
        }
        return result;
    }

    private List<String> a91553InferImportantFields(String moduleName, String entityName, String entityKey) {
        String seed = (moduleName == null ? "" : moduleName) + " " + (entityName == null ? "" : entityName) + " " + (entityKey == null ? "" : entityKey);
        if (seed.contains("出口订单") || seed.contains("ExportOrder") || seed.contains("订单")) {
            return a91553ListOf("订单编号", "客户", "关联询盘", "关联合同", "销售负责人", "订单日期", "币种", "订单金额", "贸易条款", "付款条款", "预计交期", "目的港", "收货人信息", "订单状态", "商品明细", "数量", "单价", "金额", "交付状态", "备注");
        }
        if (seed.contains("询盘") || seed.contains("Inquiry")) {
            return a91553ListOf("询盘编号", "客户", "联系人", "销售负责人", "询盘日期", "需求描述", "目的港", "目标价格", "询盘状态", "备注");
        }
        if (seed.contains("报价") || seed.contains("Quotation")) {
            return a91553ListOf("报价单号", "客户", "关联询盘", "销售负责人", "报价日期", "币种", "报价金额", "有效期", "报价状态", "报价明细", "备注");
        }
        if (seed.contains("客户") || seed.contains("Customer")) {
            return a91553ListOf("客户编号", "客户名称", "联系人", "联系方式", "国家/地区", "客户状态", "备注");
        }
        return a91553ListOf("编号", "名称", "状态", "负责人", "创建时间", "更新时间", "备注");
    }

    private List<String> a91553InferRelationships(String entityName, List<Map<String, Object>> pages) {
        String name = entityName == null ? "" : entityName;
        if (name.contains("订单")) return a91553ListOf("关联客户", "关联询盘", "关联合同", "关联商品明细", "关联异常记录", "关联操作日志");
        if (name.contains("询盘")) return a91553ListOf("关联客户", "可转化为报价或订单");
        if (name.contains("报价")) return a91553ListOf("关联询盘", "可转化为出口订单");
        return a91553ListOf("与当前模块页面和操作记录关联");
    }

    private String a91553RuleTitle(String text, int index) {
        if (text == null || text.isBlank()) {
            return "规则" + index;
        }
        String value = text.trim();
        int split = value.indexOf('：');
        if (split < 0) split = value.indexOf(':');
        if (split > 0) {
            return value.substring(0, split).trim();
        }
        return value.length() > 20 ? value.substring(0, 20) : value;
    }

    private List<String> a91553SplitByComma(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String item : text.split("[、,，；;]")) {
            if (item != null && !item.trim().isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private List<String> a91553InferRolesFromText(String text, List<Map<String, Object>> roles) {
        List<String> result = new ArrayList<>();
        String source = text == null ? "" : text;
        for (Map<String, Object> role : roles) {
            String roleName = readString(role, "role_name", "title", "name");
            if (!roleName.isBlank() && source.contains(roleName)) {
                result.add(roleName);
            }
        }
        if (result.isEmpty()) {
            for (Map<String, Object> role : roles) {
                String roleName = readString(role, "role_name", "title", "name");
                if (!roleName.isBlank()) {
                    result.add(roleName);
                }
            }
        }
        return result;
    }

    private List<String> a91553InferActionsFromText(String text) {
        String source = text == null ? "" : text;
        List<String> result = new ArrayList<>();
        if (source.contains("创建") || source.contains("新建")) result.add("创建");
        if (source.contains("编辑") || source.contains("维护")) result.add("编辑");
        if (source.contains("查看") || source.contains("查询")) result.add("查看");
        if (source.contains("确认") || source.contains("审核")) result.add("确认/审核");
        if (source.contains("更新")) result.add("更新状态");
        if (source.contains("异常")) result.add("记录异常");
        if (result.isEmpty()) result.add("按角色职责操作");
        return result;
    }

    private String a91553BuildSyntheticModulePrdMarkdown(Map<String, Object> content, String originalMarkdown) {
        List<String> lines = new ArrayList<>();
        Map<String, Object> basic = toMap(content.get("module_basic_info"));
        lines.add("# " + a9155FirstNonBlank(readString(basic, "module_name"), "模块需求"));
        lines.add("");
        lines.add("## 业务背景");
        lines.add(String.valueOf(content.getOrDefault("business_background", "")));
        lines.add("");
        a91553AppendMarkdownArray(lines, "用户角色", content.get("user_roles"), "role_name", "responsibility");
        a91553AppendMarkdownArray(lines, "功能需求", content.get("functional_requirements"), "title", "description");
        a91553AppendMarkdownArray(lines, "业务规则", content.get("business_rules"), "rule_name", "description");
        a91553AppendMarkdownArray(lines, "状态与流转", content.get("state_rules"), "state_name", "description");
        a91553AppendMarkdownArray(lines, "页面需求与原型契约", content.get("page_requirements"), "page_name", "description");
        a91553AppendMarkdownArray(lines, "数据需求", content.get("data_requirements"), "entity_name", "description");
        a91553AppendMarkdownArray(lines, "权限需求", content.get("permission_requirements"), "permission_name", "description");
        a91553AppendMarkdownArray(lines, "异常场景", content.get("exception_scenarios"), "scenario_name", "handling_strategy");
        a91553AppendMarkdownArray(lines, "验收标准", content.get("acceptance_criteria"), "criterion_name", "expected_result");
        lines.add("");
        lines.add("## 原始模块需求片段");
        lines.add(originalMarkdown == null ? "" : originalMarkdown.trim());
        return String.join("\n", lines).trim();
    }

    private void a91553AppendMarkdownArray(
            List<String> lines,
            String title,
            Object value,
            String titleKey,
            String descriptionKey
    ) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        lines.add("");
        lines.add("## " + title);
        for (Object raw : list) {
            Map<String, Object> item = toMap(raw);
            String name = a9155FirstNonBlank(readString(item, titleKey), readString(item, "title", "name"));
            String description = a9155FirstNonBlank(readString(item, descriptionKey), readString(item, "description"));
            lines.add("- " + name + (description.isBlank() ? "" : "：" + description));
        }
    }

    private List<?> extractModules(Map<String, Object> artifactContent) {
        Object modules = artifactContent.get("modules");
        if (modules instanceof List<?> list) {
            return list;
        }

        Object data = artifactContent.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object dataModules = dataMap.get("modules");
            if (dataModules instanceof List<?> list) {
                return list;
            }
        }

        Object rootModules = artifactContent.get("rootModules");
        if (rootModules instanceof List<?> list) {
            return list;
        }

        Object content = artifactContent.get("content");
        if (content instanceof Map<?, ?> contentMap) {
            Object contentModules = contentMap.get("modules");
            if (contentModules instanceof List<?> list) {
                return list;
            }
        }

        return List.of();
    }

    private String normalizeModuleKey(
            String rawModuleKey,
            String modulePath,
            Map<String, Integer> usedModuleKeys
    ) {
        String baseKey;

        if (rawModuleKey != null && !rawModuleKey.isBlank()) {
            baseKey = rawModuleKey.trim();
        } else {
            baseKey = "mod_" + Integer.toHexString(modulePath.hashCode());
        }

        baseKey = baseKey
                .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                .replaceAll("_+", "_");

        if (baseKey.isBlank()) {
            baseKey = "mod_" + Integer.toHexString(modulePath.hashCode());
        }

        int count = usedModuleKeys.getOrDefault(baseKey, 0);
        usedModuleKeys.put(baseKey, count + 1);

        if (count == 0) {
            return baseKey;
        }

        return baseKey + "_" + count;
    }

    private Map<String, Object> extractArtifactContent(
            Map<String, Object> responseEnvelope
    ) {
        Object content = responseEnvelope.get("content");

        if (content instanceof Map<?, ?> contentMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : contentMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        return responseEnvelope;
    }

    private String extractMarkdown(
            Map<String, Object> responseEnvelope,
            Map<String, Object> artifactContent
    ) {
        Object contentMarkdown = responseEnvelope.get("content_markdown");
        if (contentMarkdown != null) {
            return String.valueOf(contentMarkdown);
        }

        Object markdown = responseEnvelope.get("markdown");
        if (markdown != null) {
            return String.valueOf(markdown);
        }

        Object artifactMarkdown = artifactContent.get("markdown");
        if (artifactMarkdown != null) {
            return String.valueOf(artifactMarkdown);
        }

        Object artifactContentMarkdown = artifactContent.get("content_markdown");
        if (artifactContentMarkdown != null) {
            return String.valueOf(artifactContentMarkdown);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object object) {
        if (object instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        return objectMapper.convertValue(object, Map.class);
    }

    private String readString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String readStringOrDefault(
            Map<String, Object> map,
            String key,
            String defaultValue
    ) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }


    private String resolveGeneratedRequirementMode(
            String moduleType,
            Map<String, Object> node
    ) {
        String normalizedType = normalizeModuleType(moduleType);

        if ("FEATURE".equals(normalizedType)) {
            return "FUNCTION_POINT";
        }

        if ("PAGE".equals(normalizedType)) {
            return "PAGE_CONTRACT";
        }

        if (!"MODULE".equals(normalizedType)) {
            return normalizedType;
        }

        Object explicit = node.get("requirementMode");
        if (explicit == null) {
            explicit = node.get("requirement_mode");
        }

        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return String.valueOf(explicit).trim();
        }

        boolean hasChildModule = false;
        boolean hasFeatureOrPage = false;

        Object childrenObj = node.get("children");
        if (childrenObj instanceof List<?> children) {
            for (Object childObj : children) {
                Map<String, Object> child = toMap(childObj);
                String childType = normalizeModuleType(
                        readString(child, "moduleType", "type", "nodeType")
                );

                if ("MODULE".equals(childType)) {
                    hasChildModule = true;
                }

                if ("FEATURE".equals(childType) || "PAGE".equals(childType)) {
                    hasFeatureOrPage = true;
                }
            }
        }

        if (hasChildModule) {
            return "GROUP_MODULE";
        }

        if (hasFeatureOrPage) {
            return "REQUIREMENT_MODULE";
        }

        return "DRAFT_MODULE";
    }

    private String normalizeModuleType(
            String rawType
    ) {
        if (rawType == null || rawType.isBlank()) {
            return "MODULE";
        }

        String value = rawType.trim().toUpperCase(Locale.ROOT);

        return switch (value) {
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> "MODULE";
            case "MODULE", "BUSINESS_MODULE", "FEATURE_MODULE", "INTEGRATION_MODULE", "INFRA_MODULE" -> "MODULE";
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> "MODULE";
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION" -> "FEATURE";
            default -> "MODULE";
        };
    }
}