package com.codemind.platform.uicontract;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.common.JsonbHelper;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.theme.ProjectThemeProfileResponse;
import com.codemind.platform.theme.ProjectThemeProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConfirmedUiContractSnapshotService {

    private static final String ARTIFACT_TYPE = "UI_CONTRACT";

    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ProjectThemeProfileService projectThemeProfileService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public ConfirmedUiContractSnapshotService(
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            ProjectThemeProfileService projectThemeProfileService,
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.projectThemeProfileService = projectThemeProfileService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public ConfirmedUiContractSnapshotResponse confirm(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            ConfirmUiContractSnapshotRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        ArtifactVersionEntity artifact = resolveUiContractArtifact(
                project.getId(),
                module.getId(),
                request == null ? null : request.artifactId()
        );

        Map<String, Object> uiContract = artifact.getContent();

        if (uiContract == null || uiContract.isEmpty()) {
            throw new IllegalArgumentException(
                    "当前 UI_CONTRACT 内容为空，无法确认页面效果。"
            );
        }

        ProjectThemeProfileResponse themeProfile =
                projectThemeProfileService.getOrCreateThemeProfile(
                        workspaceId,
                        projectCode
                );

        Map<String, Object> themeProfileSnapshot = objectMapper.convertValue(
                themeProfile,
                new TypeReference<Map<String, Object>>() {
                }
        );

        Map<String, Object> pageIdentity = extractPageIdentity(
                uiContract,
                module
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UUID snapshotId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                UPDATE confirmed_ui_contract_snapshot
                SET is_current = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE project_id = :projectId
                  AND module_id = :moduleId
                  AND is_current = TRUE
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", project.getId())
                        .addValue("moduleId", module.getId())
        );

        jdbcTemplate.update(
                """
                INSERT INTO confirmed_ui_contract_snapshot (
                    id,
                    project_id,
                    module_id,
                    artifact_id,
                    artifact_type,
                    artifact_version_no,
                    page_id,
                    page_key,
                    page_name,
                    ui_contract,
                    theme_profile_snapshot,
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
                    :artifactId,
                    :artifactType,
                    :artifactVersionNo,
                    :pageId,
                    :pageKey,
                    :pageName,
                    :uiContract,
                    :themeProfileSnapshot,
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
                        .addValue("moduleId", module.getId())
                        .addValue("artifactId", artifact.getId())
                        .addValue("artifactType", ARTIFACT_TYPE)
                        .addValue("artifactVersionNo", artifact.getVersionNo())
                        .addValue("pageId", pageIdentity.get("pageId"))
                        .addValue("pageKey", pageIdentity.get("pageKey"))
                        .addValue("pageName", pageIdentity.get("pageName"))
                        .addValue("uiContract", JsonbHelper.toJsonb(uiContract, objectMapper))
                        .addValue("themeProfileSnapshot", JsonbHelper.toJsonb(themeProfileSnapshot, objectMapper))
                        .addValue("confirmationNote", normalizeNote(
                                request == null ? null : request.confirmationNote()
                        ))
                        .addValue("confirmedBy", currentUser.username())
        );

        return findCurrent(
                project.getId(),
                module.getId()
        ).orElseThrow(() -> new IllegalStateException(
                "UI_CONTRACT confirmation snapshot was not created."
        ));
    }

    @Transactional(readOnly = true)
    public Optional<ConfirmedUiContractSnapshotResponse> getCurrent(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        return findCurrent(
                project.getId(),
                module.getId()
        );
    }

    private ArtifactVersionEntity resolveUiContractArtifact(
            UUID projectId,
            UUID moduleId,
            UUID artifactId
    ) {
        if (artifactId != null) {
            return artifactVersionRepository
                    .findByIdAndProjectIdAndModuleIdAndArtifactType(
                            artifactId,
                            projectId,
                            moduleId,
                            ARTIFACT_TYPE
                    )
                    .orElseThrow(() -> new IllegalArgumentException(
                            "指定的 UI_CONTRACT 版本不存在或不属于当前页面。"
                    ));
        }

        return artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        ARTIFACT_TYPE
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "当前页面尚未生成 UI_CONTRACT，无法确认页面效果。"
                ));
    }

    private Optional<ConfirmedUiContractSnapshotResponse> findCurrent(
            UUID projectId,
            UUID moduleId
    ) {
        try {
            ConfirmedUiContractSnapshotResponse response =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT *
                            FROM confirmed_ui_contract_snapshot
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

    private ConfirmedUiContractSnapshotResponse mapResponse(
            ResultSet rs
    ) throws SQLException {
        return new ConfirmedUiContractSnapshotResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("module_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getString("artifact_type"),
                rs.getObject("artifact_version_no", Integer.class),
                rs.getString("page_id"),
                rs.getString("page_key"),
                rs.getString("page_name"),
                readJsonMap(rs, "ui_contract"),
                readJsonMap(rs, "theme_profile_snapshot"),
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

    private Map<String, Object> extractPageIdentity(
            Map<String, Object> uiContract,
            ProjectModuleEntity module
    ) {
        Map<String, Object> identity = asMap(uiContract.get("identity"));
        Map<String, Object> page = asMap(uiContract.get("page"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "pageId",
                firstText(
                        identity.get("pageId"),
                        identity.get("page_id"),
                        page.get("pageId"),
                        page.get("page_id"),
                        module.getId()
                )
        );
        result.put(
                "pageKey",
                firstText(
                        identity.get("pageKey"),
                        identity.get("page_key"),
                        page.get("pageKey"),
                        page.get("page_key"),
                        module.getModuleKey()
                )
        );
        result.put(
                "pageName",
                firstText(
                        identity.get("pageName"),
                        identity.get("page_name"),
                        page.get("pageName"),
                        page.get("page_name"),
                        page.get("title"),
                        module.getModuleName()
                )
        );
        return result;
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

    private String firstText(
            Object... values
    ) {
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
