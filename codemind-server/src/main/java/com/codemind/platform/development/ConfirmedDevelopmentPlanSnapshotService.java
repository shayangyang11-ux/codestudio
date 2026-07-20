package com.codemind.platform.development;

import com.codemind.platform.common.JsonbHelper;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConfirmedDevelopmentPlanSnapshotService {

    private final ProjectAccessService projectAccessService;
    private final DevelopmentPlanService developmentPlanService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public ConfirmedDevelopmentPlanSnapshotService(
            ProjectAccessService projectAccessService,
            DevelopmentPlanService developmentPlanService,
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider
    ) {
        this.projectAccessService = projectAccessService;
        this.developmentPlanService = developmentPlanService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public ConfirmedDevelopmentPlanSnapshotResponse confirm(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            ConfirmDevelopmentPlanSnapshotRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        Map<String, Object> plan = resolvePlan(
                workspaceId,
                projectCode,
                moduleId,
                request
        );

        if (plan.isEmpty()) {
            throw new IllegalArgumentException(
                    "当前 Development Plan 内容为空，无法确认。"
            );
        }

        String planChecksum = normalizeChecksum(
                request == null ? null : request.planChecksum(),
                plan
        );

        Map<String, Object> handoffContextSnapshot =
                asMap(plan.get("handoffContext"));

        Map<String, Object> readinessSnapshot =
                asMap(handoffContextSnapshot.get("readiness"));

        Map<String, Object> sourceArtifactVersions =
                buildSourceArtifactVersions(handoffContextSnapshot);

        UUID confirmedUiContractSnapshotId =
                extractConfirmedUiContractSnapshotId(handoffContextSnapshot);

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UUID snapshotId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                UPDATE confirmed_development_plan_snapshot
                SET is_current = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE project_id = :projectId
                  AND module_id = :moduleId
                  AND is_current = TRUE
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", project.getId())
                        .addValue("moduleId", moduleId)
        );

        jdbcTemplate.update(
                """
                INSERT INTO confirmed_development_plan_snapshot (
                    id,
                    project_id,
                    module_id,
                    plan_checksum,
                    plan_status,
                    plan_json,
                    readiness_snapshot,
                    handoff_context_snapshot,
                    source_artifact_versions,
                    confirmed_ui_contract_snapshot_id,
                    confirmation_note,
                    status,
                    is_current,
                    confirmed_by,
                    confirmed_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :projectId,
                    :moduleId,
                    :planChecksum,
                    'CONFIRMED',
                    :planJson,
                    :readinessSnapshot,
                    :handoffContextSnapshot,
                    :sourceArtifactVersions,
                    :confirmedUiContractSnapshotId,
                    :confirmationNote,
                    'CONFIRMED',
                    TRUE,
                    :confirmedBy,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", snapshotId)
                        .addValue("projectId", project.getId())
                        .addValue("moduleId", moduleId)
                        .addValue("planChecksum", planChecksum)
                        .addValue("planJson", JsonbHelper.toJsonb(plan, objectMapper))
                        .addValue("readinessSnapshot", JsonbHelper.toJsonb(readinessSnapshot, objectMapper))
                        .addValue("handoffContextSnapshot", JsonbHelper.toJsonb(handoffContextSnapshot, objectMapper))
                        .addValue("sourceArtifactVersions", JsonbHelper.toJsonb(sourceArtifactVersions, objectMapper))
                        .addValue("confirmedUiContractSnapshotId", confirmedUiContractSnapshotId)
                        .addValue("confirmationNote", normalizeNote(
                                request == null ? null : request.confirmationNote()
                        ))
                        .addValue("confirmedBy", currentUser.username())
        );

        return findCurrent(
                project.getId(),
                moduleId
        ).orElseThrow(() -> new IllegalStateException(
                "Development Plan confirmation snapshot was not created."
        ));
    }

    @Transactional(readOnly = true)
    public Optional<ConfirmedDevelopmentPlanSnapshotResponse> getCurrent(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        return findCurrent(
                project.getId(),
                moduleId
        );
    }

    private Map<String, Object> resolvePlan(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            ConfirmDevelopmentPlanSnapshotRequest request
    ) {
        if (request != null && request.plan() != null && !request.plan().isEmpty()) {
            return new LinkedHashMap<>(request.plan());
        }

        DevelopmentPlanResponse generatedPlan =
                developmentPlanService.generatePlan(
                        workspaceId,
                        projectCode,
                        moduleId,
                        new GenerateDevelopmentPlanRequest(
                                null,
                                null,
                                null
                        )
                );

        return objectMapper.convertValue(
                generatedPlan,
                new TypeReference<Map<String, Object>>() {
                }
        );
    }

    private Optional<ConfirmedDevelopmentPlanSnapshotResponse> findCurrent(
            UUID projectId,
            UUID moduleId
    ) {
        try {
            ConfirmedDevelopmentPlanSnapshotResponse response =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT *
                            FROM confirmed_development_plan_snapshot
                            WHERE project_id = :projectId
                              AND module_id = :moduleId
                              AND is_current = TRUE
                            ORDER BY confirmed_at DESC
                            LIMIT 1
                            """,
                            new MapSqlParameterSource()
                                    .addValue("projectId", projectId)
                                    .addValue("moduleId", moduleId),
                            (rs, rowNum) -> mapResponse(rs)
                    );

            return Optional.ofNullable(response);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private ConfirmedDevelopmentPlanSnapshotResponse mapResponse(
            ResultSet rs
    ) throws SQLException {
        return new ConfirmedDevelopmentPlanSnapshotResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("module_id", UUID.class),
                rs.getString("plan_checksum"),
                rs.getString("plan_status"),
                readJsonMap(rs, "plan_json"),
                readJsonMap(rs, "readiness_snapshot"),
                readJsonMap(rs, "handoff_context_snapshot"),
                readJsonMap(rs, "source_artifact_versions"),
                rs.getObject("confirmed_ui_contract_snapshot_id", UUID.class),
                rs.getString("confirmation_note"),
                rs.getString("status"),
                rs.getBoolean("is_current"),
                rs.getString("confirmed_by"),
                rs.getObject("confirmed_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
        );
    }

    private Map<String, Object> readJsonMap(
            ResultSet rs,
            String column
    ) throws SQLException {
        Object value = rs.getObject(column);

        if (value == null) {
            return new LinkedHashMap<>();
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(
                        String.valueOf(entry.getKey()),
                        entry.getValue()
                );
            }

            return result;
        }

        try {
            return objectMapper.readValue(
                    value.toString(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception e) {
            throw new SQLException(
                    "Failed to parse JSON column: " + column,
                    e
            );
        }
    }

    private Map<String, Object> buildSourceArtifactVersions(
            Map<String, Object> handoffContextSnapshot
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object artifactsValue = handoffContextSnapshot.get("artifacts");

        if (!(artifactsValue instanceof List<?> artifacts)) {
            return result;
        }

        for (Object item : artifacts) {
            Map<String, Object> artifact = asMap(item);
            String artifactType = text(artifact.get("artifactType"));

            if (artifactType.isBlank()) {
                continue;
            }

            Map<String, Object> version = new LinkedHashMap<>();
            version.put("artifactId", artifact.get("artifactId"));
            version.put("versionNo", artifact.get("versionNo"));
            version.put("status", artifact.get("status"));
            version.put("required", artifact.get("required"));

            result.put(
                    artifactType,
                    version
            );
        }

        return result;
    }

    private UUID extractConfirmedUiContractSnapshotId(
            Map<String, Object> handoffContextSnapshot
    ) {
        Map<String, Object> readiness = asMap(
                handoffContextSnapshot.get("readiness")
        );

        Map<String, Object> snapshot = asMap(
                readiness.get("uiContractSnapshot")
        );

        String snapshotId = text(snapshot.get("snapshotId"));

        if (snapshotId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(snapshotId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizeChecksum(
            String checksum,
            Map<String, Object> plan
    ) {
        if (checksum != null && !checksum.isBlank()) {
            return checksum.trim();
        }

        try {
            String json = objectMapper.writeValueAsString(plan);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(json.getBytes(StandardCharsets.UTF_8));

            StringBuilder builder = new StringBuilder();

            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }

            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to calculate Development Plan checksum.",
                    e
            );
        }
    }

    private Map<String, Object> asMap(
            Object value
    ) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(
                    String.valueOf(entry.getKey()),
                    entry.getValue()
            );
        }

        return result;
    }

    private String text(
            Object value
    ) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value).trim();
    }

    private String normalizeNote(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() > 2000) {
            return trimmed.substring(0, 2000);
        }

        return trimmed;
    }
}
