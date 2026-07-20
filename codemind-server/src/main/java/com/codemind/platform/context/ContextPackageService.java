package com.codemind.platform.context;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.common.JsonbHelper;
import com.codemind.platform.memory.MemoryReaderService;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ContextPackageService {

    private static final String ACTIVE = "ACTIVE";

    private final ArtifactVersionRepository artifactVersionRepository;
    private final ProjectModuleRepository projectModuleRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryReaderService memoryReaderService;

    public ContextPackageService(
            ArtifactVersionRepository artifactVersionRepository,
            ProjectModuleRepository projectModuleRepository,
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MemoryReaderService memoryReaderService
    ) {
        this.artifactVersionRepository = artifactVersionRepository;
        this.projectModuleRepository = projectModuleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.memoryReaderService = memoryReaderService;
    }

    public Map<String, Object> buildAndLogContextPackage(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            TaskType taskType,
            Map<String, Object> input
    ) {
        List<UUID> includedArtifactIds = new ArrayList<>();

        List<ProjectModuleEntity> activeModules = projectModuleRepository
                .findByProjectIdOrderByLevelNoAscSortOrderAsc(project.getId())
                .stream()
                .filter(item -> ACTIVE.equalsIgnoreCase(item.getStatus()))
                .toList();

        Map<String, Object> projectContext = buildProjectContext(project);
        Map<String, Object> projectThemeProfileContext =
                buildProjectThemeProfileContext(project.getId());
        projectContext.put(
                "theme_profile",
                projectThemeProfileContext
        );

        Map<String, Object> moduleContext = buildModuleContext(module);

        Map<String, Object> artifactsContext = buildArtifactsContext(
                project.getId(),
                module == null ? null : module.getId(),
                taskType,
                includedArtifactIds
        );

        boolean uiContractTask =
                TaskType.GENERATE_UI_CONTRACT.equals(taskType);

        List<Map<String, Object>> moduleAncestry = uiContractTask
                ? buildModuleAncestry(module, activeModules)
                : List.of();

        List<Map<String, Object>> featureNodes = uiContractTask
                ? buildFeatureNodes(
                project.getId(),
                module,
                activeModules,
                includedArtifactIds
        )
                : List.of();

        List<Map<String, Object>> navigationCandidates = uiContractTask
                ? buildNavigationCandidates(
                module,
                activeModules
        )
                : List.of();

        MemoryReaderService.MemoryContextResult memoryContextResult =
                memoryReaderService.readForContextPackage(
                        project.getId(),
                        module == null ? null : module.getId(),
                        taskType.name()
                );

        Map<String, Object> contextPackage = new LinkedHashMap<>();
        contextPackage.put("context_version", "1.1");
        contextPackage.put("task_type", taskType.name());
        contextPackage.put("project", projectContext);
        contextPackage.put(
                "project_theme_profile",
                projectThemeProfileContext
        );
        contextPackage.put("current_module", moduleContext);
        if (uiContractTask) {
            contextPackage.put("module_ancestry", moduleAncestry);
            contextPackage.put("feature_nodes", featureNodes);
            contextPackage.put(
                    "navigation_candidates",
                    navigationCandidates
            );
        }
        contextPackage.put("input", input == null ? Map.of() : input);
        contextPackage.put("artifacts", artifactsContext);
        contextPackage.put("memory", memoryContextResult.getMemory());
        contextPackage.put("dependencies", Map.of(
                "dependency_edges", List.of(),
                "contracts", List.of()
        ));
        contextPackage.put("constraints", Map.of(
                "language", "zh-CN",
                "output_format", "json_and_markdown",
                "must_bind_task_id", true,
                "must_bind_artifact_version", true,
                "arbitrary_script_forbidden", true
        ));
        contextPackage.put("token_budget", Map.of(
                "policy_name", "default_requirement_agent_policy",
                "max_input_tokens", 12000,
                "max_output_tokens", 6000
        ));

        writeContextPackageLog(
                task,
                module == null ? null : module.getId(),
                taskType,
                contextPackage,
                includedArtifactIds,
                memoryContextResult.getMemoryIds(),
                memoryContextResult.getChunkIds()
        );

        return contextPackage;
    }

    private Map<String, Object> buildProjectContext(
            ProjectEntity project
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("project_id", project.getId());
        context.put("project_code", project.getProjectCode());
        context.put("project_name", project.getProjectName());
        context.put("description", project.getDescription());
        context.put("status", project.getStatus());
        context.put("tech_stack", project.getTechStack());
        return context;
    }

    private Map<String, Object> buildModuleContext(
            ProjectModuleEntity module
    ) {
        if (module == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("module_id", module.getId());
        context.put("parent_id", module.getParentId());
        context.put("module_key", module.getModuleKey());
        context.put("module_name", module.getModuleName());
        context.put("module_type", module.getModuleType());
        context.put("module_path", module.getModulePath());
        context.put("level_no", module.getLevelNo());
        context.put("sort_order", module.getSortOrder());
        context.put("status", module.getStatus());
        context.put(
                "extra",
                module.getExtra() == null
                        ? Map.of()
                        : module.getExtra()
        );
        return context;
    }

    private Map<String, Object> buildArtifactsContext(
            UUID projectId,
            UUID moduleId,
            TaskType taskType,
            List<UUID> includedArtifactIds
    ) {
        Map<String, Object> artifacts = new LinkedHashMap<>();

        artifactVersionRepository
                .findFirstByProjectIdAndModuleIdIsNullAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        "MODULE_TREE"
                )
                .ifPresent(artifact -> {
                    includedArtifactIds.add(artifact.getId());
                    artifacts.put(
                            "module_tree",
                            buildArtifactCard(artifact)
                    );
                });

        if (moduleId == null) {
            return artifacts;
        }

        List<ArtifactVersionEntity> currentModuleArtifacts =
                artifactVersionRepository
                        .findByProjectIdAndModuleIdAndCurrentTrue(
                                projectId,
                                moduleId
                        );

        for (ArtifactVersionEntity artifact : currentModuleArtifacts) {
            boolean includeArtifact =
                    TaskType.GENERATE_UI_CONTRACT.equals(taskType)
                            || "MODULE_PRD".equalsIgnoreCase(
                            artifact.getArtifactType()
                    );

            if (!includeArtifact) {
                continue;
            }

            includedArtifactIds.add(artifact.getId());
            artifacts.put(
                    artifactKey(artifact.getArtifactType()),
                    buildArtifactCard(artifact)
            );
        }

        return artifacts;
    }

    private List<Map<String, Object>> buildModuleAncestry(
            ProjectModuleEntity module,
            List<ProjectModuleEntity> activeModules
    ) {
        if (module == null) {
            return List.of();
        }

        Map<UUID, ProjectModuleEntity> byId = new LinkedHashMap<>();

        for (ProjectModuleEntity item : activeModules) {
            byId.put(item.getId(), item);
        }

        List<Map<String, Object>> reverse = new ArrayList<>();
        UUID currentId = module.getParentId();

        while (currentId != null) {
            ProjectModuleEntity current = byId.get(currentId);

            if (current == null) {
                break;
            }

            reverse.add(buildModuleContext(current));
            currentId = current.getParentId();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (int index = reverse.size() - 1; index >= 0; index--) {
            result.add(reverse.get(index));
        }

        return result;
    }

    private List<Map<String, Object>> buildFeatureNodes(
            UUID projectId,
            ProjectModuleEntity page,
            List<ProjectModuleEntity> activeModules,
            List<UUID> includedArtifactIds
    ) {
        if (page == null
                || !"PAGE".equals(normalizeNodeType(page.getModuleType()))) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (ProjectModuleEntity feature : activeModules) {
            if (!page.getId().equals(feature.getParentId())) {
                continue;
            }

            if (!"FEATURE".equals(
                    normalizeNodeType(feature.getModuleType())
            )) {
                continue;
            }

            Map<String, Object> featureCard =
                    new LinkedHashMap<>(buildModuleContext(feature));

            Map<String, Object> featureArtifacts =
                    new LinkedHashMap<>();

            for (ArtifactVersionEntity artifact : artifactVersionRepository
                    .findByProjectIdAndModuleIdAndCurrentTrue(
                            projectId,
                            feature.getId()
                    )) {
                includedArtifactIds.add(artifact.getId());
                featureArtifacts.put(
                        artifactKey(artifact.getArtifactType()),
                        buildArtifactCard(artifact)
                );
            }

            featureCard.put("artifacts", featureArtifacts);
            result.add(featureCard);
        }

        return result;
    }

    private List<Map<String, Object>> buildNavigationCandidates(
            ProjectModuleEntity currentPage,
            List<ProjectModuleEntity> activeModules
    ) {
        if (currentPage == null) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (ProjectModuleEntity candidate : activeModules) {
            if (!"PAGE".equals(
                    normalizeNodeType(candidate.getModuleType())
            )) {
                continue;
            }

            if (candidate.getId().equals(currentPage.getId())) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("page_id", candidate.getId());
            item.put("page_key", candidate.getModuleKey());
            item.put("page_name", candidate.getModuleName());
            item.put("page_path", candidate.getModulePath());
            item.put("parent_id", candidate.getParentId());
            item.put(
                    "same_parent",
                    currentPage.getParentId() != null
                            && currentPage.getParentId()
                            .equals(candidate.getParentId())
            );
            item.put(
                    "extra",
                    candidate.getExtra() == null
                            ? Map.of()
                            : candidate.getExtra()
            );
            result.add(item);
        }

        return result;
    }

    private Map<String, Object> buildArtifactCard(
            ArtifactVersionEntity artifact
    ) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("artifact_id", artifact.getId());
        card.put("artifact_type", artifact.getArtifactType());
        card.put("version_no", artifact.getVersionNo());
        card.put("title", artifact.getTitle());
        card.put("status", artifact.getStatus());
        card.put("is_current", artifact.getCurrent());
        card.put("content", artifact.getContent());
        card.put("content_markdown", artifact.getContentMarkdown());
        return card;
    }

    private String artifactKey(
            String artifactType
    ) {
        if (artifactType == null || artifactType.isBlank()) {
            return "unknown";
        }

        return artifactType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNodeType(
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION" -> "FEATURE";
            default -> raw.trim().toUpperCase(Locale.ROOT);
        };
    }

    private Map<String, Object> buildProjectThemeProfileContext(
            UUID projectId
    ) {
        String sql = """
                SELECT
                    id,
                    project_id,
                    industry,
                    theme_key,
                    visual_tone,
                    primary_color,
                    secondary_color,
                    accent_color,
                    density,
                    radius,
                    login_layout,
                    navigation_style,
                    dark_mode_enabled,
                    source,
                    locked,
                    notes,
                    updated_at
                FROM project_theme_profile
                WHERE project_id = :projectId
                LIMIT 1
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("projectId", projectId),
                    (rs, rowNum) -> {
                        Map<String, Object> context =
                                new LinkedHashMap<>();

                        context.put("schema_version", "1.0");
                        context.put("id", rs.getObject("id"));
                        context.put("project_id", rs.getObject("project_id"));
                        context.put("projectId", rs.getObject("project_id"));
                        context.put("industry", rs.getString("industry"));
                        context.put("theme_key", rs.getString("theme_key"));
                        context.put("themeKey", rs.getString("theme_key"));
                        context.put("visual_tone", rs.getString("visual_tone"));
                        context.put("visualTone", rs.getString("visual_tone"));
                        context.put("primary_color", rs.getString("primary_color"));
                        context.put("primaryColor", rs.getString("primary_color"));
                        context.put("secondary_color", rs.getString("secondary_color"));
                        context.put("secondaryColor", rs.getString("secondary_color"));
                        context.put("accent_color", rs.getString("accent_color"));
                        context.put("accentColor", rs.getString("accent_color"));
                        context.put("density", rs.getString("density"));
                        context.put("radius", rs.getString("radius"));
                        context.put("login_layout", rs.getString("login_layout"));
                        context.put("loginLayout", rs.getString("login_layout"));
                        context.put("navigation_style", rs.getString("navigation_style"));
                        context.put("navigationStyle", rs.getString("navigation_style"));
                        context.put("dark_mode_enabled", rs.getBoolean("dark_mode_enabled"));
                        context.put("darkModeEnabled", rs.getBoolean("dark_mode_enabled"));
                        context.put("source", rs.getString("source"));
                        context.put("locked", rs.getBoolean("locked"));
                        context.put("notes", rs.getString("notes"));
                        context.put("updated_at", rs.getObject("updated_at"));
                        context.put("updatedAt", rs.getObject("updated_at"));

                        return context;
                    }
            );
        } catch (EmptyResultDataAccessException e) {
            return defaultProjectThemeProfileContext(projectId);
        }
    }

    private Map<String, Object> defaultProjectThemeProfileContext(
            UUID projectId
    ) {
        Map<String, Object> context = new LinkedHashMap<>();

        context.put("schema_version", "1.0");
        context.put("id", null);
        context.put("project_id", projectId);
        context.put("projectId", projectId);
        context.put("industry", "GENERAL");
        context.put("theme_key", "enterprise_default");
        context.put("themeKey", "enterprise_default");
        context.put("visual_tone", "CLEAN");
        context.put("visualTone", "CLEAN");
        context.put("primary_color", "#2563eb");
        context.put("primaryColor", "#2563eb");
        context.put("secondary_color", null);
        context.put("secondaryColor", null);
        context.put("accent_color", null);
        context.put("accentColor", null);
        context.put("density", "STANDARD");
        context.put("radius", "MEDIUM");
        context.put("login_layout", "BRAND_SPLIT");
        context.put("loginLayout", "BRAND_SPLIT");
        context.put("navigation_style", "SIDE_NAV");
        context.put("navigationStyle", "SIDE_NAV");
        context.put("dark_mode_enabled", false);
        context.put("darkModeEnabled", false);
        context.put("source", "SYSTEM_DEFAULT_FALLBACK");
        context.put("locked", false);
        context.put("notes", null);
        context.put("updated_at", null);
        context.put("updatedAt", null);

        return context;
    }

    private void writeContextPackageLog(
            AgentTaskEntity task,
            UUID moduleId,
            TaskType taskType,
            Map<String, Object> contextPackage,
            List<UUID> includedArtifactIds,
            List<UUID> includedMemoryIds,
            List<UUID> includedChunkIds
    ) {
        String sql = """
                INSERT INTO context_package_log (
                    task_id,
                    project_id,
                    module_id,
                    task_type,
                    context_package,
                    included_artifact_ids,
                    included_memory_ids,
                    included_chunk_ids,
                    included_contract_ids,
                    included_dependency_ids,
                    token_budget,
                    estimated_input_tokens,
                    created_by
                )
                VALUES (
                    :taskId,
                    :projectId,
                    :moduleId,
                    :taskType,
                    :contextPackage,
                    :includedArtifactIds,
                    :includedMemoryIds,
                    :includedChunkIds,
                    :includedContractIds,
                    :includedDependencyIds,
                    :tokenBudget,
                    :estimatedInputTokens,
                    :createdBy
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("taskId", task.getId())
                .addValue("projectId", task.getProjectId())
                .addValue("moduleId", moduleId)
                .addValue("taskType", taskType.name())
                .addValue(
                        "contextPackage",
                        JsonbHelper.toJsonb(contextPackage, objectMapper)
                )
                .addValue(
                        "includedArtifactIds",
                        JsonbHelper.toJsonb(includedArtifactIds, objectMapper)
                )
                .addValue(
                        "includedMemoryIds",
                        JsonbHelper.toJsonb(includedMemoryIds, objectMapper)
                )
                .addValue(
                        "includedChunkIds",
                        JsonbHelper.toJsonb(includedChunkIds, objectMapper)
                )
                .addValue(
                        "includedContractIds",
                        JsonbHelper.toJsonb(List.of(), objectMapper)
                )
                .addValue(
                        "includedDependencyIds",
                        JsonbHelper.toJsonb(List.of(), objectMapper)
                )
                .addValue("tokenBudget", 12000)
                .addValue("estimatedInputTokens", 0)
                .addValue("createdBy", "system");

        jdbcTemplate.update(sql, params);
    }
}
