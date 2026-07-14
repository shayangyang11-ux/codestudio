package com.codemind.platform.memory;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.common.JsonbHelper;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MemoryWriterService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProjectModuleRepository projectModuleRepository;

    public MemoryWriterService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ProjectModuleRepository projectModuleRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.projectModuleRepository = projectModuleRepository;
    }

    public void writeModuleTreeMemory(
            ProjectEntity project,
            AgentTaskEntity task,
            ArtifactVersionEntity artifact,
            Map<String, Object> moduleTreeContent
    ) {
        List<ProjectModuleEntity> modules =
                projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(project.getId());

        writeProjectModuleTreeSummary(
                project,
                task,
                artifact,
                modules,
                moduleTreeContent
        );

        writeModuleMemoryCards(
                project,
                task,
                artifact,
                modules
        );

        writeModuleTreeChunk(
                project,
                task,
                artifact,
                modules
        );
    }

    private void writeProjectModuleTreeSummary(
            ProjectEntity project,
            AgentTaskEntity task,
            ArtifactVersionEntity artifact,
            List<ProjectModuleEntity> modules,
            Map<String, Object> moduleTreeContent
    ) {
        List<String> rootModuleNames = modules.stream()
                .filter(module -> module.getParentId() == null)
                .map(ProjectModuleEntity::getModuleName)
                .toList();

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("project_code", project.getProjectCode());
        content.put("project_name", project.getProjectName());
        content.put("artifact_id", artifact.getId().toString());
        content.put("artifact_type", artifact.getArtifactType());
        content.put("version_no", artifact.getVersionNo());
        content.put("module_count", modules.size());
        content.put("root_modules", rootModuleNames);
        content.put("module_tree_content", moduleTreeContent);

        String summary = "项目当前模块树包含 "
                + modules.size()
                + " 个模块，顶层模块包括："
                + String.join("、", rootModuleNames)
                + "。";

        String sql = """
                INSERT INTO project_memory (
                    project_id,
                    version_no,
                    memory_type,
                    title,
                    summary,
                    content,
                    source_artifact_id,
                    source_task_id,
                    confidence,
                    is_confirmed,
                    status,
                    created_by,
                    updated_by
                )
                VALUES (
                    :projectId,
                    :versionNo,
                    :memoryType,
                    :title,
                    :summary,
                    :content,
                    :sourceArtifactId,
                    :sourceTaskId,
                    :confidence,
                    :isConfirmed,
                    :status,
                    :createdBy,
                    :updatedBy
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", project.getId())
                .addValue("versionNo", artifact.getVersionNo())
                .addValue("memoryType", "MODULE_TREE_SUMMARY")
                .addValue("title", "项目模块树摘要")
                .addValue("summary", summary)
                .addValue("content", JsonbHelper.toJsonb(content, objectMapper))
                .addValue("sourceArtifactId", artifact.getId())
                .addValue("sourceTaskId", task.getId())
                .addValue("confidence", 1.00)
                .addValue("isConfirmed", false)
                .addValue("status", "ACTIVE")
                .addValue("createdBy", "system")
                .addValue("updatedBy", "system");

        jdbcTemplate.update(sql, params);
    }

    private void writeModuleMemoryCards(
            ProjectEntity project,
            AgentTaskEntity task,
            ArtifactVersionEntity artifact,
            List<ProjectModuleEntity> modules
    ) {
        String sql = """
                INSERT INTO module_memory (
                    project_id,
                    module_id,
                    version_no,
                    responsibility,
                    summary,
                    key_rules,
                    provided_apis,
                    consumed_apis,
                    owned_entities,
                    risk_points,
                    source_artifact_id,
                    source_task_id,
                    confidence,
                    is_confirmed,
                    status,
                    created_by,
                    updated_by
                )
                VALUES (
                    :projectId,
                    :moduleId,
                    :versionNo,
                    :responsibility,
                    :summary,
                    :keyRules,
                    :providedApis,
                    :consumedApis,
                    :ownedEntities,
                    :riskPoints,
                    :sourceArtifactId,
                    :sourceTaskId,
                    :confidence,
                    :isConfirmed,
                    :status,
                    :createdBy,
                    :updatedBy
                )
                """;

        for (ProjectModuleEntity module : modules) {
            Map<String, Object> keyRules = new LinkedHashMap<>();
            keyRules.put("source", "MODULE_TREE");
            keyRules.put("module_path", module.getModulePath());

            Map<String, Object> riskPoints = new LinkedHashMap<>();
            riskPoints.put("status", "TO_BE_ANALYZED");
            riskPoints.put("reason", "等待 MODULE_PRD 与工程资产生成后进一步补充。");

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("projectId", project.getId())
                    .addValue("moduleId", module.getId())
                    .addValue("versionNo", artifact.getVersionNo())
                    .addValue("responsibility", buildResponsibility(module))
                    .addValue("summary", buildModuleSummary(module))
                    .addValue("keyRules", JsonbHelper.toJsonb(keyRules, objectMapper))
                    .addValue("providedApis", JsonbHelper.toJsonb(List.of(), objectMapper))
                    .addValue("consumedApis", JsonbHelper.toJsonb(List.of(), objectMapper))
                    .addValue("ownedEntities", JsonbHelper.toJsonb(List.of(), objectMapper))
                    .addValue("riskPoints", JsonbHelper.toJsonb(riskPoints, objectMapper))
                    .addValue("sourceArtifactId", artifact.getId())
                    .addValue("sourceTaskId", task.getId())
                    .addValue("confidence", 0.80)
                    .addValue("isConfirmed", false)
                    .addValue("status", "ACTIVE")
                    .addValue("createdBy", "system")
                    .addValue("updatedBy", "system");

            jdbcTemplate.update(sql, params);
        }
    }

    private void writeModuleTreeChunk(
            ProjectEntity project,
            AgentTaskEntity task,
            ArtifactVersionEntity artifact,
            List<ProjectModuleEntity> modules
    ) {
        String chunkText = modules.stream()
                .map(module -> module.getModulePath() + "：" + module.getModuleName())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        if (chunkText.isBlank()) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("artifact_id", artifact.getId().toString());
        metadata.put("artifact_type", artifact.getArtifactType());
        metadata.put("task_id", task.getId().toString());
        metadata.put("project_code", project.getProjectCode());

        String sql = """
                INSERT INTO memory_chunk (
                    project_id,
                    module_id,
                    source_type,
                    source_id,
                    source_artifact_id,
                    chunk_index,
                    chunk_text,
                    chunk_summary,
                    embedding,
                    embedding_model,
                    embedding_dim,
                    tags,
                    metadata,
                    status,
                    created_by
                )
                VALUES (
                    :projectId,
                    NULL,
                    :sourceType,
                    :sourceId,
                    :sourceArtifactId,
                    :chunkIndex,
                    :chunkText,
                    :chunkSummary,
                    NULL,
                    NULL,
                    NULL,
                    :tags,
                    :metadata,
                    :status,
                    :createdBy
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", project.getId())
                .addValue("sourceType", "ARTIFACT")
                .addValue("sourceId", artifact.getId())
                .addValue("sourceArtifactId", artifact.getId())
                .addValue("chunkIndex", 0)
                .addValue("chunkText", chunkText)
                .addValue("chunkSummary", "项目模块树文本切片，用于后续上下文检索。")
                .addValue("tags", JsonbHelper.toJsonb(List.of("MODULE_TREE", "REQUIREMENT_AGENT"), objectMapper))
                .addValue("metadata", JsonbHelper.toJsonb(metadata, objectMapper))
                .addValue("status", "ACTIVE")
                .addValue("createdBy", "system");

        jdbcTemplate.update(sql, params);
    }

    private String buildResponsibility(ProjectModuleEntity module) {
        return "负责“" + module.getModuleName() + "”相关业务能力。";
    }

    private String buildModuleSummary(ProjectModuleEntity module) {
        return "模块路径：" + module.getModulePath()
                + "；模块类型：" + module.getModuleType()
                + "；当前状态：" + module.getStatus() + "。";
    }
}