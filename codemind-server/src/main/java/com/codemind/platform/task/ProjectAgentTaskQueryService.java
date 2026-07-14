package com.codemind.platform.task;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProjectAgentTaskQueryService {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAtDesc",
            "createdAtAsc",
            "updatedAtDesc",
            "updatedAtAsc",
            "priorityDesc",
            "statusAsc"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProjectAgentTaskQueryService(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public ProjectAgentTaskStatusSummaryView getProjectTaskSummary(
            UUID projectId
    ) {
        String sql = """
                SELECT status, COUNT(*) AS task_count
                FROM agent_task
                WHERE project_id = :projectId
                  AND archived_at IS NULL
                GROUP BY status
                """;

        Map<String, Long> statusCounts = new LinkedHashMap<>();

        putDefaultStatusCounts(statusCounts);

        jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId),
                rs -> {
                    String status = rs.getString("status");
                    long taskCount = rs.getLong("task_count");
                    statusCounts.put(status, taskCount);
                }
        );

        return ProjectAgentTaskStatusSummaryView.of(
                projectId,
                statusCounts
        );
    }

    @Transactional(readOnly = true)
    public ProjectAgentTaskPageView searchProjectTasks(
            UUID projectId,
            String status,
            String taskType,
            UUID moduleId,
            String createdBy,
            int page,
            int size,
            String sort
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        String normalizedStatus = normalizeBlank(status);
        String normalizedTaskType = normalizeBlank(taskType);
        String normalizedCreatedBy = normalizeBlank(createdBy);
        String normalizedSort = normalizeSort(sort);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("limit", safeSize)
                .addValue("offset", offset);

        StringBuilder where = new StringBuilder("""
                WHERE t.project_id = :projectId
                  AND t.archived_at IS NULL
                """);

        if (normalizedStatus != null) {
            where.append(" AND t.status = :status ");
            params.addValue("status", normalizedStatus);
        }

        if (normalizedTaskType != null) {
            where.append(" AND t.task_type = :taskType ");
            params.addValue("taskType", normalizedTaskType);
        }

        if (moduleId != null) {
            where.append(" AND t.module_id = :moduleId ");
            params.addValue("moduleId", moduleId);
        }

        if (normalizedCreatedBy != null) {
            where.append(" AND t.created_by = :createdBy ");
            params.addValue("createdBy", normalizedCreatedBy);
        }

        String countSql = """
                SELECT COUNT(*)
                FROM agent_task t
                """ + where;

        Long total = jdbcTemplate.queryForObject(
                countSql,
                params,
                Long.class
        );

        String pageSql = """
                SELECT
                    t.id,
                    t.project_id,
                    t.module_id,
                    t.task_type,
                    t.status,
                    t.result_artifact_id,
                    t.error_code,
                    t.error_message,
                    t.last_error_code,
                    t.last_error_message,
                    t.queue_name,
                    t.priority,
                    t.attempt_count,
                    t.max_attempts,
                    t.next_run_at,
                    t.locked_by,
                    t.locked_until,
                    t.heartbeat_at,
                    t.created_by,
                    t.started_at,
                    t.finished_at,
                    t.request_payload IS NOT NULL AS has_request_payload,
                    t.response_payload IS NOT NULL AS has_response_payload,
                    t.request_payload ->> 'requirement_text' AS requirement_text,
                    t.created_at,
                    t.updated_at
                FROM agent_task t
                """ + where + """
                """ + orderBySql(normalizedSort) + """
                LIMIT :limit OFFSET :offset
                """;

        List<ProjectAgentTaskPageView.ProjectAgentTaskPageItemView> content =
                jdbcTemplate.query(
                        pageSql,
                        params,
                        taskPageItemRowMapper()
                );

        return ProjectAgentTaskPageView.of(
                content,
                safePage,
                safeSize,
                total == null ? 0L : total,
                normalizedStatus,
                normalizedTaskType,
                moduleId == null ? null : moduleId.toString(),
                normalizedCreatedBy,
                normalizedSort
        );
    }

    @Transactional(readOnly = true)
    public ProjectAgentTaskMatrixView getProjectModuleTaskMatrix(
            UUID projectId
    ) {
        List<ModuleRow> modules = loadProjectModules(projectId);
        Map<ModuleTaskKey, LatestTaskRow> latestTaskMap = loadLatestTaskMap(projectId);
        Map<ModuleArtifactKey, CurrentArtifactRow> currentArtifactMap = loadCurrentArtifactMap(projectId);

        List<ProjectAgentTaskMatrixView.ProjectModuleTaskMatrixView> moduleViews = new ArrayList<>();

        for (ModuleRow module : modules) {
            List<ProjectAgentTaskMatrixView.ProjectModuleTaskMatrixItemView> items = new ArrayList<>();

            for (RequirementArtifactTaskDefinition definition : RequirementArtifactTaskDefinition.REQUIREMENT_ARTIFACT_TASKS) {
                ModuleTaskKey taskKey = new ModuleTaskKey(
                        module.moduleId(),
                        definition.taskType()
                );

                ModuleArtifactKey artifactKey = new ModuleArtifactKey(
                        module.moduleId(),
                        definition.artifactType()
                );

                LatestTaskRow latestTask = latestTaskMap.get(taskKey);
                CurrentArtifactRow artifact = currentArtifactMap.get(artifactKey);

                items.add(new ProjectAgentTaskMatrixView.ProjectModuleTaskMatrixItemView(
                        definition.artifactType(),
                        definition.title(),
                        definition.taskType(),
                        definition.title(),
                        definition.sortOrder(),
                        artifact != null,
                        artifact == null ? null : artifact.artifactId(),
                        artifact == null ? null : artifact.versionNo(),
                        artifact == null ? null : artifact.status(),
                        artifact == null ? null : artifact.updatedAt(),
                        latestTask == null ? null : latestTask.taskId(),
                        latestTask == null ? null : latestTask.status(),
                        latestTask == null ? resolveNotStartedStatusText(artifact) : statusText(latestTask.status()),
                        latestTask == null
                                ? null
                                : displayMessage(
                                latestTask.errorCode(),
                                latestTask.errorMessage(),
                                latestTask.lastErrorCode(),
                                latestTask.lastErrorMessage()
                        ),
                        latestTask == null ? null : latestTask.errorCode(),
                        latestTask == null ? null : latestTask.lastErrorCode(),
                        latestTask == null ? null : latestTask.attemptCount(),
                        latestTask == null ? null : latestTask.maxAttempts(),
                        latestTask == null ? null : latestTask.createdAt(),
                        latestTask == null ? null : latestTask.updatedAt(),
                        latestTask == null ? null : latestTask.startedAt(),
                        latestTask == null ? null : latestTask.finishedAt()
                ));
            }

            moduleViews.add(new ProjectAgentTaskMatrixView.ProjectModuleTaskMatrixView(
                    module.moduleId(),
                    module.parentId(),
                    module.moduleKey(),
                    module.moduleName(),
                    module.moduleType(),
                    module.modulePath(),
                    module.levelNo(),
                    module.sortOrder(),
                    module.status(),
                    items
            ));
        }

        return ProjectAgentTaskMatrixView.of(
                projectId,
                moduleViews
        );
    }

    private List<ModuleRow> loadProjectModules(
            UUID projectId
    ) {
        String sql = """
                SELECT
                    id,
                    parent_id,
                    module_key,
                    module_name,
                    module_type,
                    module_path,
                    level_no,
                    sort_order,
                    status
                FROM project_module
                WHERE project_id = :projectId
                  AND status = 'ACTIVE'
                ORDER BY level_no ASC, sort_order ASC, created_at ASC
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId),
                (rs, rowNum) -> new ModuleRow(
                        getUuid(rs, "id"),
                        getUuid(rs, "parent_id"),
                        rs.getString("module_key"),
                        rs.getString("module_name"),
                        rs.getString("module_type"),
                        rs.getString("module_path"),
                        getInteger(rs, "level_no"),
                        getInteger(rs, "sort_order"),
                        rs.getString("status")
                )
        );
    }

    private Map<ModuleTaskKey, LatestTaskRow> loadLatestTaskMap(
            UUID projectId
    ) {
        String sql = """
                SELECT DISTINCT ON (module_id, task_type)
                    id,
                    module_id,
                    task_type,
                    status,
                    error_code,
                    error_message,
                    last_error_code,
                    last_error_message,
                    attempt_count,
                    max_attempts,
                    started_at,
                    finished_at,
                    created_at,
                    updated_at
                FROM agent_task
                WHERE project_id = :projectId
                  AND module_id IS NOT NULL
                  AND archived_at IS NULL
                ORDER BY module_id, task_type, created_at DESC, updated_at DESC
                """;

        Map<ModuleTaskKey, LatestTaskRow> result = new LinkedHashMap<>();

        jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId),
                rs -> {
                    UUID moduleId = getUuid(rs, "module_id");
                    String taskType = rs.getString("task_type");

                    result.put(
                            new ModuleTaskKey(moduleId, taskType),
                            new LatestTaskRow(
                                    getUuid(rs, "id"),
                                    moduleId,
                                    taskType,
                                    rs.getString("status"),
                                    rs.getString("error_code"),
                                    rs.getString("error_message"),
                                    rs.getString("last_error_code"),
                                    rs.getString("last_error_message"),
                                    getInteger(rs, "attempt_count"),
                                    getInteger(rs, "max_attempts"),
                                    getLocalDateTime(rs, "started_at"),
                                    getLocalDateTime(rs, "finished_at"),
                                    getLocalDateTime(rs, "created_at"),
                                    getLocalDateTime(rs, "updated_at")
                            )
                    );
                }
        );

        return result;
    }

    private Map<ModuleArtifactKey, CurrentArtifactRow> loadCurrentArtifactMap(
            UUID projectId
    ) {
        String sql = """
                SELECT
                    id,
                    module_id,
                    artifact_type,
                    version_no,
                    status,
                    updated_at
                FROM artifact_version
                WHERE project_id = :projectId
                  AND module_id IS NOT NULL
                  AND is_current = true
                  AND status = 'ACTIVE'
                """;

        Map<ModuleArtifactKey, CurrentArtifactRow> result = new LinkedHashMap<>();

        jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId),
                rs -> {
                    UUID moduleId = getUuid(rs, "module_id");
                    String artifactType = rs.getString("artifact_type");

                    result.put(
                            new ModuleArtifactKey(moduleId, artifactType),
                            new CurrentArtifactRow(
                                    getUuid(rs, "id"),
                                    moduleId,
                                    artifactType,
                                    getInteger(rs, "version_no"),
                                    rs.getString("status"),
                                    getLocalDateTime(rs, "updated_at")
                            )
                    );
                }
        );

        return result;
    }

    private RowMapper<ProjectAgentTaskPageView.ProjectAgentTaskPageItemView> taskPageItemRowMapper() {
        return (rs, rowNum) -> {
            String taskType = rs.getString("task_type");
            String status = rs.getString("status");
            String errorCode = rs.getString("error_code");
            String errorMessage = rs.getString("error_message");
            String lastErrorCode = rs.getString("last_error_code");
            String lastErrorMessage = rs.getString("last_error_message");

            RequirementArtifactTaskDefinition definition = findDefinitionByTaskType(taskType);

            LocalDateTime startedAt = getLocalDateTime(rs, "started_at");
            LocalDateTime finishedAt = getLocalDateTime(rs, "finished_at");

            return new ProjectAgentTaskPageView.ProjectAgentTaskPageItemView(
                    stringUuid(rs, "id"),
                    stringUuid(rs, "project_id"),
                    stringUuid(rs, "module_id"),
                    taskType,
                    definition == null ? taskType : definition.title(),
                    definition == null ? null : definition.artifactType(),
                    definition == null ? null : definition.title(),
                    status,
                    statusText(status),
                    stringUuid(rs, "result_artifact_id"),
                    errorCode,
                    truncate(errorMessage, 300),
                    lastErrorCode,
                    truncate(lastErrorMessage, 300),
                    displayMessage(
                            errorCode,
                            errorMessage,
                            lastErrorCode,
                            lastErrorMessage
                    ),
                    rs.getString("queue_name"),
                    getInteger(rs, "priority"),
                    getInteger(rs, "attempt_count"),
                    getInteger(rs, "max_attempts"),
                    getLocalDateTime(rs, "next_run_at"),
                    rs.getString("locked_by"),
                    getLocalDateTime(rs, "locked_until"),
                    getLocalDateTime(rs, "heartbeat_at"),
                    rs.getString("created_by"),
                    startedAt,
                    finishedAt,
                    durationMs(startedAt, finishedAt),
                    rs.getBoolean("has_request_payload"),
                    rs.getBoolean("has_response_payload"),
                    truncate(rs.getString("requirement_text"), 160),
                    getLocalDateTime(rs, "created_at"),
                    getLocalDateTime(rs, "updated_at")
            );
        };
    }

    private static RequirementArtifactTaskDefinition findDefinitionByTaskType(
            String taskType
    ) {
        if (taskType == null) {
            return null;
        }

        return RequirementArtifactTaskDefinition.REQUIREMENT_ARTIFACT_TASKS
                .stream()
                .filter(item -> taskType.equals(item.taskType()))
                .findFirst()
                .orElse(null);
    }

    private static String normalizeBlank(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.isBlank()) {
            return null;
        }

        return trimmed;
    }

    private static String normalizeSort(
            String sort
    ) {
        String normalized = normalizeBlank(sort);

        if (normalized == null || !ALLOWED_SORTS.contains(normalized)) {
            return "createdAtDesc";
        }

        return normalized;
    }

    private static String orderBySql(
            String sort
    ) {
        return switch (sort) {
            case "createdAtAsc" -> " ORDER BY t.created_at ASC, t.id ASC ";
            case "updatedAtDesc" -> " ORDER BY t.updated_at DESC, t.created_at DESC ";
            case "updatedAtAsc" -> " ORDER BY t.updated_at ASC, t.created_at ASC ";
            case "priorityDesc" -> " ORDER BY t.priority DESC, t.created_at DESC ";
            case "statusAsc" -> " ORDER BY t.status ASC, t.created_at DESC ";
            default -> " ORDER BY t.created_at DESC, t.id DESC ";
        };
    }

    private static void putDefaultStatusCounts(
            Map<String, Long> statusCounts
    ) {
        statusCounts.put("PENDING", 0L);
        statusCounts.put("QUEUED", 0L);
        statusCounts.put("RUNNING", 0L);
        statusCounts.put("RETRYING", 0L);
        statusCounts.put("BLOCKED", 0L);
        statusCounts.put("SUCCESS", 0L);
        statusCounts.put("PARTIAL_SUCCESS", 0L);
        statusCounts.put("FAILED", 0L);
        statusCounts.put("TIMEOUT", 0L);
        statusCounts.put("CANCELLED", 0L);
        statusCounts.put("CONFLICT", 0L);
    }

    private static String statusText(
            String status
    ) {
        if (status == null) {
            return "未知状态";
        }

        return switch (status) {
            case "PENDING" -> "待创建";
            case "QUEUED" -> "排队中";
            case "RUNNING" -> "生成中";
            case "RETRYING" -> "等待重试";
            case "BLOCKED" -> "等待依赖";
            case "SUCCESS" -> "已完成";
            case "PARTIAL_SUCCESS" -> "部分完成";
            case "FAILED" -> "生成失败";
            case "TIMEOUT" -> "生成超时";
            case "CANCELLED" -> "已取消";
            case "CONFLICT" -> "存在冲突";
            default -> status;
        };
    }

    private static String resolveNotStartedStatusText(
            CurrentArtifactRow artifact
    ) {
        if (artifact != null) {
            return "已生成";
        }

        return "未开始";
    }

    private static String displayMessage(
            String errorCode,
            String errorMessage,
            String lastErrorCode,
            String lastErrorMessage
    ) {
        String code = firstNonBlank(
                lastErrorCode,
                errorCode
        );

        String message = firstNonBlank(
                lastErrorMessage,
                errorMessage
        );

        if ("AGENT_TASK_DEPENDENCY_MISSING".equals(code)) {
            return translateMissingDependencyMessage(message);
        }

        if ("AGENT_TASK_DEPENDENCY_RESOLVED".equals(code)) {
            return "任务依赖已补齐，系统已自动重新入队。";
        }

        if ("AGENT_TASK_CANCELLED".equals(code)) {
            return "任务已取消。";
        }

        if ("AGENT_TASK_MANUAL_RETRY".equals(code)) {
            return "任务已手动重试并重新进入队列。";
        }

        if ("AGENT_TASK_TIMEOUT".equals(code)) {
            return "任务执行超时，可以稍后重试。";
        }

        if ("AGENT_TASK_RELEASED".equals(code)) {
            return "任务已释放并重新排队。";
        }

        if ("PYTHON_AGENT_CALL_FAILED".equals(code)) {
            return "调用 Python Agent 失败，请检查 Agent 服务或稍后重试。";
        }

        if (message != null && isModuleSerialMessage(message)) {
            return "同一模块已有任务正在执行，当前任务已自动排队等待。";
        }

        if (message != null && !message.isBlank()) {
            return truncate(message, 300);
        }

        return null;
    }

    private static String translateMissingDependencyMessage(
            String message
    ) {
        if (message == null || message.isBlank()) {
            return "当前缺少上游资产，请先补齐依赖后再继续。";
        }

        return message
                .replace("Missing required upstream artifacts:", "缺少上游资产：")
                .replace("MODULE_PRD", "模块 PRD")
                .replace("BUSINESS_FLOW", "业务流程")
                .replace("PAGE_SPEC", "页面规范")
                .replace("DATA_MODEL", "数据模型")
                .replace("API_CONTRACT", "接口契约")
                .replace("TEST_SPEC", "测试规格")
                .replace("DEV_HANDOFF_PACKAGE", "开发交接包");
    }

    private static boolean isModuleSerialMessage(
            String message
    ) {
        return message.contains("Another task in the same module is running")
                || message.contains("module serial policy")
                || message.contains("postponed due to the module")
                || message.contains("Task deferred by module serial policy");
    }

    private static String firstNonBlank(
            String first,
            String second
    ) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        if (second != null && !second.isBlank()) {
            return second;
        }

        return null;
    }

    private static String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...";
    }

    private static Long durationMs(
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }

        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private static UUID getUuid(
            ResultSet rs,
            String columnName
    ) throws java.sql.SQLException {
        Object value = rs.getObject(columnName);

        if (value == null) {
            return null;
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        return UUID.fromString(String.valueOf(value));
    }

    private static String stringUuid(
            ResultSet rs,
            String columnName
    ) throws java.sql.SQLException {
        UUID uuid = getUuid(rs, columnName);

        return uuid == null ? null : uuid.toString();
    }

    private static Integer getInteger(
            ResultSet rs,
            String columnName
    ) throws java.sql.SQLException {
        int value = rs.getInt(columnName);

        if (rs.wasNull()) {
            return null;
        }

        return value;
    }

    private static LocalDateTime getLocalDateTime(
            ResultSet rs,
            String columnName
    ) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);

        if (timestamp == null) {
            return null;
        }

        return timestamp.toLocalDateTime();
    }

    private record ModuleRow(
            UUID moduleId,
            UUID parentId,
            String moduleKey,
            String moduleName,
            String moduleType,
            String modulePath,
            Integer levelNo,
            Integer sortOrder,
            String status
    ) {
    }

    private record ModuleTaskKey(
            UUID moduleId,
            String taskType
    ) {
    }

    private record ModuleArtifactKey(
            UUID moduleId,
            String artifactType
    ) {
    }

    private record LatestTaskRow(
            UUID taskId,
            UUID moduleId,
            String taskType,
            String status,
            String errorCode,
            String errorMessage,
            String lastErrorCode,
            String lastErrorMessage,
            Integer attemptCount,
            Integer maxAttempts,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    private record CurrentArtifactRow(
            UUID artifactId,
            UUID moduleId,
            String artifactType,
            Integer versionNo,
            String status,
            LocalDateTime updatedAt
    ) {
    }
}