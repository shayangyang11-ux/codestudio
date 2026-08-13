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

        String moduleType = normalizeModuleType(
                request == null ? null : request.getModuleType()
        );

        assertAllowedChildType(parent, moduleType);

        if (parent != null
                && "MODULE".equals(normalizeModuleType(parent.getModuleType()))
                && "FEATURE".equals(moduleType)) {
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
                && "FEATURE".equals(normalizeModuleType(entity.getModuleType()))) {
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
                    finalRequirementModule ? List.of("FEATURE") : List.of("MODULE", "FEATURE")
            );
            extra.put(
                    "allowedChildCreateLabel",
                    finalRequirementModule ? "新增业务能力" : "新增子模块"
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
    public Map<String, Object> completeRequirementModuleChildrenFromModulePrd(
            UUID projectId,
            UUID moduleId,
            Map<String, Object> modulePrdContent,
            String username
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION");
        result.put("projectId", projectId == null ? "" : projectId.toString());
        result.put("moduleId", moduleId == null ? "" : moduleId.toString());
        result.put("createdFeatureCount", 0);
        result.put("createdPageCount", 0);
        result.put("updatedFeatureCount", 0);
        result.put("updatedPageCount", 0);
        result.put("skipped", false);
        result.put("reason", "");

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

        if (functionalRequirements.isEmpty() && pageRequirements.isEmpty()) {
            result.put("skipped", true);
            result.put("reason", "MODULE_PRD_HAS_NO_FUNCTIONAL_OR_PAGE_REQUIREMENTS");
            return result;
        }

        List<ProjectModuleEntity> active = new ArrayList<>(activeModules(projectId));
        boolean hasChildModule = active.stream().anyMatch(item ->
                moduleId.equals(item.getParentId())
                        && ACTIVE.equals(item.getStatus())
                        && "MODULE".equals(normalizeModuleType(item.getModuleType()))
        );

        if (hasChildModule) {
            result.put("skipped", true);
            result.put("reason", "MODULE_STILL_HAS_CHILD_MODULES");
            return result;
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        String operator = hasText(username) ? username : currentUser.username();

        markParentAsRequirementModule(module, operator);

        Map<String, Integer> usedKeys = loadUsedModuleKeys(projectId);
        Map<String, ProjectModuleEntity> featureIndex = new LinkedHashMap<>();

        for (ProjectModuleEntity item : active) {
            if (!moduleId.equals(item.getParentId())) {
                continue;
            }

            if (!"FEATURE".equals(normalizeModuleType(item.getModuleType()))) {
                continue;
            }

            indexNode(featureIndex, item);
        }

        int createdFeatures = 0;
        int createdPages = 0;
        int updatedFeatures = 0;
        int updatedPages = 0;

        int nextFeatureSortOrder = nextSortOrder(projectId, moduleId);

        for (Map<String, Object> requirement : functionalRequirements) {
            String featureName = text(
                    requirement.get("title"),
                    requirement.get("requirement_name"),
                    requirement.get("requirementName"),
                    requirement.get("feature_name"),
                    requirement.get("featureName"),
                    requirement.get("name")
            );

            if (!hasText(featureName)) {
                continue;
            }

            String featureKeySeed = text(
                    requirement.get("requirement_key"),
                    requirement.get("requirementKey"),
                    requirement.get("feature_key"),
                    requirement.get("featureKey"),
                    requirement.get("key"),
                    featureName
            );

            String featureKey = semanticModuleKey(featureKeySeed, "feature");
            ProjectModuleEntity existing = findIndexedNode(featureIndex, featureKey, featureName);

            if (existing == null) {
                ProjectModuleEntity created = createMaterializedNode(
                        projectId,
                        moduleId,
                        module.getModulePath(),
                        module.getLevelNo() + 1,
                        featureName,
                        featureKey,
                        "FEATURE",
                        nextFeatureSortOrder++,
                        buildFeatureExtra(requirement, featureKey, featureName),
                        usedKeys,
                        operator
                );
                createdFeatures++;
                active.add(created);
                indexNode(featureIndex, created);
                projectModuleImpactService.markImpactedArtifactsAfterCreate(projectId, created);
            } else {
                mergeNodeExtra(existing, buildFeatureExtra(requirement, featureKey, featureName), operator);
                projectModuleRepository.save(existing);
                updatedFeatures++;
                indexNode(featureIndex, existing);
            }
        }

        if (featureIndex.isEmpty() && !pageRequirements.isEmpty()) {
            for (Map<String, Object> page : pageRequirements) {
                String featureName = inferFeatureNameFromPage(page, module.getModuleName());
                String featureKey = semanticModuleKey(
                        text(page.get("parent_feature_key"), page.get("parentFeatureKey"), featureName),
                        "feature"
                );

                ProjectModuleEntity existing = findIndexedNode(featureIndex, featureKey, featureName);
                if (existing != null) {
                    continue;
                }

                ProjectModuleEntity created = createMaterializedNode(
                        projectId,
                        moduleId,
                        module.getModulePath(),
                        module.getLevelNo() + 1,
                        featureName,
                        featureKey,
                        "FEATURE",
                        nextFeatureSortOrder++,
                        buildFeatureExtra(page, featureKey, featureName),
                        usedKeys,
                        operator
                );
                createdFeatures++;
                active.add(created);
                indexNode(featureIndex, created);
                projectModuleImpactService.markImpactedArtifactsAfterCreate(projectId, created);
            }
        }

        Map<String, ProjectModuleEntity> pageIndex = new LinkedHashMap<>();
        for (ProjectModuleEntity item : active) {
            if (!"PAGE".equals(normalizeModuleType(item.getModuleType()))) {
                continue;
            }

            ProjectModuleEntity parent = findById(active, item.getParentId());
            if (parent == null || !moduleId.equals(parent.getParentId())) {
                continue;
            }

            indexNode(pageIndex, item);
        }

        for (Map<String, Object> page : pageRequirements) {
            String pageName = text(
                    page.get("page_name"),
                    page.get("pageName"),
                    page.get("name"),
                    page.get("title")
            );

            if (!hasText(pageName)) {
                continue;
            }

            String pageKey = semanticModuleKey(
                    text(page.get("page_key"), page.get("pageKey"), page.get("key"), pageName),
                    "page"
            );

            ProjectModuleEntity feature = resolveFeatureForPage(page, featureIndex);
            if (feature == null) {
                String featureName = inferFeatureNameFromPage(page, module.getModuleName());
                String featureKey = semanticModuleKey(
                        text(page.get("parent_feature_key"), page.get("parentFeatureKey"), featureName),
                        "feature"
                );

                feature = findIndexedNode(featureIndex, featureKey, featureName);
                if (feature == null) {
                    feature = createMaterializedNode(
                            projectId,
                            moduleId,
                            module.getModulePath(),
                            module.getLevelNo() + 1,
                            featureName,
                            featureKey,
                            "FEATURE",
                            nextFeatureSortOrder++,
                            buildFeatureExtra(page, featureKey, featureName),
                            usedKeys,
                            operator
                    );
                    createdFeatures++;
                    active.add(feature);
                    indexNode(featureIndex, feature);
                    projectModuleImpactService.markImpactedArtifactsAfterCreate(projectId, feature);
                }
            }

            ProjectModuleEntity existingPage = findIndexedNode(pageIndex, pageKey, pageName);
            Map<String, Object> pageExtra = buildPageExtra(
                    page,
                    pageKey,
                    pageName,
                    feature.getModuleKey(),
                    feature.getModuleName()
            );

            if (existingPage == null) {
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
                createdPages++;
                active.add(createdPage);
                indexNode(pageIndex, createdPage);
                projectModuleImpactService.markImpactedArtifactsAfterCreate(projectId, createdPage);
            } else {
                mergeNodeExtra(existingPage, pageExtra, operator);
                projectModuleRepository.save(existingPage);
                updatedPages++;
                indexNode(pageIndex, existingPage);
            }
        }

        Map<String, Object> moduleExtra = module.getExtra() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(module.getExtra());
        moduleExtra.put("requirementMode", "REQUIREMENT_MODULE");
        moduleExtra.put("moduleMaterializedBy", "MODULE_PRD_MANUAL_COMPLETION");
        moduleExtra.put("modulePrdTreeCompletionAt", LocalDateTime.now().toString());
        moduleExtra.put("modulePrdTreeCompletionSource", "A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION");
        moduleExtra.put("modulePrdTreeCompletion", Map.of(
                "createdFeatureCount", createdFeatures,
                "createdPageCount", createdPages,
                "updatedFeatureCount", updatedFeatures,
                "updatedPageCount", updatedPages
        ));
        module.setExtra(moduleExtra);
        module.setUpdatedBy(operator);
        module.setUpdatedAt(LocalDateTime.now());
        projectModuleRepository.save(module);

        projectModuleRepository.flush();

        result.put("createdFeatureCount", createdFeatures);
        result.put("createdPageCount", createdPages);
        result.put("updatedFeatureCount", updatedFeatures);
        result.put("updatedPageCount", updatedPages);
        result.put("featureCount", featureIndex.size());
        result.put("pageCount", pageIndex.size());
        result.put("reason", "COMPLETED_FROM_REVIEWED_MODULE_PRD_BY_USER_ACTION");
        return result;
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
            String featureName
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

        extra.put("requirementMode", "FUNCTION_POINT");
        extra.put("featureRole", "prd_functional_requirement");
        extra.put("source", "MODULE_PRD_TO_TREE_COMPLETION");
        extra.put("sourcePrdSection", "functional_requirements");
        extra.put("functionContext", functionContext);
        return extra;
    }

    private Map<String, Object> buildPageExtra(
            Map<String, Object> source,
            String pageKey,
            String pageName,
            String parentFeatureKey,
            String parentFeatureName
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
        return extra;
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
                    if (!"FEATURE".equals(normalizedChildType)) {
                        throw new IllegalArgumentException("已有 MODULE_PRD 的最终模块下只能新增业务能力，不能继续新增子模块。");
                    }

                    return;
                }

                if ("FEATURE".equals(normalizedChildType) && hasActiveChildModule(parent)) {
                    throw new IllegalArgumentException("当前模块下仍有子模块。请先选择末级模块并生成 MODULE_PRD 后再新增业务能力。");
                }

                if (!Set.of("MODULE", "FEATURE").contains(normalizedChildType)) {
                    throw new IllegalArgumentException("模块下只能新增子模块或业务能力。");
                }
            }
            case "FEATURE" -> {
                if (!"PAGE".equals(normalizedChildType)) {
                    throw new IllegalArgumentException("业务能力下只能新增页面。");
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

    private String normalizeModuleType(String rawType) {
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
        extra.put("moduleMaterializedBy", "FEATURE_CHILD");
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