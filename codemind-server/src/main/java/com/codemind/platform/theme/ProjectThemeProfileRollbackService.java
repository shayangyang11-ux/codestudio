package com.codemind.platform.theme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProjectThemeProfileRollbackService {

    private static final List<String> TRACKED_FIELDS = List.of(
            "industry",
            "themeKey",
            "visualTone",
            "primaryColor",
            "secondaryColor",
            "accentColor",
            "density",
            "radius",
            "loginLayout",
            "navigationStyle",
            "darkModeEnabled",
            "locked",
            "notes"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProjectThemeProfileService projectThemeProfileService;

    public ProjectThemeProfileRollbackService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ProjectThemeProfileService projectThemeProfileService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.projectThemeProfileService = projectThemeProfileService;
    }

    @Transactional
    public ProjectThemeProfileRollbackResponse rollbackToBeforeProfile(
            UUID workspaceId,
            String projectCode,
            UUID sourceLogId
    ) {
        ProjectThemeProfileApplicationLogResponse sourceLog = findLog(
                workspaceId,
                projectCode,
                sourceLogId
        );

        Map<String, Object> targetProfile = new LinkedHashMap<>(sourceLog.getBeforeProfile());

        if (targetProfile.isEmpty()) {
            throw new IllegalArgumentException(
                    "Selected theme profile log does not contain beforeProfile and cannot be rolled back."
            );
        }

        ProjectThemeProfileResponse currentProfile =
                projectThemeProfileService.getOrCreateThemeProfile(
                        workspaceId,
                        projectCode
                );

        UpdateProjectThemeProfileRequest rollbackRequest = toUpdateRequest(targetProfile);
        Map<String, Object> rollbackPayload = updateRequestToMap(rollbackRequest);

        ProjectThemeProfileResponse resultProfile =
                projectThemeProfileService.updateThemeProfile(
                        workspaceId,
                        projectCode,
                        rollbackRequest
                );

        ProjectThemeProfileApplicationLogResponse rollbackLog = insertRollbackLog(
                workspaceId,
                projectCode,
                sourceLog,
                profileToMap(currentProfile),
                rollbackPayload,
                profileToMap(resultProfile)
        );

        ProjectThemeProfileRollbackResponse response =
                new ProjectThemeProfileRollbackResponse();
        response.setSourceLog(sourceLog);
        response.setRollbackLog(rollbackLog);
        response.setResultProfile(resultProfile);
        return response;
    }

    private ProjectThemeProfileApplicationLogResponse findLog(
            UUID workspaceId,
            String projectCode,
            UUID logId
    ) {
        String sql = """
                SELECT
                    id,
                    workspace_id,
                    project_id,
                    project_code,
                    source,
                    style_text,
                    target_page_type,
                    agent_mode,
                    confidence,
                    summary,
                    before_profile,
                    suggestion,
                    update_payload,
                    result_profile,
                    changed_fields,
                    reasons,
                    warnings,
                    created_at
                FROM project_theme_profile_application_log
                WHERE id = :id
                  AND workspace_id = :workspaceId
                  AND project_code = :projectCode
                LIMIT 1
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("id", logId)
                            .addValue("workspaceId", workspaceId)
                            .addValue("projectCode", projectCode),
                    (rs, rowNum) -> {
                        ProjectThemeProfileApplicationLogResponse response =
                                new ProjectThemeProfileApplicationLogResponse();
                        response.setId((UUID) rs.getObject("id"));
                        response.setWorkspaceId((UUID) rs.getObject("workspace_id"));
                        response.setProjectId((UUID) rs.getObject("project_id"));
                        response.setProjectCode(rs.getString("project_code"));
                        response.setSource(rs.getString("source"));
                        response.setStyleText(rs.getString("style_text"));
                        response.setTargetPageType(rs.getString("target_page_type"));
                        response.setAgentMode(rs.getString("agent_mode"));
                        response.setConfidence(rs.getObject("confidence") == null ? null : rs.getDouble("confidence"));
                        response.setSummary(rs.getString("summary"));
                        response.setBeforeProfile(readJsonMap(rs.getObject("before_profile")));
                        response.setSuggestion(readJsonMap(rs.getObject("suggestion")));
                        response.setUpdatePayload(readJsonMap(rs.getObject("update_payload")));
                        response.setResultProfile(readJsonMap(rs.getObject("result_profile")));
                        response.setChangedFields(readJsonStringList(rs.getObject("changed_fields")));
                        response.setReasons(readJsonStringList(rs.getObject("reasons")));
                        response.setWarnings(readJsonStringList(rs.getObject("warnings")));
                        response.setCreatedAt(toCreatedAtString(rs.getObject("created_at")));
                        return response;
                    }
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException(
                    "Theme profile application log not found. logId=" + logId
            );
        }
    }

    private ProjectThemeProfileApplicationLogResponse insertRollbackLog(
            UUID workspaceId,
            String projectCode,
            ProjectThemeProfileApplicationLogResponse sourceLog,
            Map<String, Object> beforeProfile,
            Map<String, Object> updatePayload,
            Map<String, Object> resultProfile
    ) {
        UUID id = UUID.randomUUID();
        UUID projectId = readUuid(resultProfile, "projectId");
        List<String> changedFields = resolveChangedFields(beforeProfile, updatePayload, resultProfile);
        List<String> reasons = List.of(
                "用户从风格应用记录发起回滚。",
                "回滚目标为所选记录应用前的 beforeProfile。"
        );
        List<String> warnings = List.of(
                "回滚只恢复项目级 Theme Profile，不会自动重写已生成的 UI_CONTRACT 内容；重新生成或重新打开预览时会使用新的项目风格。"
        );
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("rollbackFromLogId", sourceLog.getId() == null ? null : sourceLog.getId().toString());
        suggestion.put("rollbackFromSource", sourceLog.getSource());
        suggestion.put("rollbackFromCreatedAt", sourceLog.getCreatedAt());
        suggestion.put("rollbackStrategy", "RESTORE_BEFORE_PROFILE");

        String summary = "已回滚到所选风格记录应用前的项目 Theme Profile。";

        String sql = """
                INSERT INTO project_theme_profile_application_log (
                    id,
                    workspace_id,
                    project_id,
                    project_code,
                    source,
                    style_text,
                    target_page_type,
                    agent_mode,
                    confidence,
                    summary,
                    before_profile,
                    suggestion,
                    update_payload,
                    result_profile,
                    changed_fields,
                    reasons,
                    warnings
                ) VALUES (
                    :id,
                    :workspaceId,
                    :projectId,
                    :projectCode,
                    'THEME_PROFILE_ROLLBACK',
                    :styleText,
                    :targetPageType,
                    'ROLLBACK',
                    NULL,
                    :summary,
                    CAST(:beforeProfile AS jsonb),
                    CAST(:suggestion AS jsonb),
                    CAST(:updatePayload AS jsonb),
                    CAST(:resultProfile AS jsonb),
                    CAST(:changedFields AS jsonb),
                    CAST(:reasons AS jsonb),
                    CAST(:warnings AS jsonb)
                )
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectId", projectId)
                        .addValue("projectCode", projectCode)
                        .addValue("styleText", sourceLog.getStyleText())
                        .addValue("targetPageType", sourceLog.getTargetPageType())
                        .addValue("summary", summary)
                        .addValue("beforeProfile", toJson(beforeProfile))
                        .addValue("suggestion", toJson(suggestion))
                        .addValue("updatePayload", toJson(updatePayload))
                        .addValue("resultProfile", toJson(resultProfile))
                        .addValue("changedFields", toJson(changedFields))
                        .addValue("reasons", toJson(reasons))
                        .addValue("warnings", toJson(warnings))
        );

        ProjectThemeProfileApplicationLogResponse response = new ProjectThemeProfileApplicationLogResponse();
        response.setId(id);
        response.setWorkspaceId(workspaceId);
        response.setProjectId(projectId);
        response.setProjectCode(projectCode);
        response.setSource("THEME_PROFILE_ROLLBACK");
        response.setStyleText(sourceLog.getStyleText());
        response.setTargetPageType(sourceLog.getTargetPageType());
        response.setAgentMode("ROLLBACK");
        response.setConfidence(null);
        response.setSummary(summary);
        response.setBeforeProfile(beforeProfile);
        response.setSuggestion(suggestion);
        response.setUpdatePayload(updatePayload);
        response.setResultProfile(resultProfile);
        response.setChangedFields(changedFields);
        response.setReasons(reasons);
        response.setWarnings(warnings);
        response.setCreatedAt(OffsetDateTime.now().toString());
        return response;
    }

    private UpdateProjectThemeProfileRequest toUpdateRequest(
            Map<String, Object> targetProfile
    ) {
        return new UpdateProjectThemeProfileRequest(
                readString(targetProfile, "industry"),
                readString(targetProfile, "themeKey"),
                readString(targetProfile, "visualTone"),
                readString(targetProfile, "primaryColor"),
                readString(targetProfile, "secondaryColor"),
                readString(targetProfile, "accentColor"),
                readString(targetProfile, "density"),
                readString(targetProfile, "radius"),
                readString(targetProfile, "loginLayout"),
                readString(targetProfile, "navigationStyle"),
                readBoolean(targetProfile, "darkModeEnabled"),
                readBoolean(targetProfile, "locked"),
                readString(targetProfile, "notes")
        );
    }

    private Map<String, Object> updateRequestToMap(
            UpdateProjectThemeProfileRequest request
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("industry", request.industry());
        payload.put("themeKey", request.themeKey());
        payload.put("visualTone", request.visualTone());
        payload.put("primaryColor", request.primaryColor());
        payload.put("secondaryColor", request.secondaryColor());
        payload.put("accentColor", request.accentColor());
        payload.put("density", request.density());
        payload.put("radius", request.radius());
        payload.put("loginLayout", request.loginLayout());
        payload.put("navigationStyle", request.navigationStyle());
        payload.put("darkModeEnabled", Boolean.TRUE.equals(request.darkModeEnabled()));
        payload.put("locked", Boolean.TRUE.equals(request.locked()));
        payload.put("notes", request.notes());
        return payload;
    }

    private Map<String, Object> profileToMap(
            ProjectThemeProfileResponse profile
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (profile == null) {
            return payload;
        }
        payload.put("id", profile.id());
        payload.put("projectId", profile.projectId());
        payload.put("industry", profile.industry());
        payload.put("themeKey", profile.themeKey());
        payload.put("visualTone", profile.visualTone());
        payload.put("primaryColor", profile.primaryColor());
        payload.put("secondaryColor", profile.secondaryColor());
        payload.put("accentColor", profile.accentColor());
        payload.put("density", profile.density());
        payload.put("radius", profile.radius());
        payload.put("loginLayout", profile.loginLayout());
        payload.put("navigationStyle", profile.navigationStyle());
        payload.put("darkModeEnabled", profile.darkModeEnabled());
        payload.put("source", profile.source());
        payload.put("locked", profile.locked());
        payload.put("notes", profile.notes());
        payload.put("createdBy", profile.createdBy());
        payload.put("updatedBy", profile.updatedBy());
        payload.put("createdAt", profile.createdAt());
        payload.put("updatedAt", profile.updatedAt());
        return payload;
    }

    private List<String> resolveChangedFields(
            Map<String, Object> beforeProfile,
            Map<String, Object> updatePayload,
            Map<String, Object> resultProfile
    ) {
        List<String> changedFields = new ArrayList<>();
        for (String field : TRACKED_FIELDS) {
            Object beforeValue = readAny(beforeProfile, field);
            Object updatedValue = readAny(updatePayload, field);
            Object resultValue = readAny(resultProfile, field);
            boolean explicitlyUpdated = updatePayload.containsKey(field)
                    || updatePayload.containsKey(toSnakeCase(field));
            if (explicitlyUpdated || !Objects.equals(stringify(beforeValue), stringify(resultValue))) {
                if (!Objects.equals(stringify(beforeValue), stringify(updatedValue))
                        || !Objects.equals(stringify(beforeValue), stringify(resultValue))) {
                    changedFields.add(field);
                }
            }
        }
        return changedFields.stream().distinct().toList();
    }

    private Object readAny(
            Map<String, Object> source,
            String key
    ) {
        if (source == null || key == null) {
            return null;
        }
        if (source.containsKey(key)) {
            return source.get(key);
        }
        return source.get(toSnakeCase(key));
    }

    private String readString(
            Map<String, Object> source,
            String key
    ) {
        Object value = readAny(source, key);
        return value == null ? null : String.valueOf(value);
    }

    private Boolean readBoolean(
            Map<String, Object> source,
            String key
    ) {
        Object value = readAny(source, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private UUID readUuid(
            Map<String, Object> source,
            String key
    ) {
        Object value = readAny(source, key);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String toSnakeCase(
            String value
    ) {
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String stringify(
            Object value
    ) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(
            Object value
    ) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rollback JSON.", e);
        }
    }

    private Map<String, Object> readJsonMap(
            Object value
    ) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    String.valueOf(value),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", String.valueOf(value));
            return fallback;
        }
    }

    private List<String> readJsonStringList(
            Object value
    ) {
        if (value == null) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(
                    String.valueOf(value),
                    new TypeReference<List<?>>() {
                    }
            );
            return raw.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        } catch (Exception e) {
            return List.of(String.valueOf(value));
        }
    }

    private String toCreatedAtString(
            Object value
    ) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        return String.valueOf(value);
    }
}
