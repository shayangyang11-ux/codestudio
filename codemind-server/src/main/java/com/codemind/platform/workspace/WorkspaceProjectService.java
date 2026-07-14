package com.codemind.platform.workspace;

import com.codemind.platform.common.JsonbHelper;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.workspace.dto.CreateProjectRequest;
import com.codemind.platform.workspace.dto.ProjectSummaryResponse;
import com.codemind.platform.workspace.dto.ProjectWorkspaceStateResponse;
import com.codemind.platform.workspace.dto.SaveProjectWorkspaceStateRequest;
import com.codemind.platform.workspace.dto.WorkspaceSummaryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkspaceProjectService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public WorkspaceProjectService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public List<WorkspaceSummaryResponse> listCurrentUserWorkspaces() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        String sql = """
                SELECT
                    w.id AS workspace_id,
                    w.workspace_code,
                    w.workspace_name,
                    w.workspace_type,
                    wm.member_role,
                    w.status
                FROM workspace_member wm
                JOIN workspace w ON w.id = wm.workspace_id
                WHERE wm.user_id = :userId
                  AND wm.status = 'ACTIVE'
                  AND w.status = 'ACTIVE'
                ORDER BY
                    CASE WHEN w.workspace_type = 'PERSONAL' THEN 0 ELSE 1 END,
                    w.created_at ASC
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("userId", currentUser.userId()),
                workspaceSummaryRowMapper()
        );
    }

    public List<ProjectSummaryResponse> listProjectsByWorkspace(UUID workspaceId) {
        WorkspaceRecord workspace = resolveAccessibleWorkspace(workspaceId);

        String sql = """
                SELECT
                    p.id AS project_id,
                    p.project_code,
                    p.project_name,
                    p.description,
                    p.status,
                    p.workspace_id,
                    w.workspace_code,
                    p.visibility,
                    COALESCE(module_stat.module_count, 0) AS module_count,
                    COALESCE(artifact_stat.artifact_count, 0) AS artifact_count,
                    artifact_stat.latest_artifact_at,
                    p.created_at,
                    p.updated_at
                FROM project p
                JOIN workspace w ON w.id = p.workspace_id
                JOIN workspace_member wm
                    ON wm.workspace_id = p.workspace_id
                   AND wm.user_id = :userId
                   AND wm.status = 'ACTIVE'
                LEFT JOIN project_member pm
                    ON pm.project_id = p.id
                   AND pm.user_id = :userId
                   AND pm.status = 'ACTIVE'
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS module_count
                    FROM project_module m
                    WHERE m.project_id = p.id
                ) module_stat ON TRUE
                LEFT JOIN LATERAL (
                    SELECT
                        COUNT(*) AS artifact_count,
                        MAX(a.updated_at) AS latest_artifact_at
                    FROM artifact_version a
                    WHERE a.project_id = p.id
                ) artifact_stat ON TRUE
                WHERE p.workspace_id = :workspaceId
                  AND p.status = 'ACTIVE'
                  AND (
                        p.visibility = 'WORKSPACE'
                        OR pm.user_id IS NOT NULL
                  )
                ORDER BY p.updated_at DESC, p.created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspace.id())
                        .addValue("userId", currentUserProvider.getCurrentUser().userId()),
                projectSummaryRowMapper()
        );
    }

    @Transactional
    public ProjectSummaryResponse createProject(
            UUID workspaceId,
            CreateProjectRequest request
    ) {
        WorkspaceRecord workspace = resolveAccessibleWorkspace(workspaceId);
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        String projectCode = normalizeRequiredText(request.projectCode(), "projectCode");
        String projectName = normalizeRequiredText(request.projectName(), "projectName");
        String description = normalizeOptionalText(request.description());

        String sql = """
                INSERT INTO project (
                    tenant_id,
                    project_code,
                    project_name,
                    description,
                    status,
                    tech_stack,
                    workspace_id,
                    owner_user_id,
                    visibility,
                    created_by,
                    updated_by
                )
                VALUES (
                    :tenantId,
                    :projectCode,
                    :projectName,
                    :description,
                    'ACTIVE',
                    :techStack,
                    :workspaceId,
                    :ownerUserId,
                    'WORKSPACE',
                    :createdBy,
                    :updatedBy
                )
                RETURNING id
                """;

        UUID projectId = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("tenantId", workspace.workspaceCode())
                        .addValue("projectCode", projectCode)
                        .addValue("projectName", projectName)
                        .addValue("description", description)
                        .addValue("techStack", JsonbHelper.toJsonb(
                                request.techStack() == null ? Map.of() : request.techStack(),
                                objectMapper
                        ))
                        .addValue("workspaceId", workspace.id())
                        .addValue("ownerUserId", currentUser.userId())
                        .addValue("createdBy", currentUser.username())
                        .addValue("updatedBy", currentUser.username()),
                UUID.class
        );

        String memberSql = """
                INSERT INTO project_member (
                    project_id,
                    user_id,
                    project_role,
                    status
                )
                VALUES (
                    :projectId,
                    :userId,
                    'OWNER',
                    'ACTIVE'
                )
                ON CONFLICT (project_id, user_id)
                DO UPDATE SET
                    project_role = EXCLUDED.project_role,
                    status = EXCLUDED.status,
                    updated_at = CURRENT_TIMESTAMP
                """;

        jdbcTemplate.update(
                memberSql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("userId", currentUser.userId())
        );

        return getProjectSummary(workspace.id(), projectCode);
    }

    public ProjectWorkspaceStateResponse getProjectWorkspaceState(
            UUID workspaceId,
            String projectCode
    ) {
        ProjectRecord project = resolveAccessibleProject(workspaceId, projectCode);
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        String sql = """
                SELECT
                    workspace_id,
                    project_id,
                    user_id,
                    selected_module_id,
                    selected_artifact_type,
                    selected_artifact_id,
                    active_agent_tab,
                    ui_state,
                    updated_at
                FROM project_workspace_state
                WHERE project_id = :projectId
                  AND user_id = :userId
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("projectId", project.id())
                            .addValue("userId", currentUser.userId()),
                    workspaceStateRowMapper(true)
            );
        } catch (EmptyResultDataAccessException e) {
            return new ProjectWorkspaceStateResponse(
                    false,
                    workspaceId,
                    project.id(),
                    currentUser.userId(),
                    null,
                    null,
                    null,
                    "requirement",
                    Map.of(),
                    null
            );
        }
    }

    @Transactional
    public ProjectWorkspaceStateResponse saveProjectWorkspaceState(
            UUID workspaceId,
            String projectCode,
            SaveProjectWorkspaceStateRequest request
    ) {
        ProjectRecord project = resolveAccessibleProject(workspaceId, projectCode);
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        validateSelectedReferences(project.id(), request);

        String activeAgentTab = normalizeOptionalText(request.activeAgentTab());
        if (activeAgentTab == null || activeAgentTab.isBlank()) {
            activeAgentTab = "requirement";
        }

        Map<String, Object> uiState = request.uiState() == null
                ? Map.of()
                : request.uiState();

        String sql = """
                INSERT INTO project_workspace_state (
                    workspace_id,
                    project_id,
                    user_id,
                    selected_module_id,
                    selected_artifact_type,
                    selected_artifact_id,
                    active_agent_tab,
                    ui_state
                )
                VALUES (
                    :workspaceId,
                    :projectId,
                    :userId,
                    :selectedModuleId,
                    :selectedArtifactType,
                    :selectedArtifactId,
                    :activeAgentTab,
                    :uiState
                )
                ON CONFLICT (user_id, project_id)
                DO UPDATE SET
                    selected_module_id = EXCLUDED.selected_module_id,
                    selected_artifact_type = EXCLUDED.selected_artifact_type,
                    selected_artifact_id = EXCLUDED.selected_artifact_id,
                    active_agent_tab = EXCLUDED.active_agent_tab,
                    ui_state = EXCLUDED.ui_state,
                    updated_at = CURRENT_TIMESTAMP
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectId", project.id())
                        .addValue("userId", currentUser.userId())
                        .addValue("selectedModuleId", request.selectedModuleId())
                        .addValue("selectedArtifactType", normalizeOptionalText(request.selectedArtifactType()))
                        .addValue("selectedArtifactId", request.selectedArtifactId())
                        .addValue("activeAgentTab", activeAgentTab)
                        .addValue("uiState", JsonbHelper.toJsonb(uiState, objectMapper))
        );

        return getProjectWorkspaceState(workspaceId, projectCode);
    }

    private WorkspaceRecord resolveAccessibleWorkspace(UUID workspaceId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        String sql = """
                SELECT
                    w.id,
                    w.workspace_code
                FROM workspace w
                JOIN workspace_member wm ON wm.workspace_id = w.id
                WHERE w.id = :workspaceId
                  AND wm.user_id = :userId
                  AND wm.status = 'ACTIVE'
                  AND w.status = 'ACTIVE'
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("workspaceId", workspaceId)
                            .addValue("userId", currentUser.userId()),
                    (rs, rowNum) -> new WorkspaceRecord(
                            rs.getObject("id", UUID.class),
                            rs.getString("workspace_code")
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Workspace not accessible: " + workspaceId);
        }
    }

    private ProjectRecord resolveAccessibleProject(
            UUID workspaceId,
            String projectCode
    ) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        String sql = """
                SELECT
                    p.id,
                    p.project_code,
                    p.workspace_id
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
                            .addValue("userId", currentUser.userId()),
                    (rs, rowNum) -> new ProjectRecord(
                            rs.getObject("id", UUID.class),
                            rs.getString("project_code"),
                            rs.getObject("workspace_id", UUID.class)
                    )
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

    private ProjectSummaryResponse getProjectSummary(
            UUID workspaceId,
            String projectCode
    ) {
        List<ProjectSummaryResponse> projects = listProjectsByWorkspace(workspaceId);

        return projects.stream()
                .filter(project -> project.projectCode().equals(projectCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Project not found after creation: " + projectCode));
    }

    private void validateSelectedReferences(
            UUID projectId,
            SaveProjectWorkspaceStateRequest request
    ) {
        if (request.selectedModuleId() != null) {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM project_module
                    WHERE id = :moduleId
                      AND project_id = :projectId
                    """,
                    new MapSqlParameterSource()
                            .addValue("moduleId", request.selectedModuleId())
                            .addValue("projectId", projectId),
                    Integer.class
            );

            if (count == null || count == 0) {
                throw new IllegalArgumentException("Selected module does not belong to current project.");
            }
        }

        if (request.selectedArtifactId() != null) {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM artifact_version
                    WHERE id = :artifactId
                      AND project_id = :projectId
                    """,
                    new MapSqlParameterSource()
                            .addValue("artifactId", request.selectedArtifactId())
                            .addValue("projectId", projectId),
                    Integer.class
            );

            if (count == null || count == 0) {
                throw new IllegalArgumentException("Selected artifact does not belong to current project.");
            }
        }
    }

    private RowMapper<WorkspaceSummaryResponse> workspaceSummaryRowMapper() {
        return (rs, rowNum) -> new WorkspaceSummaryResponse(
                rs.getObject("workspace_id", UUID.class),
                rs.getString("workspace_code"),
                rs.getString("workspace_name"),
                rs.getString("workspace_type"),
                rs.getString("member_role"),
                rs.getString("status")
        );
    }

    private RowMapper<ProjectSummaryResponse> projectSummaryRowMapper() {
        return (rs, rowNum) -> new ProjectSummaryResponse(
                rs.getObject("project_id", UUID.class),
                rs.getString("project_code"),
                rs.getString("project_name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("workspace_code"),
                rs.getString("visibility"),
                rs.getLong("module_count"),
                rs.getLong("artifact_count"),
                toLocalDateTime(rs.getTimestamp("latest_artifact_at")),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private RowMapper<ProjectWorkspaceStateResponse> workspaceStateRowMapper(boolean exists) {
        return (rs, rowNum) -> new ProjectWorkspaceStateResponse(
                exists,
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("selected_module_id", UUID.class),
                rs.getString("selected_artifact_type"),
                rs.getObject("selected_artifact_id", UUID.class),
                rs.getString("active_agent_tab"),
                readJsonObject(rs.getObject("ui_state")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
        );
    }

    private Map<String, Object> readJsonObject(Object value) {
        if (value == null) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    String.valueOf(value),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read jsonb value.", e);
        }
    }

    private String normalizeRequiredText(
            String value,
            String fieldName
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record WorkspaceRecord(
            UUID id,
            String workspaceCode
    ) {
    }

    private record ProjectRecord(
            UUID id,
            String projectCode,
            UUID workspaceId
    ) {
    }
}