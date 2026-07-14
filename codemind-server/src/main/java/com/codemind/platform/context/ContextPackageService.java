package com.codemind.platform.context;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.common.JsonbHelper;
import com.codemind.platform.memory.MemoryReaderService;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ContextPackageService {

    private final ArtifactVersionRepository artifactVersionRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryReaderService memoryReaderService;

    public ContextPackageService(
            ArtifactVersionRepository artifactVersionRepository,
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MemoryReaderService memoryReaderService
    ) {
        this.artifactVersionRepository = artifactVersionRepository;
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

        Map<String, Object> projectContext = buildProjectContext(project);
        Map<String, Object> moduleContext = buildModuleContext(module);

        Map<String, Object> artifactsContext = buildArtifactsContext(
                project.getId(),
                module == null ? null : module.getId(),
                includedArtifactIds
        );

        MemoryReaderService.MemoryContextResult memoryContextResult =
                memoryReaderService.readForContextPackage(
                        project.getId(),
                        module == null ? null : module.getId(),
                        taskType.name()
                );

        Map<String, Object> contextPackage = new LinkedHashMap<>();
        contextPackage.put("context_version", "1.0");
        contextPackage.put("task_type", taskType.name());
        contextPackage.put("project", projectContext);
        contextPackage.put("current_module", moduleContext);
        contextPackage.put("input", input);
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
                "must_bind_artifact_version", true
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

    private Map<String, Object> buildProjectContext(ProjectEntity project) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("project_id", project.getId());
        context.put("project_code", project.getProjectCode());
        context.put("project_name", project.getProjectName());
        context.put("description", project.getDescription());
        context.put("status", project.getStatus());
        context.put("tech_stack", project.getTechStack());
        return context;
    }

    private Map<String, Object> buildModuleContext(ProjectModuleEntity module) {
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
        context.put("extra", module.getExtra());
        return context;
    }

    private Map<String, Object> buildArtifactsContext(
            UUID projectId,
            UUID moduleId,
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
                    artifacts.put("module_tree", buildArtifactCard(artifact));
                });

        if (moduleId != null) {
            artifactVersionRepository
                    .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                            projectId,
                            moduleId,
                            "MODULE_PRD"
                    )
                    .ifPresent(artifact -> {
                        includedArtifactIds.add(artifact.getId());
                        artifacts.put("module_prd", buildArtifactCard(artifact));
                    });
        }

        return artifacts;
    }

    private Map<String, Object> buildArtifactCard(ArtifactVersionEntity artifact) {
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
                .addValue("contextPackage", JsonbHelper.toJsonb(contextPackage, objectMapper))
                .addValue("includedArtifactIds", JsonbHelper.toJsonb(includedArtifactIds, objectMapper))
                .addValue("includedMemoryIds", JsonbHelper.toJsonb(includedMemoryIds, objectMapper))
                .addValue("includedChunkIds", JsonbHelper.toJsonb(includedChunkIds, objectMapper))
                .addValue("includedContractIds", JsonbHelper.toJsonb(List.of(), objectMapper))
                .addValue("includedDependencyIds", JsonbHelper.toJsonb(List.of(), objectMapper))
                .addValue("tokenBudget", 12000)
                .addValue("estimatedInputTokens", 0)
                .addValue("createdBy", "system");

        jdbcTemplate.update(sql, params);
    }
}