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
        node.setExtra(entity.getExtra());

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

    private void assertAllowedChildType(
            ProjectModuleEntity parent,
            String childType
    ) {
        String parentType = parent == null ? null : normalizeModuleType(parent.getModuleType());
        String normalizedChildType = normalizeModuleType(childType);

        if (parentType == null) {
            if (!Set.of("DOMAIN", "MODULE").contains(normalizedChildType)) {
                throw new IllegalArgumentException("根节点只能创建业务域或模块。");
            }

            return;
        }

        switch (parentType) {
            case "DOMAIN" -> {
                if (!"MODULE".equals(normalizedChildType)) {
                    throw new IllegalArgumentException("业务域下只能创建模块节点。");
                }
            }
            case "MODULE" -> {
                if (!Set.of("SUBMODULE", "PAGE").contains(normalizedChildType)) {
                    throw new IllegalArgumentException("模块下只能创建子模块或页面节点。");
                }
            }
            case "SUBMODULE" -> {
                if (!"PAGE".equals(normalizedChildType)) {
                    throw new IllegalArgumentException("子模块下只能创建页面节点。");
                }
            }
            case "PAGE" -> {
                if (!"FEATURE".equals(normalizedChildType)) {
                    throw new IllegalArgumentException("页面下只能创建功能点节点。");
                }
            }
            case "FEATURE" -> throw new IllegalArgumentException("功能点下不支持继续创建子节点。");
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
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> "DOMAIN";
            case "MODULE", "BUSINESS_MODULE", "FEATURE_MODULE", "INTEGRATION_MODULE", "INFRA_MODULE" -> "MODULE";
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> "SUBMODULE";
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION" -> "FEATURE";
            default -> "MODULE";
        };
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

        if ("PAGE".equals(normalizedType)) {
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