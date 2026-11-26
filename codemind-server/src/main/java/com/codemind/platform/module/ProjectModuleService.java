package com.codemind.platform.module;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProjectModuleService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";

    private final ProjectModuleRepository projectModuleRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CurrentUserProvider currentUserProvider;
    private final ProjectModuleImpactService projectModuleImpactService;


    public ProjectModuleService(
            ProjectModuleRepository projectModuleRepository,
            NamedParameterJdbcTemplate jdbcTemplate,
            CurrentUserProvider currentUserProvider,
            ProjectModuleImpactService projectModuleImpactService
    ) {
        this.projectModuleRepository = projectModuleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserProvider = currentUserProvider;
        this.projectModuleImpactService = projectModuleImpactService;
    }

    public List<ModuleTreeNode> getModuleTreeByProjectCode(String projectCode) {
        UUID projectId = resolveAccessibleProjectByProjectCode(projectCode);
        return buildModuleTree(projectId);
    }

    public List<ModuleTreeNode> getModuleTreeByWorkspaceAndProjectCode(
            UUID workspaceId,
            String projectCode
    ) {
        UUID projectId = resolveAccessibleProjectByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        return buildModuleTree(projectId);
    }

    @Transactional
    public ProjectModuleMutationResponse createModuleNode(
            UUID workspaceId,
            String projectCode,
            CreateProjectModuleNodeRequest request
    ) {
        UUID projectId = resolveAccessibleProjectByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        UUID parentId = request == null ? null : request.getParentId();
        ProjectModuleEntity parent = null;

        if (parentId != null) {
            parent = getActiveModuleOrThrow(projectId, parentId);
        }

        String moduleName = normalizeRequiredName(
                request == null ? null : request.getModuleName()
        );

        String requestedModuleType = request == null ? null : request.getModuleType();
        String moduleType = normalizeModuleType(requestedModuleType);
        moduleType = normalizeCreateNodeTypeForRetiredFeatureLayer(
                requestedModuleType,
                moduleType,
                moduleName,
                parent,
                request == null ? null : request.getExtra()
        );

        assertAllowedChildType(parent, moduleType);

        if (parent != null
                && "MODULE".equals(normalizeModuleType(parent.getModuleType()))
                && ("FEATURE".equals(moduleType) || "PAGE".equals(moduleType))) {
            markParentAsRequirementModule(parent, currentUser.username());
        }

        int levelNo = parent == null ? 1 : parent.getLevelNo() + 1;
        String modulePath = parent == null
                ? moduleName
                : parent.getModulePath() + "/" + moduleName;

        Map<String, Integer> usedKeys = loadUsedModuleKeys(projectId);

        String moduleKey = normalizeModuleKey(
                request == null ? null : request.getModuleKey(),
                modulePath,
                usedKeys
        );

        int sortOrder = request != null && request.getSortOrder() != null
                ? request.getSortOrder()
                : nextSortOrder(projectId, parentId);

        Map<String, Object> extra = normalizeExtra(
                request == null ? null : request.getExtra(),
                moduleType
        );

        LocalDateTime now = LocalDateTime.now();

        ProjectModuleEntity entity = new ProjectModuleEntity();
        entity.setProjectId(projectId);
        entity.setParentId(parentId);
        entity.setModuleKey(moduleKey);
        entity.setModuleName(moduleName);
        entity.setModuleType(moduleType);
        entity.setModulePath(modulePath);
        entity.setLevelNo(levelNo);
        entity.setSortOrder(sortOrder);
        entity.setStatus(ACTIVE);
        entity.setExtra(extra);
        entity.setCreatedBy(currentUser.username());
        entity.setUpdatedBy(currentUser.username());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        ProjectModuleEntity saved = projectModuleRepository.save(entity);
        projectModuleImpactService.markImpactedArtifactsAfterCreate(
                projectId,
                saved
        );
        projectModuleRepository.flush();

        return new ProjectModuleMutationResponse(
                toNode(saved),
                buildModuleTree(projectId)
        );
    }

    @Transactional
    public ProjectModuleMutationResponse updateModuleNode(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            UpdateProjectModuleNodeRequest request
    ) {
        UUID projectId = resolveAccessibleProjectByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ProjectModuleEntity entity = getActiveModuleOrThrow(projectId, moduleId);

        boolean nameChanged = false;
        boolean typeChanged = false;

        if (request != null && hasText(request.getModuleName())) {
            String newName = request.getModuleName().trim();

            if (!newName.equals(entity.getModuleName())) {
                entity.setModuleName(newName);
                nameChanged = true;
            }
        }

        if (request != null && hasText(request.getModuleKey())) {
            entity.setModuleKey(
                    sanitizeModuleKey(request.getModuleKey().trim())
            );
        }

        if (request != null && hasText(request.getModuleType())) {
            String newType = normalizeModuleType(request.getModuleType());

            if (!newType.equals(entity.getModuleType())) {
                ProjectModuleEntity parent = entity.getParentId() == null
                        ? null
                        : getActiveModuleOrThrow(projectId, entity.getParentId());

                assertAllowedChildType(parent, newType);

                entity.setModuleType(newType);
                typeChanged = true;
            }
        }

        if (request != null && request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }

        if (request != null && request.getExtra() != null) {
            entity.setExtra(
                    normalizeExtra(
                            request.getExtra(),
                            entity.getModuleType()
                    )
            );
        } else if (typeChanged) {
            entity.setExtra(
                    normalizeExtra(
                            entity.getExtra(),
                            entity.getModuleType()
                    )
            );
        }

        if (nameChanged) {
            String parentPath = "";

            if (entity.getParentId() != null) {
                ProjectModuleEntity parent = getActiveModuleOrThrow(
                        projectId,
                        entity.getParentId()
                );
                parentPath = parent.getModulePath();
            }

            entity.setModulePath(
                    parentPath.isBlank()
                            ? entity.getModuleName()
                            : parentPath + "/" + entity.getModuleName()
            );
        }

        entity.setUpdatedBy(currentUser.username());
        entity.setUpdatedAt(LocalDateTime.now());

        ProjectModuleEntity saved = projectModuleRepository.save(entity);

        if (nameChanged) {
            refreshDescendantPaths(projectId, saved);
        }

        projectModuleImpactService.markImpactedArtifactsAfterUpdate(
                projectId,
                saved
        );

        projectModuleRepository.flush();

        return new ProjectModuleMutationResponse(
                toNode(saved),
                buildModuleTree(projectId)
        );
    }

    @Transactional
    public ProjectModuleMutationResponse moveModuleNode(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            MoveProjectModuleNodeRequest request
    ) {
        UUID projectId = resolveAccessibleProjectByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ProjectModuleEntity entity = getActiveModuleOrThrow(projectId, moduleId);
        UUID oldParentId = entity.getParentId();

        UUID newParentId = request == null ? null : request.getParentId();
        ProjectModuleEntity newParent = null;

        if (newParentId != null) {
            newParent = getActiveModuleOrThrow(projectId, newParentId);

            if (moduleId.equals(newParentId)) {
                throw new IllegalArgumentException("节点不能移动到自身下面。");
            }

            if (isDescendant(projectId, newParentId, moduleId)) {
                throw new IllegalArgumentException("节点不能移动到自己的子节点下面。");
            }
        }

        assertAllowedChildType(newParent, entity.getModuleType());

        if (newParent != null
                && "MODULE".equals(normalizeModuleType(newParent.getModuleType()))
                && Set.of("FEATURE", "PAGE").contains(normalizeModuleType(entity.getModuleType()))) {
            markParentAsRequirementModule(newParent, currentUser.username());
        }

        entity.setParentId(newParentId);
        entity.setLevelNo(newParent == null ? 1 : newParent.getLevelNo() + 1);
        entity.setSortOrder(
                request != null && request.getSortOrder() != null
                        ? request.getSortOrder()
                        : nextSortOrder(projectId, newParentId)
        );

        entity.setModulePath(
                newParent == null
                        ? entity.getModuleName()
                        : newParent.getModulePath() + "/" + entity.getModuleName()
        );

        entity.setUpdatedBy(currentUser.username());
        entity.setUpdatedAt(LocalDateTime.now());

        ProjectModuleEntity saved = projectModuleRepository.save(entity);

        refreshDescendantPaths(projectId, saved);

        projectModuleImpactService.markImpactedArtifactsAfterMove(
                projectId,
                saved,
                oldParentId,
                newParentId
        );

        projectModuleRepository.flush();

        return new ProjectModuleMutationResponse(
                toNode(saved),
                buildModuleTree(projectId)
        );
    }

    @Transactional
    public ProjectModuleMutationResponse deleteModuleNode(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        UUID projectId = resolveAccessibleProjectByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ProjectModuleEntity entity = getActiveModuleOrThrow(projectId, moduleId);

        projectModuleImpactService.markImpactedArtifactsAfterDelete(
                projectId,
                entity
        );

        softDeleteRecursive(
                projectId,
                entity,
                currentUser.username(),
                LocalDateTime.now()
        );

        projectModuleRepository.flush();

        return new ProjectModuleMutationResponse(
                toNode(entity),
                buildModuleTree(projectId)
        );
    }

    private UUID resolveAccessibleProjectByProjectCode(String projectCode) {
        UUID userId = currentUserProvider.getCurrentUser().userId();

        String sql = """
                SELECT p.id
                FROM project p
                JOIN workspace_member wm
                    ON wm.workspace_id = p.workspace_id
                   AND wm.user_id = :userId
                   AND wm.status = 'ACTIVE'
                LEFT JOIN project_member pm
                    ON pm.project_id = p.id
                   AND pm.user_id = :userId
                   AND pm.status = 'ACTIVE'
                WHERE p.project_code = :projectCode
                  AND p.status = 'ACTIVE'
                  AND (
                        p.visibility = 'WORKSPACE'
                        OR pm.user_id IS NOT NULL
                  )
                ORDER BY p.updated_at DESC, p.created_at DESC
                LIMIT 1
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("projectCode", projectCode)
                            .addValue("userId", userId),
                    UUID.class
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Project not accessible: " + projectCode);
        }
    }

    private UUID resolveAccessibleProjectByWorkspaceAndProjectCode(
            UUID workspaceId,
            String projectCode
    ) {
        UUID userId = currentUserProvider.getCurrentUser().userId();

        String sql = """
                SELECT p.id
                FROM project p
                JOIN workspace_member wm
                    ON wm.workspace_id = p.workspace_id
                   AND wm.user_id = :userId
                   AND wm.status = 'ACTIVE'
                LEFT JOIN project_member pm
                    ON pm.project_id = p.id
                   AND pm.user_id = :userId
                   AND pm.status = 'ACTIVE'
                WHERE p.workspace_id = :workspaceId
                  AND p.project_code = :projectCode
                  AND p.status = 'ACTIVE'
                  AND (
                        p.visibility = 'WORKSPACE'
                        OR pm.user_id IS NOT NULL
                  )
                LIMIT 1
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("workspaceId", workspaceId)
                            .addValue("projectCode", projectCode)
                            .addValue("userId", userId),
                    UUID.class
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException(
                    "Project not accessible. workspaceId="
                            + workspaceId
                            + ", projectCode="
                            + projectCode
            );
        }
    }

    private List<ModuleTreeNode> buildModuleTree(UUID projectId) {
        List<ProjectModuleEntity> modules =
                projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                        .stream()
                        .filter(module -> ACTIVE.equals(module.getStatus()))
                        .toList();

        Map<UUID, ModuleTreeNode> nodeMap = new LinkedHashMap<>();
        List<ModuleTreeNode> roots = new ArrayList<>();

        for (ProjectModuleEntity entity : modules) {
            ModuleTreeNode node = toNode(entity);
            nodeMap.put(entity.getId(), node);
        }

        for (ModuleTreeNode node : nodeMap.values()) {
            UUID parentId = node.getParentId();

            if (parentId == null) {
                roots.add(node);
            } else {
                ModuleTreeNode parent = nodeMap.get(parentId);

                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }

        return roots;
    }

    private ProjectModuleEntity getActiveModuleOrThrow(
            UUID projectId,
            UUID moduleId
    ) {
        ProjectModuleEntity module = projectModuleRepository
                .findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("模块节点不存在。"));

        if (!projectId.equals(module.getProjectId())) {
            throw new IllegalArgumentException("模块节点不属于当前项目。");
        }

        if (!ACTIVE.equals(module.getStatus())) {
            throw new IllegalArgumentException("模块节点已删除或不可用。");
        }

        return module;
    }

    private ModuleTreeNode toNode(ProjectModuleEntity entity) {
        ModuleTreeNode node = new ModuleTreeNode();
        node.setId(entity.getId());
        node.setParentId(entity.getParentId());
        node.setModuleKey(entity.getModuleKey());
        node.setModuleName(entity.getModuleName());
        node.setModuleType(entity.getModuleType());
        node.setModulePath(entity.getModulePath());
        node.setLevelNo(entity.getLevelNo());
        node.setSortOrder(entity.getSortOrder());
        node.setStatus(entity.getStatus());

        Map<String, Object> extra = entity.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(entity.getExtra());

        String normalizedType = normalizeModuleType(entity.getModuleType());
        extra.put("moduleType", normalizedType);
        extra.put("type", normalizedType);

        if ("MODULE".equals(normalizedType)) {
            boolean hasModulePrd = hasCurrentModulePrdArtifact(entity);
            boolean finalRequirementModule = isRequirementModule(entity) || hasModulePrd;
            extra.put("hasModulePrd", hasModulePrd);
            extra.put("finalRequirementModule", finalRequirementModule);
            extra.put(
                    "allowedChildTypes",
                    finalRequirementModule ? List.of("PAGE") : List.of("MODULE", "PAGE")
            );
            extra.put(
                    "allowedChildCreateLabel",
                    finalRequirementModule ? "新增页面" : "新增下级节点"
            );
        } else if ("FEATURE".equals(normalizedType)) {
            extra.put("allowedChildTypes", List.of("PAGE"));
            extra.put("allowedChildCreateLabel", "新增页面");
        } else {
            extra.put("allowedChildTypes", List.of());
        }

        node.setExtra(extra);

        return node;
    }

    private void softDeleteRecursive(
            UUID projectId,
            ProjectModuleEntity entity,
            String operator,
            LocalDateTime now
    ) {
        List<ProjectModuleEntity> children = activeModules(projectId)
                .stream()
                .filter(item -> entity.getId().equals(item.getParentId()))
                .toList();

        String originalModuleKey = entity.getModuleKey();

        entity.setStatus(DELETED);
        entity.setModuleKey(buildDeletedModuleKey(entity));
        entity.setUpdatedBy(operator);
        entity.setUpdatedAt(now);

        Map<String, Object> extra = entity.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(entity.getExtra());

        extra.put("deletedAt", now.toString());
        extra.put("deletedBy", operator);
        extra.put("originalModuleKey", originalModuleKey);
        extra.put("moduleTreeDeleteMode", "SOFT_DELETE_RELEASE_KEY");

        entity.setExtra(extra);

        projectModuleRepository.save(entity);

        for (ProjectModuleEntity child : children) {
            softDeleteRecursive(projectId, child, operator, now);
        }
    }

    private void refreshDescendantPaths(
            UUID projectId,
            ProjectModuleEntity root
    ) {
        Map<UUID, List<ProjectModuleEntity>> childrenByParent = new LinkedHashMap<>();

        for (ProjectModuleEntity module : activeModules(projectId)) {
            if (module.getParentId() != null) {
                childrenByParent
                        .computeIfAbsent(module.getParentId(), ignored -> new ArrayList<>())
                        .add(module);
            }
        }

        refreshDescendantPathsRecursive(
                root,
                childrenByParent
        );
    }

    private void refreshDescendantPathsRecursive(
            ProjectModuleEntity parent,
            Map<UUID, List<ProjectModuleEntity>> childrenByParent
    ) {
        List<ProjectModuleEntity> children = childrenByParent
                .getOrDefault(parent.getId(), List.of());

        for (ProjectModuleEntity child : children) {
            child.setLevelNo(parent.getLevelNo() + 1);
            child.setModulePath(parent.getModulePath() + "/" + child.getModuleName());
            child.setUpdatedAt(LocalDateTime.now());

            projectModuleRepository.save(child);

            refreshDescendantPathsRecursive(
                    child,
                    childrenByParent
            );
        }
    }

    private boolean isDescendant(
            UUID projectId,
            UUID candidateNodeId,
            UUID ancestorId
    ) {
        Map<UUID, UUID> parentMap = new LinkedHashMap<>();

        for (ProjectModuleEntity module : activeModules(projectId)) {
            parentMap.put(
                    module.getId(),
                    module.getParentId()
            );
        }

        UUID current = candidateNodeId;

        while (current != null) {
            if (ancestorId.equals(current)) {
                return true;
            }

            current = parentMap.get(current);
        }

        return false;
    }

    private List<ProjectModuleEntity> activeModules(UUID projectId) {
        return projectModuleRepository
                .findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                .stream()
                .filter(module -> ACTIVE.equals(module.getStatus()))
                .toList();
    }

    private int nextSortOrder(
            UUID projectId,
            UUID parentId
    ) {
        return activeModules(projectId)
                .stream()
                .filter(module -> Objects.equals(parentId, module.getParentId()))
                .map(ProjectModuleEntity::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }


    // A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION
    // Explicit user-triggered completion from reviewed MODULE_PRD.
    // Do not call this from MODULE_PRD persistence. The user must click the visible
    // action after reviewing/saving the PRD, then FEATURE/PAGE children are materialized.
    @Transactional
    public Map<String, Object> completeRequirementModuleChildrenFromModulePrd(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            Map<String, Object> request
    ) {
        UUID projectId = resolveAccessibleProjectByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        Map<String, Object> payload = request == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request);

        Map<String, Object> modulePrdContent = asObjectMap(
                firstValue(
                        payload,
                        "modulePrdContent",
                        "module_prd_content",
                        "modulePrd",
                        "module_prd",
                        "content"
                )
        );

        if (modulePrdContent.isEmpty()
                && (
                payload.containsKey("functional_requirements")
                        || payload.containsKey("functionalRequirements")
                        || payload.containsKey("page_requirements")
                        || payload.containsKey("pageRequirements")
                        || payload.containsKey("markdown")
        )) {
            modulePrdContent = new LinkedHashMap<>(payload);
        }

        Map<String, Object> result = completeRequirementModuleChildrenFromModulePrd(
                projectId,
                moduleId,
                modulePrdContent,
                currentUserProvider.getCurrentUser().username()
        );

        result.put("tree", buildModuleTree(projectId));
        result.put("node", toNode(getActiveModuleOrThrow(projectId, moduleId)));
        result.put("manualUserAction", true);
        result.put("source", "A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION");
        return result;
    }

    @Transactional
        // A9.10.14.4.3.15.6.24L0B_NO_VISIBLE_FEATURE_ON_CREATE_OR_PRD_COMPLETION
    public Map<String, Object> completeRequirementModuleChildrenFromModulePrd(
            UUID projectId,
            UUID moduleId,
            Map<String, Object> modulePrdContent,
            String username
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "A9.10.14.4.3.15.6.24L0B_PAGE_DIRECT_COMPLETION");
        result.put("projectId", projectId == null ? "" : projectId.toString());
        result.put("moduleId", moduleId == null ? "" : moduleId.toString());
        result.put("createdFeatureCount", 0);
        result.put("updatedFeatureCount", 0);
        result.put("createdPageCount", 0);
        result.put("updatedPageCount", 0);

        if (projectId == null || moduleId == null) {
            result.put("skipped", true);
            result.put("reason", "MISSING_PROJECT_OR_MODULE_ID");
            return result;
        }

        ProjectModuleEntity module = getActiveModuleOrThrow(projectId, moduleId);
        String moduleType = normalizeModuleType(module.getModuleType());

        if (!"MODULE".equals(moduleType)) {
            result.put("skipped", true);
            result.put("reason", "SELECTED_NODE_IS_NOT_MODULE");
            return result;
        }

        List<Map<String, Object>> functionalRequirements = asMapList(
                firstValue(modulePrdContent, "functional_requirements", "functionalRequirements")
        );
        List<Map<String, Object>> pageRequirements = asMapList(
                firstValue(modulePrdContent, "page_requirements", "pageRequirements")
        );
        List<Map<String, Object>> structuredRequirements = asMapList(
                firstValue(modulePrdContent, "structured_requirements", "structuredRequirements")
        );

        Map<String, Map<String, Object>> prdTraceIndex = buildPrdRequirementTraceIndex(
                structuredRequirements,
                functionalRequirements,
                pageRequirements
        );

        if (pageRequirements.isEmpty()) {
            result.put("skipped", true);
            result.put("reason", functionalRequirements.isEmpty()
                    ? "MODULE_PRD_HAS_NO_FUNCTIONAL_OR_PAGE_REQUIREMENTS"
                    : "MODULE_PRD_FUNCTIONAL_REQUIREMENTS_HAVE_NO_PAGE_REQUIREMENTS_TO_MATERIALIZE");
            result.put("capabilityCount", functionalRequirements.size());
            result.put("featureCount", 0);
            result.put("pageCount", 0);
            return result;
        }

        List<ProjectModuleEntity> active = new ArrayList<>(activeModules(projectId));

        Map<UUID, ProjectModuleEntity> activeById = new LinkedHashMap<>();
        for (ProjectModuleEntity item : active) {
            if (item != null && item.getId() != null) {
                activeById.put(item.getId(), item);
            }
        }

        boolean hasChildModule = active.stream().anyMatch(item ->
                moduleId.equals(item.getParentId())
                        && "MODULE".equals(normalizeModuleType(item.getModuleType()))
        );

        if (hasChildModule) {
            result.put("skipped", true);
            result.put("reason", "MODULE_STILL_HAS_CHILD_MODULES");
            return result;
        }

        // A9.10.14.4.3.15.6.24L0B_FIX_USED_KEYS_MAP
        // createMaterializedNode / normalizeModuleKey expects key usage counts,
        // not a Set. Keep the original Map<String, Integer> contract.
        Map<String, Integer> usedKeys = new LinkedHashMap<>();
        for (ProjectModuleEntity item : active) {
            if (item == null || item.getModuleKey() == null) {
                continue;
            }

            String key = item.getModuleKey().trim();
            if (key.isBlank()) {
                continue;
            }

            usedKeys.merge(key, 1, Integer::sum);
        }

        Map<String, ProjectModuleEntity> pageIndex = new LinkedHashMap<>();

        for (ProjectModuleEntity item : active) {
            if (item == null || !"PAGE".equals(normalizeModuleType(item.getModuleType()))) {
                continue;
            }

            boolean directPage = moduleId.equals(item.getParentId());
            boolean legacyFeaturePage = false;

            ProjectModuleEntity parent = activeById.get(item.getParentId());
            if (parent != null
                    && "FEATURE".equals(normalizeModuleType(parent.getModuleType()))
                    && moduleId.equals(parent.getParentId())) {
                legacyFeaturePage = true;
            }

            if (directPage || legacyFeaturePage) {
                indexNode(pageIndex, item);
            }
        }

        int createdPages = 0;
        int updatedPages = 0;

        for (Map<String, Object> page : pageRequirements) {
            if (page == null || page.isEmpty()) {
                continue;
            }

            String rawPageName = text(
                    page.get("page_name"),
                    page.get("pageName"),
                    page.get("name"),
                    page.get("title")
            );

            String capabilityName = text(
                    page.get("parent_feature_name"),
                    page.get("parentFeatureName"),
                    page.get("feature_name"),
                    page.get("featureName"),
                    inferFeatureNameFromPage(page, module.getModuleName())
            );

            String pageName = hasText(rawPageName)
                    ? rawPageName
                    : (hasText(capabilityName) ? capabilityName + "页" : "页面");

            String pageKeySeed = text(page.get("page_key"), page.get("pageKey"), page.get("key"));
            if (!hasText(pageKeySeed)) {
                pageKeySeed = pageName;
            }

            String pageKey = semanticModuleKey(pageKeySeed, "page");

            String capabilityKey = semanticModuleKey(
                    text(
                            page.get("parent_feature_key"),
                            page.get("parentFeatureKey"),
                            page.get("feature_key"),
                            page.get("featureKey"),
                            capabilityName
                    ),
                    "feature"
            );

            ProjectModuleEntity existingPage = findIndexedNode(pageIndex, pageKey, pageName);

            Map<String, Object> requirementTrace = resolvePrdRequirementTrace(
                    prdTraceIndex,
                    "page_requirements",
                    page,
                    pageKey
            );

            Map<String, Object> pageExtra = buildPageExtra(
                    page,
                    pageKey,
                    pageName,
                    capabilityKey,
                    capabilityName,
                    requirementTrace
            );

            Map<String, Object> capabilityBinding = new LinkedHashMap<>();
            capabilityBinding.put("source", "MODULE_PRD_FUNCTIONAL_REQUIREMENT");
            capabilityBinding.put("featureKey", capabilityKey);
            capabilityBinding.put("featureName", capabilityName);
            capabilityBinding.put("featureLayerHiddenFromUser", true);

            pageExtra.put("featureLayerHiddenFromUser", true);
            pageExtra.put("capabilityBindings", List.of(capabilityBinding));
            pageExtra.put("relatedFeatureKeys", List.of(capabilityKey));
            pageExtra.put("parentFeatureKey", capabilityKey);
            pageExtra.put("parentFeatureName", capabilityName);
            pageExtra.put("ownerModuleId", moduleId.toString());
            pageExtra.put("ownerModuleKey", module.getModuleKey());
            pageExtra.put("ownerModuleName", module.getModuleName());

            if (existingPage == null) {
                ProjectModuleEntity createdPage = createMaterializedNode(
                        projectId,
                        moduleId,
                        module.getModulePath(),
                        module.getLevelNo() + 1,
                        pageName,
                        pageKey,
                        "PAGE",
                        nextSortOrder(projectId, moduleId),
                        pageExtra,
                        usedKeys,
                        username
                );
                createdPages++;
                active.add(createdPage);
                activeById.put(createdPage.getId(), createdPage);
                indexNode(pageIndex, createdPage);
                projectModuleImpactService.markImpactedArtifactsAfterCreate(projectId, createdPage);
            } else {
                mergeNodeExtra(existingPage, pageExtra, username);
                projectModuleRepository.save(existingPage);
                updatedPages++;
                indexNode(pageIndex, existingPage);
            }
        }

        Map<String, Object> moduleExtra = module.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(module.getExtra());

        moduleExtra.put("requirementMode", "REQUIREMENT_MODULE");
        moduleExtra.put("moduleMaterializedBy", "MODULE_PRD_MANUAL_COMPLETION_PAGE_DIRECT");
        moduleExtra.put("modulePrdTreeCompletionAt", LocalDateTime.now().toString());
        moduleExtra.put("modulePrdTreeCompletionSource", "A9.10.14.4.3.15.6.24L0B_NO_FEATURE_TREE_LAYER");
        moduleExtra.put("featureLayerMode", "MODULE_PRD_CAPABILITY_ONLY");
        moduleExtra.put("modulePrdTreeCompletion", Map.of(
                "createdFeatureCount", 0,
                "createdPageCount", createdPages,
                "updatedFeatureCount", 0,
                "updatedPageCount", updatedPages,
                "capabilityCount", functionalRequirements.size()
        ));

        module.setExtra(moduleExtra);
        module.setUpdatedBy(username);
        module.setUpdatedAt(LocalDateTime.now());
        projectModuleRepository.save(module);

        projectModuleRepository.flush();

        result.put("createdFeatureCount", 0);
        result.put("createdPageCount", createdPages);
        result.put("updatedFeatureCount", 0);
        result.put("updatedPageCount", updatedPages);
        result.put("capabilityCount", functionalRequirements.size());
        result.put("featureCount", 0);
        result.put("pageCount", pageIndex.size());
        result.put("traceEnhanced", true);
        result.put("traceSource", "MODULE_PRD_STRUCTURED_REQUIREMENTS");
        result.put("projectionQualityGate", "A9.10.14.4.3.15.6.24L0B");
        result.put("reason", "COMPLETED_FROM_REVIEWED_MODULE_PRD_BY_USER_ACTION_PAGE_DIRECT");
        return result;
    }









    // A9.10.14.4.3.15.4.15.3.1_PRD_FEATURE_PAGE_TRACE
    private Map<String, Map<String, Object>> buildPrdRequirementTraceIndex(
            List<Map<String, Object>> structuredRequirements,
            List<Map<String, Object>> functionalRequirements,
            List<Map<String, Object>> pageRequirements
    ) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();

        for (Map<String, Object> item : structuredRequirements) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            String section = text(item.get("sourceSection"), item.get("source_section"));
            String requirementKey = text(item.get("requirementKey"), item.get("requirement_key"));
            String requirementId = text(item.get("requirementId"), item.get("requirement_id"));
            String type = text(item.get("type"), item.get("requirementType"), item.get("requirement_type"));

            indexPrdTraceCandidate(index, section, requirementKey, item);
            indexPrdTraceCandidate(index, section, requirementId, item);

            if ("FEATURE".equalsIgnoreCase(type) || "FUNCTIONAL_REQUIREMENT".equalsIgnoreCase(type)) {
                indexPrdTraceCandidate(index, "functional_requirements", requirementKey, item);
                indexPrdTraceCandidate(index, "functional_requirements", requirementId, item);
            }

            if ("PAGE".equalsIgnoreCase(type) || "PAGE_REQUIREMENT".equalsIgnoreCase(type)) {
                indexPrdTraceCandidate(index, "page_requirements", requirementKey, item);
                indexPrdTraceCandidate(index, "page_requirements", requirementId, item);

                Object relatedPageKeys = firstValue(item, "relatedPageKeys", "related_page_keys");
                if (relatedPageKeys instanceof Iterable<?>) {
                    for (Object relatedPageKey : (Iterable<?>) relatedPageKeys) {
                        indexPrdTraceCandidate(index, "page_requirements", text(relatedPageKey), item);
                    }
                }
            }
        }

        for (Map<String, Object> item : functionalRequirements) {
            String key = text(
                    item.get("requirement_key"),
                    item.get("requirementKey"),
                    item.get("feature_key"),
                    item.get("featureKey"),
                    item.get("key"),
                    item.get("title"),
                    item.get("name")
            );
            indexPrdTraceCandidate(
                    index,
                    "functional_requirements",
                    key,
                    buildSyntheticPrdRequirementTrace("functional_requirements", item, key)
            );
        }

        for (Map<String, Object> item : pageRequirements) {
            String key = text(
                    item.get("page_key"),
                    item.get("pageKey"),
                    item.get("key"),
                    item.get("page_id"),
                    item.get("pageId"),
                    item.get("page_name"),
                    item.get("pageName"),
                    item.get("name"),
                    item.get("title")
            );
            indexPrdTraceCandidate(
                    index,
                    "page_requirements",
                    key,
                    buildSyntheticPrdRequirementTrace("page_requirements", item, key)
            );
        }

        return index;
    }

    private void indexPrdTraceCandidate(
            Map<String, Map<String, Object>> index,
            String section,
            String key,
            Map<String, Object> trace
    ) {
        String normalizedSection = normalizeTraceKey(section);
        String normalizedKey = normalizeTraceKey(key);

        if (!hasText(normalizedSection) || !hasText(normalizedKey) || trace == null || trace.isEmpty()) {
            return;
        }

        index.putIfAbsent(normalizedSection + "::" + normalizedKey, new LinkedHashMap<>(trace));
        index.putIfAbsent(normalizedKey, new LinkedHashMap<>(trace));
    }

    private Map<String, Object> resolvePrdRequirementTrace(
            Map<String, Map<String, Object>> index,
            String section,
            Map<String, Object> source,
            String fallbackKey
    ) {
        if (index == null || index.isEmpty()) {
            return Map.of();
        }

        List<String> keys = List.of(
                text(source.get("requirement_key"), source.get("requirementKey")),
                text(source.get("feature_key"), source.get("featureKey")),
                text(source.get("page_key"), source.get("pageKey")),
                text(source.get("page_id"), source.get("pageId")),
                text(source.get("key")),
                text(source.get("title")),
                text(source.get("page_name"), source.get("pageName"), source.get("name")),
                text(fallbackKey)
        );

        String normalizedSection = normalizeTraceKey(section);
        for (String key : keys) {
            String normalizedKey = normalizeTraceKey(key);
            if (!hasText(normalizedKey)) {
                continue;
            }

            Map<String, Object> bySection = index.get(normalizedSection + "::" + normalizedKey);
            if (bySection != null && !bySection.isEmpty()) {
                return new LinkedHashMap<>(bySection);
            }

            Map<String, Object> byKey = index.get(normalizedKey);
            if (byKey != null && !byKey.isEmpty()) {
                return new LinkedHashMap<>(byKey);
            }
        }

        return Map.of();
    }

    private Map<String, Object> buildSyntheticPrdRequirementTrace(
            String section,
            Map<String, Object> source,
            String key
    ) {
        String normalizedKey = hasText(key) ? key : "requirement";
        String title = text(
                source.get("title"),
                source.get("requirement_name"),
                source.get("requirementName"),
                source.get("feature_name"),
                source.get("featureName"),
                source.get("page_name"),
                source.get("pageName"),
                source.get("name"),
                normalizedKey
        );

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("requirementId", text(
                source.get("requirement_id"),
                source.get("requirementId"),
                "req_" + normalizeTraceKey(section) + "_" + normalizeTraceKey(normalizedKey)
        ));
        trace.put("requirementKey", normalizedKey);
        trace.put("title", title);
        trace.put("type", "functional_requirements".equals(section) ? "FUNCTIONAL_REQUIREMENT" : "PAGE_REQUIREMENT");
        trace.put("sourceSection", section);
        trace.put("sourceType", text(source.get("sourceType"), source.get("source_type"), "AI_GENERATED_FROM_MODULE_PRD"));
        trace.put("confirmationStatus", text(source.get("confirmationStatus"), source.get("confirmation_status"), "PENDING_CONFIRMATION"));
        trace.put("sourceEvidence", Map.of(
                "artifactType", "MODULE_PRD",
                "sourceSection", section,
                "sourceKey", normalizedKey,
                "title", title
        ));
        return trace;
    }

    private Map<String, Object> buildTreeMaterializationTrace(
            String section,
            Map<String, Object> source,
            String key,
            Map<String, Object> requirementTrace
    ) {
        Map<String, Object> trace = requirementTrace == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(requirementTrace);

        if (trace.isEmpty()) {
            trace = buildSyntheticPrdRequirementTrace(section, source, key);
        }

        String requirementKey = text(
                trace.get("requirementKey"),
                trace.get("requirement_key"),
                key
        );
        String requirementId = text(
                trace.get("requirementId"),
                trace.get("requirement_id"),
                "req_" + normalizeTraceKey(section) + "_" + normalizeTraceKey(requirementKey)
        );
        String title = text(
                trace.get("title"),
                source.get("title"),
                source.get("page_name"),
                source.get("pageName"),
                source.get("name"),
                requirementKey
        );
        String sourceType = text(
                trace.get("sourceType"),
                trace.get("source_type"),
                source.get("sourceType"),
                source.get("source_type"),
                "AI_GENERATED_FROM_MODULE_PRD"
        );
        String confirmationStatus = text(
                trace.get("confirmationStatus"),
                trace.get("confirmation_status"),
                source.get("confirmationStatus"),
                source.get("confirmation_status"),
                "CONFIRMED_FROM_REVIEWED_MODULE_PRD"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifactType", "MODULE_PRD");
        result.put("sourceSection", section);
        result.put("sourceRequirementId", requirementId);
        result.put("sourceRequirementKey", requirementKey);
        result.put("sourceRequirementTitle", title);
        result.put("sourceType", sourceType);
        result.put("confirmationStatus", confirmationStatus);
        result.put("sourceEvidence", firstValue(trace, "sourceEvidence", "source_evidence"));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requirementId", requirementId);
        snapshot.put("requirementKey", requirementKey);
        snapshot.put("title", title);
        snapshot.put("sourceSection", section);
        snapshot.put("sourceType", sourceType);
        snapshot.put("confirmationStatus", confirmationStatus);
        result.put("structuredRequirementSnapshot", snapshot);

        return result;
    }

    private void applyPrdTraceToTreeExtra(
            Map<String, Object> extra,
            Map<String, Object> prdTrace
    ) {
        if (extra == null || prdTrace == null || prdTrace.isEmpty()) {
            return;
        }

        extra.put("sourceRequirementId", prdTrace.get("sourceRequirementId"));
        extra.put("sourceRequirementKey", prdTrace.get("sourceRequirementKey"));
        extra.put("sourceRequirementTitle", prdTrace.get("sourceRequirementTitle"));
        extra.put("sourceType", prdTrace.get("sourceType"));
        extra.put("confirmationStatus", prdTrace.get("confirmationStatus"));
        extra.put("sourceEvidence", prdTrace.get("sourceEvidence"));
        extra.put("prdTrace", prdTrace);
    }

    private String normalizeTraceKey(String value) {
        String normalized = String.valueOf(value == null ? "" : value).trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^0-9a-zA-Z\\u4e00-\\u9fff_\\-]+", "_");
        normalized = normalized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return normalized;
    }


    private ProjectModuleEntity createMaterializedNode(
            UUID projectId,
            UUID parentId,
            String parentPath,
            Integer levelNo,
            String requestedName,
            String requestedKey,
            String requestedType,
            int requestedSortOrder,
            Map<String, Object> extra,
            Map<String, Integer> usedKeys,
            String operator
    ) {
        String moduleType = normalizeModuleType(requestedType);
        String moduleName = normalizeRequiredName(requestedName);
        String modulePath = hasText(parentPath) ? parentPath + "/" + moduleName : moduleName;
        String moduleKey = normalizeModuleKey(requestedKey, modulePath, usedKeys);

        ProjectModuleEntity entity = new ProjectModuleEntity();
        entity.setProjectId(projectId);
        entity.setParentId(parentId);
        entity.setModuleKey(moduleKey);
        entity.setModuleName(moduleName);
        entity.setModuleType(moduleType);
        entity.setModulePath(modulePath);
        entity.setLevelNo(levelNo == null ? 1 : levelNo);
        entity.setSortOrder(requestedSortOrder);
        entity.setStatus(ACTIVE);
        entity.setExtra(normalizeExtra(extra, moduleType));
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        return projectModuleRepository.save(entity);
    }

    private void mergeNodeExtra(
            ProjectModuleEntity entity,
            Map<String, Object> patch,
            String operator
    ) {
        Map<String, Object> next = entity.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(entity.getExtra());
        next.putAll(patch);
        entity.setExtra(normalizeExtra(next, entity.getModuleType()));
        entity.setUpdatedBy(operator);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private Map<String, Object> buildFeatureExtra(
            Map<String, Object> source,
            String featureKey,
            String featureName,
            Map<String, Object> requirementTrace
    ) {
        Map<String, Object> extra = new LinkedHashMap<>();
        String description = text(
                source.get("description"),
                source.get("summary"),
                source.get("input_description"),
                source.get("inputDescription"),
                source.get("output_description"),
                source.get("outputDescription")
        );

        Map<String, Object> functionContext = new LinkedHashMap<>();
        functionContext.put("source", "MODULE_PRD");
        functionContext.put("featureKey", featureKey);
        functionContext.put("featureName", featureName);
        functionContext.put("summary", hasText(description) ? description : "承载 " + featureName + " 的业务能力。");
        functionContext.put("hiddenFromUser", true);

        Map<String, Object> prdTrace = buildTreeMaterializationTrace(
                "functional_requirements",
                source,
                featureKey,
                requirementTrace
        );
        functionContext.put("sourceRequirementId", prdTrace.get("sourceRequirementId"));
        functionContext.put("sourceRequirementKey", prdTrace.get("sourceRequirementKey"));
        functionContext.put("sourceRequirementTitle", prdTrace.get("sourceRequirementTitle"));
        functionContext.put("sourceType", prdTrace.get("sourceType"));
        functionContext.put("confirmationStatus", prdTrace.get("confirmationStatus"));

        extra.put("requirementMode", "FUNCTION_POINT");
        extra.put("featureRole", "prd_functional_requirement");
        extra.put("source", "MODULE_PRD_TO_TREE_COMPLETION");
        extra.put("sourcePrdSection", "functional_requirements");
        extra.put("functionContext", functionContext);
        applyPrdTraceToTreeExtra(extra, prdTrace);
        return extra;
    }

    private Map<String, Object> buildPageExtra(
            Map<String, Object> source,
            String pageKey,
            String pageName,
            String parentFeatureKey,
            String parentFeatureName,
            Map<String, Object> requirementTrace
    ) {
        String pageKind = normalizePageKind(text(
                source.get("page_kind"),
                source.get("pageKind"),
                source.get("page_type"),
                source.get("pageType"),
                source.get("type"),
                pageName
        ));
        String routePath = text(
                source.get("route_path"),
                source.get("routePath"),
                source.get("route"),
                "/" + pageKey.replace("_", "-")
        );
        String layoutType = text(
                source.get("layout_type"),
                source.get("layoutType"),
                defaultLayoutType(pageKind)
        );
        String primaryEntity = text(
                source.get("primary_entity"),
                source.get("primaryEntity"),
                pageName.replace("页面", "").replace("页", "")
        );
        String description = text(
                source.get("description"),
                source.get("summary"),
                pageName + "用于承载 " + primaryEntity + " 的页面任务。"
        );

        Map<String, Object> pagePrototypeContext = new LinkedHashMap<>();
        pagePrototypeContext.put("source", "MODULE_PRD");
        pagePrototypeContext.put("sourcePhase", "MODULE_PRD_TO_TREE_COMPLETION");
        pagePrototypeContext.put("pageKey", pageKey);
        pagePrototypeContext.put("pageName", pageName);
        pagePrototypeContext.put("pageKind", pageKind);
        pagePrototypeContext.put("routePath", routePath);
        pagePrototypeContext.put("layoutType", layoutType);
        pagePrototypeContext.put("primaryEntity", primaryEntity);
        pagePrototypeContext.put("parentFeatureKey", parentFeatureKey);
        pagePrototypeContext.put("parentFeatureName", parentFeatureName);
        pagePrototypeContext.put("goal", description);
        pagePrototypeContext.put("coreFields", textList(
                firstValue(source, "form_fields", "formFields", "table_columns", "tableColumns", "filter_fields", "filterFields", "main_components", "mainComponents")
        ));
        pagePrototypeContext.put("mainActions", textList(
                firstValue(source, "primary_actions", "primaryActions", "row_actions", "rowActions", "user_actions", "userActions")
        ));
        pagePrototypeContext.put("hiddenFromUser", true);

        Map<String, Object> prdTrace = buildTreeMaterializationTrace(
                "page_requirements",
                source,
                pageKey,
                requirementTrace
        );
        pagePrototypeContext.put("sourceRequirementId", prdTrace.get("sourceRequirementId"));
        pagePrototypeContext.put("sourceRequirementKey", prdTrace.get("sourceRequirementKey"));
        pagePrototypeContext.put("sourceRequirementTitle", prdTrace.get("sourceRequirementTitle"));
        pagePrototypeContext.put("sourceType", prdTrace.get("sourceType"));
        pagePrototypeContext.put("confirmationStatus", prdTrace.get("confirmationStatus"));

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("requirementMode", "PAGE_CONTRACT");
        extra.put("source", "MODULE_PRD_TO_TREE_COMPLETION");
        extra.put("sourcePrdSection", "page_requirements");
        extra.put("canGenerateUiContract", true);
        extra.put("pageKind", pageKind);
        extra.put("routePath", routePath);
        extra.put("layoutType", layoutType);
        extra.put("primaryEntity", primaryEntity);
        extra.put("parentFeatureKey", parentFeatureKey);
        extra.put("parentFeatureName", parentFeatureName);
        extra.put("pagePrototypeContext", pagePrototypeContext);
        applyPrdTraceToTreeExtra(extra, prdTrace);
        return extra;
    }


    // A9.10.14.4.3.15.4.15.5.4.3_PRD_PROJECTION_QUALITY_GATE
    private int a915543EnsureFeaturePageCoverage(
            UUID projectId,
            ProjectModuleEntity module,
            Map<String, ProjectModuleEntity> featureIndex,
            Map<String, ProjectModuleEntity> pageIndex,
            List<ProjectModuleEntity> active,
            Map<String, Integer> usedKeys,
            String operator
    ) {
        if (projectId == null || module == null || featureIndex == null || featureIndex.isEmpty()) {
            return 0;
        }

        Map<UUID, ProjectModuleEntity> uniqueFeatures = new LinkedHashMap<>();
        for (ProjectModuleEntity feature : featureIndex.values()) {
            if (feature == null || feature.getId() == null) {
                continue;
            }

            if (!module.getId().equals(feature.getParentId())) {
                continue;
            }

            if (!"FEATURE".equals(normalizeModuleType(feature.getModuleType()))) {
                continue;
            }

            uniqueFeatures.putIfAbsent(feature.getId(), feature);
        }

        int createdPages = 0;
        for (ProjectModuleEntity feature : uniqueFeatures.values()) {
            if (a915543FeatureHasPage(active, feature.getId())) {
                continue;
            }

            String pageName = a915543FallbackPageName(feature.getModuleName(), module.getModuleName());
            String pageKey = semanticModuleKey(pageName, "page");

            ProjectModuleEntity existingPage = findIndexedNode(pageIndex, pageKey, pageName);
            if (existingPage != null) {
                continue;
            }

            Map<String, Object> fallbackPage = new LinkedHashMap<>();
            fallbackPage.put("page_name", pageName);
            fallbackPage.put("title", pageName);
            fallbackPage.put("name", pageName);
            fallbackPage.put("page_key", pageKey);
            fallbackPage.put("page_type", a915543FallbackPageKind(feature.getModuleName(), pageName));
            fallbackPage.put("parent_feature_key", feature.getModuleKey());
            fallbackPage.put("parent_feature_name", feature.getModuleName());
            fallbackPage.put("primary_entity", a915543BusinessObjectName(module.getModuleName(), feature.getModuleName()));
            fallbackPage.put("description", pageName + "用于承载“" + feature.getModuleName() + "”功能点，避免功能点缺少页面或交互承载。");
            fallbackPage.put("sourceType", "AI_COMPLETED");
            fallbackPage.put("confirmationStatus", "PENDING_CONFIRMATION");
            fallbackPage.put("qualityGateGenerated", true);
            fallbackPage.put("qualityGateReason", "FEATURE_WITHOUT_PAGE_CARRIER");

            Map<String, Object> featureTrace = a915543ExtractPrdTrace(feature);
            Map<String, Object> pageExtra = buildPageExtra(
                    fallbackPage,
                    pageKey,
                    pageName,
                    feature.getModuleKey(),
                    feature.getModuleName(),
                    featureTrace
            );

            pageExtra.put("sourcePrdSection", "functional_requirements");
            pageExtra.put("qualityGateGenerated", true);
            pageExtra.put("qualityGateReason", "FEATURE_WITHOUT_PAGE_CARRIER");
            pageExtra.put("projectionQualityGateVersion", "A9.10.14.4.3.15.4.15.5.4.3");
            pageExtra.put("derivedFromFeatureKey", feature.getModuleKey());
            pageExtra.put("derivedFromFeatureName", feature.getModuleName());

            ProjectModuleEntity createdPage = createMaterializedNode(
                    projectId,
                    feature.getId(),
                    feature.getModulePath(),
                    feature.getLevelNo() + 1,
                    pageName,
                    pageKey,
                    "PAGE",
                    nextSortOrder(projectId, feature.getId()),
                    pageExtra,
                    usedKeys,
                    operator
            );

            active.add(createdPage);
            indexNode(pageIndex, createdPage);
            projectModuleImpactService.markImpactedArtifactsAfterCreate(projectId, createdPage);
            createdPages++;
        }

        return createdPages;
    }

    private boolean a915543FeatureHasPage(
            List<ProjectModuleEntity> active,
            UUID featureId
    ) {
        if (active == null || active.isEmpty() || featureId == null) {
            return false;
        }

        for (ProjectModuleEntity item : active) {
            if (item == null) {
                continue;
            }

            if (!featureId.equals(item.getParentId())) {
                continue;
            }

            if ("PAGE".equals(normalizeModuleType(item.getModuleType()))
                    && ACTIVE.equals(item.getStatus())) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Object> a915543ExtractPrdTrace(ProjectModuleEntity feature) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (feature == null || feature.getExtra() == null) {
            return result;
        }

        Map<String, Object> extra = feature.getExtra();
        Object trace = extra.get("prdTrace");
        if (trace instanceof Map<?, ?> sourceTrace) {
            for (Map.Entry<?, ?> entry : sourceTrace.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        if (result.isEmpty()) {
            a915543CopyIfPresent(result, extra, "sourceRequirementId");
            a915543CopyIfPresent(result, extra, "sourceRequirementKey");
            a915543CopyIfPresent(result, extra, "sourceRequirementTitle");
            a915543CopyIfPresent(result, extra, "sourceType");
            a915543CopyIfPresent(result, extra, "confirmationStatus");
            a915543CopyIfPresent(result, extra, "sourceEvidence");
        }

        return result;
    }

    private void a915543CopyIfPresent(
            Map<String, Object> target,
            Map<String, Object> source,
            String key
    ) {
        if (target == null || source == null || !source.containsKey(key)) {
            return;
        }
        target.put(key, source.get(key));
    }

    private String a915543QualityPageName(
            String rawPageName,
            String featureName,
            String moduleName
    ) {
        String name = text(rawPageName);
        if (!hasText(name)) {
            return a915543FallbackPageName(featureName, moduleName);
        }

        if (!a915543IsGenericPageName(name)) {
            return name;
        }

        String suffix = "页";
        if (name.endsWith("弹窗")) {
            suffix = "弹窗";
        } else if (name.endsWith("抽屉")) {
            suffix = "抽屉";
        }

        String action = name
                .replace("页面", "")
                .replace("页", "")
                .replace("弹窗", "")
                .replace("抽屉", "")
                .trim();

        if (!hasText(action)) {
            action = "详情";
        }

        String prefix = a915543BusinessObjectName(moduleName, featureName);
        return prefix + action + suffix;
    }

    private boolean a915543IsGenericPageName(String value) {
        String name = text(value)
                .replace("页面", "页")
                .replace(" ", "")
                .trim();

        if (!hasText(name)) {
            return true;
        }

        return List.of(
                "新建页",
                "新增页",
                "创建页",
                "编辑页",
                "修改页",
                "列表页",
                "查询页",
                "搜索页",
                "详情页",
                "明细页",
                "查看页",
                "维护页",
                "设置页",
                "确认页",
                "提交页",
                "弹窗"
        ).contains(name);
    }

    private String a915543FallbackPageName(
            String featureName,
            String moduleName
    ) {
        String feature = text(featureName);
        String module = text(moduleName);
        String prefix = a915543BusinessObjectName(module, feature);

        if (!hasText(feature)) {
            return prefix + "详情页";
        }

        if (feature.contains("弹窗") || feature.contains("抽屉") || feature.endsWith("页")) {
            return feature;
        }

        if (feature.contains("提交")
                || feature.contains("确认")
                || feature.contains("状态更新")
                || feature.contains("异常记录")
                || feature.contains("异常处理")
                || feature.contains("记录")) {
            return prefix + feature + "弹窗";
        }

        if (feature.contains("看板")
                || feature.contains("报表")
                || feature.contains("统计")
                || feature.contains("分析")
                || feature.contains("指标")
                || feature.contains("筛选")
                || feature.contains("钻取")) {
            return feature + "页";
        }

        if (feature.contains("日志")) {
            return prefix + feature + "页";
        }

        if (a915543IsGenericFeatureName(feature)) {
            String action = feature
                    .replace("新增", "新建")
                    .replace("创建", "新建")
                    .trim();
            return prefix + action + "页";
        }

        return feature + "页";
    }

    private String a915543FallbackPageKind(
            String featureName,
            String pageName
    ) {
        String text = text(featureName) + " " + text(pageName);

        if (text.contains("列表") || text.contains("查询") || text.contains("搜索")) {
            return "LIST_PAGE";
        }

        if (text.contains("详情") || text.contains("明细") || text.contains("查看") || text.contains("日志")) {
            return "DETAIL_PAGE";
        }

        if (text.contains("看板") || text.contains("报表") || text.contains("统计") || text.contains("分析") || text.contains("指标")) {
            return "DASHBOARD_PAGE";
        }

        if (text.contains("配置") || text.contains("设置") || text.contains("权限")) {
            return "SETTINGS_PAGE";
        }

        return "FORM_PAGE";
    }

    private boolean a915543IsGenericFeatureName(String value) {
        String name = text(value).replace(" ", "").trim();
        return List.of(
                "新建",
                "新增",
                "创建",
                "编辑",
                "修改",
                "列表",
                "查询",
                "搜索",
                "详情",
                "明细",
                "查看",
                "维护",
                "设置"
        ).contains(name);
    }

    private String a915543BusinessObjectName(
            String moduleName,
            String featureName
    ) {
        String feature = text(featureName).trim();
        if (hasText(feature) && !a915543IsGenericFeatureName(feature)) {
            String cleanedFeature = feature
                    .replace("提交确认", "")
                    .replace("状态更新", "")
                    .replace("异常记录", "")
                    .replace("异常处理", "")
                    .replace("操作日志", "")
                    .replace("列表查询", "")
                    .replace("列表", "")
                    .replace("查询", "")
                    .replace("新建", "")
                    .replace("新增", "")
                    .replace("创建", "")
                    .replace("编辑", "")
                    .replace("修改", "")
                    .replace("详情", "")
                    .replace("查看", "")
                    .replace("维护", "")
                    .trim();

            if (hasText(cleanedFeature) && cleanedFeature.length() >= 2) {
                return cleanedFeature;
            }
        }

        String module = text(moduleName)
                .replace("模块", "")
                .replace("管理", "")
                .replace("维护", "")
                .trim();

        if (hasText(module)) {
            return module;
        }

        if (hasText(feature)) {
            return feature;
        }

        return "业务";
    }


    private String normalizePageKind(String value) {
        String text = String.valueOf(value == null ? "" : value).toUpperCase(Locale.ROOT);
        String zh = String.valueOf(value == null ? "" : value);

        if (text.contains("LIST") || text.contains("TABLE") || zh.contains("列表") || zh.contains("查询") || zh.contains("搜索")) {
            return "LIST_PAGE";
        }

        if (text.contains("APPROVAL") || text.contains("AUDIT") || zh.contains("审核") || zh.contains("审批")) {
            return "APPROVAL_PAGE";
        }

        if (text.contains("DETAIL") || text.contains("VIEW") || zh.contains("详情") || zh.contains("明细") || zh.contains("查看")) {
            return "DETAIL_PAGE";
        }

        if (text.contains("DASHBOARD") || zh.contains("看板") || zh.contains("统计") || zh.contains("分析")) {
            return "DASHBOARD_PAGE";
        }

        if (text.contains("SETTING") || zh.contains("设置") || zh.contains("配置")) {
            return "SETTINGS_PAGE";
        }

        return "FORM_PAGE";
    }

    private String defaultLayoutType(String pageKind) {
        return switch (String.valueOf(pageKind == null ? "" : pageKind)) {
            case "LIST_PAGE" -> "table-list";
            case "DETAIL_PAGE" -> "detail-tabs";
            case "APPROVAL_PAGE" -> "approval-workbench";
            case "DASHBOARD_PAGE" -> "dashboard";
            default -> "form-detail";
        };
    }

    private ProjectModuleEntity resolveFeatureForPage(
            Map<String, Object> page,
            Map<String, ProjectModuleEntity> featureIndex
    ) {
        String explicitKey = text(page.get("parent_feature_key"), page.get("parentFeatureKey"));
        String explicitName = text(page.get("parent_feature_name"), page.get("parentFeatureName"));

        ProjectModuleEntity explicit = findIndexedNode(
                featureIndex,
                semanticModuleKey(text(explicitKey, explicitName), "feature"),
                explicitName
        );

        if (explicit != null) {
            return explicit;
        }

        String pageText = normalizedTreeText(text(
                page.get("page_name"),
                page.get("pageName"),
                page.get("name"),
                page.get("title"),
                page.get("description")
        ));

        ProjectModuleEntity best = null;
        int bestScore = 0;

        for (ProjectModuleEntity feature : featureIndex.values()) {
            String featureText = normalizedTreeText(feature.getModuleName() + " " + feature.getModuleKey());
            if (!hasText(featureText)) {
                continue;
            }

            int score = 0;
            if (pageText.contains(featureText) || featureText.contains(pageText)) {
                score += Math.min(pageText.length(), featureText.length()) + 20;
            }

            String featureName = normalizedTreeText(feature.getModuleName());
            if (hasText(featureName) && pageText.contains(featureName)) {
                score += 30;
            }

            if (score > bestScore) {
                bestScore = score;
                best = feature;
            }
        }

        if (best != null && bestScore >= 2) {
            return best;
        }

        return featureIndex.values().stream().findFirst().orElse(null);
    }

    private String inferFeatureNameFromPage(
            Map<String, Object> page,
            String moduleName
    ) {
        String explicit = text(page.get("parent_feature_name"), page.get("parentFeatureName"));
        if (hasText(explicit)) {
            return explicit;
        }

        String name = text(page.get("page_name"), page.get("pageName"), page.get("name"), page.get("title"));
        String cleaned = name
                .replace("页面", "")
                .replace("页", "")
                .replace("列表", "查询")
                .replace("详情", "查看")
                .trim();

        if (hasText(cleaned)) {
            return cleaned;
        }

        return hasText(moduleName) ? moduleName + "业务能力" : "业务能力";
    }

    private ProjectModuleEntity findById(
            List<ProjectModuleEntity> nodes,
            UUID id
    ) {
        if (id == null) {
            return null;
        }

        for (ProjectModuleEntity node : nodes) {
            if (id.equals(node.getId())) {
                return node;
            }
        }

        return null;
    }

    private void indexNode(
            Map<String, ProjectModuleEntity> index,
            ProjectModuleEntity node
    ) {
        if (node == null) {
            return;
        }

        String keyText = normalizedTreeText(node.getModuleKey());
        String nameText = normalizedTreeText(node.getModuleName());

        if (hasText(keyText)) {
            index.putIfAbsent(keyText, node);
        }

        if (hasText(nameText)) {
            index.putIfAbsent(nameText, node);
        }
    }

    private ProjectModuleEntity findIndexedNode(
            Map<String, ProjectModuleEntity> index,
            String key,
            String name
    ) {
        String keyText = normalizedTreeText(key);
        String nameText = normalizedTreeText(name);

        if (hasText(keyText) && index.containsKey(keyText)) {
            return index.get(keyText);
        }

        if (hasText(nameText) && index.containsKey(nameText)) {
            return index.get(nameText);
        }

        for (Map.Entry<String, ProjectModuleEntity> entry : index.entrySet()) {
            String candidate = entry.getKey();
            if (hasText(candidate) && hasText(nameText)
                    && (candidate.contains(nameText) || nameText.contains(candidate))) {
                return entry.getValue();
            }
        }

        return null;
    }


    private Map<String, Object> asObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(converted);
            }
        }

        return result;
    }

    private Object firstValue(
            Map<String, Object> source,
            String... keys
    ) {
        if (source == null) {
            return null;
        }

        for (String key : keys) {
            if (!source.containsKey(key)) {
                continue;
            }

            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }

        return null;
    }

    private List<String> textList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String text = text(item);
                if (hasText(text) && !result.contains(text)) {
                    result.add(text);
                }
            }
            return result;
        }

        String text = text(value);
        if (!hasText(text)) {
            return List.of();
        }

        String[] parts = text.split("[,，、;；\\n]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String item = part == null ? "" : part.trim();
            if (hasText(item) && !result.contains(item)) {
                result.add(item);
            }
        }

        return result;
    }

    private String text(Object... values) {
        if (values == null) {
            return "";
        }

        for (Object value : values) {
            if (value == null) {
                continue;
            }

            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }

    private String semanticModuleKey(
            String value,
            String suffix
    ) {
        String text = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        String[][] replacements = new String[][]{
                {"入库", "inbound"},
                {"出库", "outbound"},
                {"库存", "inventory"},
                {"仓库", "warehouse"},
                {"供应商", "supplier"},
                {"客户", "customer"},
                {"订单", "order"},
                {"单据", "document"},
                {"商品", "product"},
                {"物料", "material"},
                {"创建", "create"},
                {"新建", "create"},
                {"新增", "create"},
                {"录入", "create"},
                {"查询", "query"},
                {"搜索", "search"},
                {"列表", "list"},
                {"详情", "detail"},
                {"明细", "detail"},
                {"查看", "view"},
                {"编辑", "edit"},
                {"修改", "edit"},
                {"审核", "approval"},
                {"审批", "approval"},
                {"提交", "submit"},
                {"管理", "management"},
                {"页面", "page"},
                {"页", "page"},
                {"功能点", "feature"},
                {"功能", "feature"},
                {"业务能力", "feature"}
        };

        for (String[] pair : replacements) {
            text = text.replace(pair[0], "_" + pair[1] + "_");
        }

        text = text.replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        if (!hasText(text)) {
            text = "node_" + Integer.toHexString(String.valueOf(value == null ? suffix : value).hashCode());
        }

        if (hasText(suffix) && !text.endsWith("_" + suffix) && !text.equals(suffix)) {
            text = text + "_" + suffix;
        }

        if (text.length() > 80) {
            text = text.substring(0, 80);
        }

        return sanitizeModuleKey(text);
    }

    private String normalizedTreeText(Object value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-\\/]+", "")
                .replaceAll("[^0-9a-z\\u4e00-\\u9fff]+", "");
    }


    // A9.10.14.4.3.15.6.24L0B_FIX_CREATE_PAGE_UNDER_FINAL_MODULE
    private String normalizeCreateNodeTypeForRetiredFeatureLayer(
            String requestedType,
            String normalizedType,
            String moduleName,
            ProjectModuleEntity parent,
            Map<String, Object> extra
    ) {
        String parentType = parent == null ? null : normalizeModuleType(parent.getModuleType());

        if (!"MODULE".equals(parentType)) {
            return normalizedType;
        }

        if ("PAGE".equals(normalizedType)) {
            return "PAGE";
        }

        // FEATURE has retired to MODULE_PRD capability metadata. Manual creation
        // under a MODULE should create a PAGE, not a visible FEATURE node.
        if ("FEATURE".equals(normalizedType)) {
            return "PAGE";
        }

        boolean rawBlank = requestedType == null || requestedType.isBlank();
        boolean finalRequirementModule = isFinalRequirementModule(parent);

        if ("MODULE".equals(normalizedType)
                && (finalRequirementModule || rawBlank || looksLikePageCreation(moduleName, extra))) {
            return "PAGE";
        }

        return normalizedType;
    }

    private boolean looksLikePageCreation(
            String moduleName,
            Map<String, Object> extra
    ) {
        String name = moduleName == null ? "" : moduleName.trim();

        if (name.matches(".*(页|页面|弹窗|弹框|列表|详情|新建|新增|编辑|维护|查看|查询|表单|日志|确认|取消|状态|异常).*")) {
            return true;
        }

        if (extra == null || extra.isEmpty()) {
            return false;
        }

        String type = text(
                extra.get("nodeType"),
                extra.get("moduleType"),
                extra.get("type"),
                extra.get("pageKind"),
                extra.get("pageType"),
                extra.get("layoutType"),
                extra.get("routePath"),
                extra.get("route_path")
        );

        if (hasText(type) && type.matches(".*(PAGE|LIST|DETAIL|CREATE|EDIT|FORM|DIALOG|ROUTE|页面|页|弹窗).*")) {
            return true;
        }

        Object canGenerateUiContract = extra.get("canGenerateUiContract");
        return Boolean.TRUE.equals(canGenerateUiContract);
    }

    // A9.10.14.4.3.15.6.24L0B_FINAL_CREATE_PAGE_POLICY_FIX
    private void assertAllowedChildType(
            ProjectModuleEntity parent,
            String childType
    ) {
        String parentType = parent == null ? null : normalizeModuleType(parent.getModuleType());
        String normalizedChildType = normalizeModuleType(childType);

        if (parentType == null) {
            if (!"MODULE".equals(normalizedChildType)) {
                throw new IllegalArgumentException("根节点只能创建模块。");
            }

            return;
        }

        switch (parentType) {
            case "MODULE" -> {
                if (isFinalRequirementModule(parent)) {
                    if (!Set.of("PAGE", "FEATURE").contains(normalizedChildType)) {
                        throw new IllegalArgumentException("已有 MODULE_PRD 的最终模块下只能新增页面，功能清单保留在 PRD 中。");
                    }

                    return;
                }

                if (Set.of("PAGE", "FEATURE").contains(normalizedChildType) && hasActiveChildModule(parent)) {
                    throw new IllegalArgumentException("当前模块下仍有子模块。请先选择末级模块后再新增页面。");
                }

                if (!Set.of("MODULE", "PAGE", "FEATURE").contains(normalizedChildType)) {
                    throw new IllegalArgumentException("模块下只能新增子模块或页面。");
                }
            }
            case "FEATURE" -> {
                if (!"PAGE".equals(normalizedChildType)) {
                    throw new IllegalArgumentException("历史能力绑定下只能新增页面。");
                }
            }
            case "PAGE" -> throw new IllegalArgumentException("页面下不支持继续新增业务节点。");
            default -> throw new IllegalArgumentException("未知父节点类型：" + parentType);
        }
    }



    private String normalizeRequiredName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("节点名称不能为空。");
        }

        return value.trim();
    }

    // A9.10.14.4.3.15.4.15.6.1_FINAL_MODULE_FEATURE_PAGE_ALIAS
    // A9.10.14.4.3.15.4.15.6.1_HOTFIX_CHINESE_NODE_TYPE_ALIAS_PROJECT
    private String normalizeModuleType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "MODULE";
        }

        String raw = rawType.trim();
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

        return "MODULE";
    }


    private boolean isRequirementModule(ProjectModuleEntity module) {
        if (module == null || module.getExtra() == null) {
            return false;
        }

        Object value = module.getExtra().get("requirementMode");

        if (value == null) {
            value = module.getExtra().get("requirement_mode");
        }

        return "REQUIREMENT_MODULE".equalsIgnoreCase(String.valueOf(value));
    }

    // A9.10.14.4.3.15.4.10.4.6_FINAL_MODULE_RULE_CLOSURE
    // MODULE_PRD existence is also a final-module signal. This prevents users
    // from adding more MODULE children after the module has entered reviewed PRD flow.
    private boolean isFinalRequirementModule(ProjectModuleEntity module) {
        return isRequirementModule(module) || hasCurrentModulePrdArtifact(module);
    }

    private boolean hasCurrentModulePrdArtifact(ProjectModuleEntity module) {
        if (module == null || module.getProjectId() == null || module.getId() == null) {
            return false;
        }

        String sql = """
                SELECT COUNT(1)
                FROM artifact_version
                WHERE project_id = :projectId
                  AND module_id = :moduleId
                  AND artifact_type = 'MODULE_PRD'
                  AND is_current = true
                  AND status NOT IN ('SUPERSEDED', 'ARCHIVED', 'DELETED')
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", module.getProjectId())
                        .addValue("moduleId", module.getId()),
                Integer.class
        );

        return count != null && count > 0;
    }

    private boolean hasActiveChildModule(ProjectModuleEntity parent) {
        if (parent == null || parent.getProjectId() == null || parent.getId() == null) {
            return false;
        }

        for (ProjectModuleEntity module : projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(parent.getProjectId())) {
            if (!parent.getId().equals(module.getParentId())) {
                continue;
            }

            if (!ACTIVE.equals(module.getStatus())) {
                continue;
            }

            if ("MODULE".equals(normalizeModuleType(module.getModuleType()))) {
                return true;
            }
        }

        return false;
    }

    private void markParentAsRequirementModule(ProjectModuleEntity parent, String username) {
        if (parent == null || isRequirementModule(parent)) {
            return;
        }

        Map<String, Object> extra = parent.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(parent.getExtra());

        extra.put("requirementMode", "REQUIREMENT_MODULE");
        extra.put("moduleMaterializedBy", "PAGE_CHILD_OR_CAPABILITY");
        extra.put("moduleMaterializedAt", LocalDateTime.now().toString());

        parent.setExtra(extra);
        parent.setUpdatedBy(username);
        parent.setUpdatedAt(LocalDateTime.now());

        projectModuleRepository.save(parent);
    }

    private Map<String, Object> normalizeExtra(
            Map<String, Object> rawExtra,
            String moduleType
    ) {
        Map<String, Object> extra = new LinkedHashMap<>();

        if (rawExtra != null) {
            extra.putAll(rawExtra);
        }

        String normalizedType = normalizeModuleType(moduleType);

        extra.put("moduleType", normalizedType);
        extra.put("type", normalizedType);
        extra.put("canGenerateUiContract", "PAGE".equals(normalizedType));

        if ("MODULE".equals(normalizedType)) {
            extra.putIfAbsent("requirementMode", "DRAFT_MODULE");
        }

        if ("FEATURE".equals(normalizedType)) {
            extra.put("requirementMode", "FUNCTION_POINT");
        }

        if ("PAGE".equals(normalizedType)) {
            extra.put("requirementMode", "PAGE_CONTRACT");
            extra.putIfAbsent("pageKind", "FORM_PAGE");
            extra.putIfAbsent("layoutType", "form-detail");
        }

        return extra;
    }

    private Map<String, Integer> loadUsedModuleKeys(UUID projectId) {
        Map<String, Integer> used = new LinkedHashMap<>();

        for (ProjectModuleEntity module : projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)) {
            String key = module.getModuleKey();

            if (key == null || key.isBlank()) {
                continue;
            }

            used.put(key, 1);
        }

        return used;
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

        baseKey = sanitizeModuleKey(baseKey);

        if (baseKey.isBlank()) {
            baseKey = "mod_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        String candidate = baseKey;
        int index = 1;

        while (usedModuleKeys.containsKey(candidate)) {
            candidate = baseKey + "_" + index;
            index++;
        }

        usedModuleKeys.put(candidate, 1);

        return candidate;
    }

    private String sanitizeModuleKey(String value) {
        String result = value
                .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                .replaceAll("_+", "_");

        if (result.isBlank()) {
            return "mod_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        return result;
    }

    private String buildDeletedModuleKey(
            ProjectModuleEntity entity
    ) {
        String rawKey = entity.getModuleKey();

        if (rawKey == null || rawKey.isBlank()) {
            rawKey = "deleted_module";
        }

        String idPart = entity.getId() == null
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                : entity.getId().toString().replace("-", "").substring(0, 8);

        String suffix = "__deleted_" + idPart;

        if (rawKey.contains("__deleted_")) {
            return rawKey;
        }

        return sanitizeModuleKey(rawKey + suffix);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }


    // A9.10.14.4.3.15.6.24L0C_PHYSICAL_FEATURE_RETIRE_AND_24L1B_DEMO_DATA_LABEL
    @Transactional
    public Map<String, Object> retireFeatureLayerPhysical(
            UUID workspaceId,
            String projectCode,
            boolean dryRun
    ) {
        UUID projectId = resolveAccessibleProjectByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        List<ProjectModuleEntity> active = new ArrayList<>(activeModules(projectId));

        Map<UUID, ProjectModuleEntity> byId = new LinkedHashMap<>();
        Map<UUID, List<ProjectModuleEntity>> childrenByParent = new LinkedHashMap<>();

        for (ProjectModuleEntity item : active) {
            if (item == null || item.getId() == null) {
                continue;
            }

            byId.put(item.getId(), item);

            if (item.getParentId() != null) {
                childrenByParent
                        .computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        List<Map<String, Object>> plannedMoves = new ArrayList<>();
        List<Map<String, Object>> skippedFeatures = new ArrayList<>();
        List<ProjectModuleEntity> pagesToRefresh = new ArrayList<>();

        int featureCount = 0;
        int movedPageCount = 0;
        int retiredFeatureCount = 0;

        for (ProjectModuleEntity feature : active) {
            if (feature == null || !"FEATURE".equals(normalizeModuleType(feature.getModuleType()))) {
                continue;
            }

            featureCount++;

            ProjectModuleEntity ownerModule = nearestVisibleModuleForFeature(feature, byId);

            if (ownerModule == null) {
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("featureId", feature.getId().toString());
                skipped.put("featureName", feature.getModuleName());
                skipped.put("reason", "NO_VISIBLE_MODULE_ANCESTOR");
                skippedFeatures.add(skipped);
                continue;
            }

            List<ProjectModuleEntity> children = childrenByParent.getOrDefault(
                    feature.getId(),
                    List.of()
            );

            List<ProjectModuleEntity> pageChildren = children.stream()
                    .filter(item -> "PAGE".equals(normalizeModuleType(item.getModuleType())))
                    .toList();

            List<ProjectModuleEntity> nonPageChildren = children.stream()
                    .filter(item -> !"PAGE".equals(normalizeModuleType(item.getModuleType())))
                    .toList();

            if (!nonPageChildren.isEmpty()) {
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("featureId", feature.getId().toString());
                skipped.put("featureName", feature.getModuleName());
                skipped.put("reason", "FEATURE_HAS_NON_PAGE_CHILDREN");
                skipped.put("nonPageChildCount", nonPageChildren.size());
                skippedFeatures.add(skipped);
                continue;
            }

            if (pageChildren.isEmpty()) {
                Map<String, Object> planned = new LinkedHashMap<>();
                planned.put("featureId", feature.getId().toString());
                planned.put("featureName", feature.getModuleName());
                planned.put("targetModuleId", ownerModule.getId().toString());
                planned.put("targetModuleName", ownerModule.getModuleName());
                planned.put("pageCount", 0);
                planned.put("action", dryRun ? "DRY_RUN_RETIRE_EMPTY_FEATURE" : "RETIRE_EMPTY_FEATURE");
                plannedMoves.add(planned);

                if (!dryRun) {
                    retireFeatureNode(feature, ownerModule, currentUser.username(), now, List.of());
                    retiredFeatureCount++;
                }

                continue;
            }

            List<String> movedPageIds = new ArrayList<>();
            int baseSortOrder = nextSortOrder(projectId, ownerModule.getId());
            int offset = 0;

            for (ProjectModuleEntity page : pageChildren) {
                Map<String, Object> planned = new LinkedHashMap<>();
                planned.put("pageId", page.getId().toString());
                planned.put("pageName", page.getModuleName());
                planned.put("fromFeatureId", feature.getId().toString());
                planned.put("fromFeatureName", feature.getModuleName());
                planned.put("toModuleId", ownerModule.getId().toString());
                planned.put("toModuleName", ownerModule.getModuleName());
                plannedMoves.add(planned);

                movedPageIds.add(page.getId().toString());

                if (!dryRun) {
                    Map<String, Object> extra = page.getExtra() == null
                            ? new LinkedHashMap<>()
                            : new LinkedHashMap<>(page.getExtra());

                    List<Object> bindings = new ArrayList<>();
                    Object oldBindings = extra.get("capabilityBindings");
                    if (oldBindings instanceof List<?> list) {
                        bindings.addAll(list);
                    }

                    Map<String, Object> binding = new LinkedHashMap<>();
                    binding.put("source", "PHYSICAL_FEATURE_LAYER_RETIRE");
                    binding.put("featureNodeId", feature.getId().toString());
                    binding.put("featureKey", feature.getModuleKey());
                    binding.put("featureName", feature.getModuleName());
                    binding.put("originalParentId", feature.getId().toString());
                    binding.put("retiredAt", now.toString());
                    bindings.add(binding);

                    extra.put("capabilityBindings", dedupeCapabilityBindings(bindings));
                    extra.put("legacyParentFeature", binding);
                    extra.put("legacyParentFeatureId", feature.getId().toString());
                    extra.put("legacyParentFeatureName", feature.getModuleName());
                    extra.put("featureLayerPhysicalMigration", Map.of(
                            "source", "A9.10.14.4.3.15.6.24L0C",
                            "migratedAt", now.toString(),
                            "migratedBy", currentUser.username(),
                            "targetModuleId", ownerModule.getId().toString(),
                            "targetModuleName", ownerModule.getModuleName()
                    ));

                    page.setParentId(ownerModule.getId());
                    page.setLevelNo(ownerModule.getLevelNo() + 1);
                    page.setSortOrder(baseSortOrder + offset);
                    page.setModulePath(joinModulePath(ownerModule, page.getModuleName()));
                    page.setExtra(extra);
                    page.setUpdatedBy(currentUser.username());
                    page.setUpdatedAt(now);

                    projectModuleRepository.save(page);
                    pagesToRefresh.add(page);
                    movedPageCount++;
                }

                offset++;
            }

            if (!dryRun) {
                retireFeatureNode(feature, ownerModule, currentUser.username(), now, movedPageIds);
                retiredFeatureCount++;
            }
        }

        if (!dryRun) {
            for (ProjectModuleEntity page : pagesToRefresh) {
                refreshDescendantPaths(projectId, page);
            }

            projectModuleRepository.flush();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "A9.10.14.4.3.15.6.24L0C_PHYSICAL_FEATURE_RETIRE");
        result.put("dryRun", dryRun);
        result.put("projectCode", projectCode);
        result.put("featureCount", featureCount);
        result.put("plannedMoveCount", plannedMoves.size());
        result.put("movedPageCount", movedPageCount);
        result.put("retiredFeatureCount", retiredFeatureCount);
        result.put("skippedFeatureCount", skippedFeatures.size());
        result.put("plannedMoves", plannedMoves);
        result.put("skippedFeatures", skippedFeatures);

        if (!dryRun) {
            result.put("tree", buildModuleTree(projectId));
        }

        return result;
    }

    private ProjectModuleEntity nearestVisibleModuleForFeature(
            ProjectModuleEntity feature,
            Map<UUID, ProjectModuleEntity> byId
    ) {
        ProjectModuleEntity cursor = feature == null || feature.getParentId() == null
                ? null
                : byId.get(feature.getParentId());

        while (cursor != null) {
            String type = normalizeModuleType(cursor.getModuleType());

            if ("MODULE".equals(type) || "SUBMODULE".equals(type) || "DOMAIN".equals(type)) {
                return cursor;
            }

            UUID parentId = cursor.getParentId();
            cursor = parentId == null ? null : byId.get(parentId);
        }

        return null;
    }

    private void retireFeatureNode(
            ProjectModuleEntity feature,
            ProjectModuleEntity ownerModule,
            String username,
            LocalDateTime now,
            List<String> movedPageIds
    ) {
        Map<String, Object> extra = feature.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(feature.getExtra());

        extra.put("featureLayerRetired", true);
        extra.put("featureLayerRetiredAt", now.toString());
        extra.put("featureLayerRetiredBy", username);
        extra.put("featureLayerRetiredSource", "A9.10.14.4.3.15.6.24L0C");
        extra.put("retiredToModuleId", ownerModule == null ? "" : ownerModule.getId().toString());
        extra.put("retiredToModuleName", ownerModule == null ? "" : ownerModule.getModuleName());
        extra.put("movedPageIds", movedPageIds);

        feature.setStatus(DELETED);
        feature.setModuleKey(buildDeletedModuleKey(feature));
        feature.setExtra(extra);
        feature.setUpdatedBy(username);
        feature.setUpdatedAt(now);

        projectModuleRepository.save(feature);
    }

    private List<Object> dedupeCapabilityBindings(List<Object> bindings) {
        List<Object> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Object item : bindings) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }

            Object featureNodeId = map.get("featureNodeId");
            Object featureKey = map.get("featureKey");
            Object featureName = map.get("featureName");

            String key = String.valueOf(featureNodeId == null ? "" : featureNodeId)
                    + "|" + String.valueOf(featureKey == null ? "" : featureKey)
                    + "|" + String.valueOf(featureName == null ? "" : featureName);

            if (key.isBlank() || seen.contains(key)) {
                continue;
            }

            seen.add(key);
            result.add(item);
        }

        return result;
    }

    private String joinModulePath(
            ProjectModuleEntity parent,
            String childName
    ) {
        String parentPath = parent == null ? "" : String.valueOf(parent.getModulePath() == null ? "" : parent.getModulePath()).trim();
        String name = String.valueOf(childName == null ? "" : childName).trim();

        if (parentPath.isBlank()) {
            return name;
        }

        if (name.isBlank()) {
            return parentPath;
        }

        return parentPath + "/" + name;
    }


    public static class ModuleTreeNode {

        private UUID id;
        private UUID parentId;
        private String moduleKey;
        private String moduleName;
        private String moduleType;
        private String modulePath;
        private Integer levelNo;
        private Integer sortOrder;
        private String status;
        private Map<String, Object> extra;
        private List<ModuleTreeNode> children = new ArrayList<>();

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public UUID getParentId() {
            return parentId;
        }

        public void setParentId(UUID parentId) {
            this.parentId = parentId;
        }

        public String getModuleKey() {
            return moduleKey;
        }

        public void setModuleKey(String moduleKey) {
            this.moduleKey = moduleKey;
        }

        public String getModuleName() {
            return moduleName;
        }

        public void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }

        public String getModuleType() {
            return moduleType;
        }

        public void setModuleType(String moduleType) {
            this.moduleType = moduleType;
        }

        public String getModulePath() {
            return modulePath;
        }

        public void setModulePath(String modulePath) {
            this.modulePath = modulePath;
        }

        public Integer getLevelNo() {
            return levelNo;
        }

        public void setLevelNo(Integer levelNo) {
            this.levelNo = levelNo;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }

        public void setExtra(Map<String, Object> extra) {
            this.extra = extra;
        }

        public List<ModuleTreeNode> getChildren() {
            return children;
        }

        public void setChildren(List<ModuleTreeNode> children) {
            this.children = children;
        }
    }
}
// A9.10.14.4.3.15.6.24L0A_CONTINUE_AGENT_BACKEND: backend policy allows MODULE -> PAGE; FEATURE remains legacy capability metadata.

// A9.10.14.4.3.15.6.24L0B_FINAL_CREATE_PAGE_POLICY_FIX: final MODULE create policy exposes PAGE, not visible FEATURE.
