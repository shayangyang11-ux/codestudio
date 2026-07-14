package com.codemind.platform.project;

import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProjectAccessService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CurrentUserProvider currentUserProvider;
    private final ProjectRepository projectRepository;
    private final ProjectModuleRepository projectModuleRepository;

    public ProjectAccessService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CurrentUserProvider currentUserProvider,
            ProjectRepository projectRepository,
            ProjectModuleRepository projectModuleRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserProvider = currentUserProvider;
        this.projectRepository = projectRepository;
        this.projectModuleRepository = projectModuleRepository;
    }

    public ProjectAccess resolveAccessibleProject(
            UUID workspaceId,
            String projectCode
    ) {
        UUID userId = currentUserProvider.getCurrentUser().userId();

        String sql = """
                SELECT
                    p.id AS project_id,
                    p.project_code,
                    p.workspace_id,
                    p.tenant_id,
                    w.workspace_code
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
                    (rs, rowNum) -> new ProjectAccess(
                            rs.getObject("project_id", UUID.class),
                            rs.getString("project_code"),
                            rs.getObject("workspace_id", UUID.class),
                            rs.getString("tenant_id"),
                            rs.getString("workspace_code")
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

    public ProjectAccess resolveAccessibleProjectByCode(
            String projectCode
    ) {
        UUID userId = currentUserProvider.getCurrentUser().userId();

        String sql = """
                SELECT
                    p.id AS project_id,
                    p.project_code,
                    p.workspace_id,
                    p.tenant_id,
                    w.workspace_code
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
                    (rs, rowNum) -> new ProjectAccess(
                            rs.getObject("project_id", UUID.class),
                            rs.getString("project_code"),
                            rs.getObject("workspace_id", UUID.class),
                            rs.getString("tenant_id"),
                            rs.getString("workspace_code")
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Project not accessible: " + projectCode);
        }
    }

    public ProjectAccess resolveAccessibleProjectById(
            UUID projectId
    ) {
        UUID userId = currentUserProvider.getCurrentUser().userId();

        String sql = """
            SELECT
                p.id AS project_id,
                p.project_code,
                p.workspace_id,
                p.tenant_id,
                w.workspace_code
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
            WHERE p.id = :projectId
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
                            .addValue("projectId", projectId)
                            .addValue("userId", userId),
                    (rs, rowNum) -> new ProjectAccess(
                            rs.getObject("project_id", UUID.class),
                            rs.getString("project_code"),
                            rs.getObject("workspace_id", UUID.class),
                            rs.getString("tenant_id"),
                            rs.getString("workspace_code")
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Project not accessible. projectId=" + projectId);
        }
    }

    public ProjectEntity resolveAccessibleProjectEntity(
            UUID workspaceId,
            String projectCode
    ) {
        ProjectAccess access = resolveAccessibleProject(workspaceId, projectCode);

        return projectRepository.findById(access.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Project entity not found: " + access.projectId()));
    }

    public ProjectEntity resolveAccessibleProjectEntityByCode(
            String projectCode
    ) {
        ProjectAccess access = resolveAccessibleProjectByCode(projectCode);

        return projectRepository.findById(access.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Project entity not found: " + access.projectId()));
    }

    public ProjectModuleEntity resolveAccessibleModule(
            UUID projectId,
            UUID moduleId
    ) {
        ProjectModuleEntity module = projectModuleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module not found: " + moduleId));

        if (!projectId.equals(module.getProjectId())) {
            throw new IllegalArgumentException(
                    "Module does not belong to current project. projectId="
                            + projectId
                            + ", moduleId="
                            + moduleId
            );
        }

        return module;
    }

    public record ProjectAccess(
            UUID projectId,
            String projectCode,
            UUID workspaceId,
            String tenantId,
            String workspaceCode
    ) {
    }
}