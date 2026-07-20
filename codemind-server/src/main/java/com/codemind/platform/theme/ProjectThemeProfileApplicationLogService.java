package com.codemind.platform.theme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProjectThemeProfileApplicationLogService {

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

    public ProjectThemeProfileApplicationLogService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ProjectThemeProfileApplicationLogResponse create(
            UUID workspaceId,
            String projectCode,
            ProjectThemeProfileApplicationLogRequest request
    ) {
        ProjectThemeProfileApplicationLogRequest safeRequest = request == null
                ? new ProjectThemeProfileApplicationLogRequest()
                : request;

        UUID id = UUID.randomUUID();
        Map<String, Object> beforeProfile = safeMap(safeRequest.getBeforeProfile());
        Map<String, Object> suggestion = safeMap(safeRequest.getSuggestion());
        Map<String, Object> updatePayload = safeMap(safeRequest.getUpdatePayload());
        Map<String, Object> resultProfile = safeMap(safeRequest.getResultProfile());
        List<String> changedFields = resolveChangedFields(beforeProfile, updatePayload, resultProfile);
        List<String> reasons = safeList(safeRequest.getReasons());
        List<String> warnings = safeList(safeRequest.getWarnings());
        UUID projectId = resolveProjectId(resultProfile, beforeProfile, suggestion);
        String source = normalizeSource(safeRequest.getSource(), suggestion, warnings);
        String agentMode = firstNonBlank(
                safeRequest.getAgentMode(),
                readString(suggestion, "agentMode"),
                readString(suggestion, "agent_mode")
        );
        String summary = firstNonBlank(
                safeRequest.getSummary(),
                readString(suggestion, "summary")
        );
        Double confidence = safeRequest.getConfidence() == null
                ? readDouble(suggestion, "confidence")
                : safeRequest.getConfidence();

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
                    :source,
                    :styleText,
                    :targetPageType,
                    :agentMode,
                    :confidence,
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
                        .addValue("source", source)
                        .addValue("styleText", blankToNull(safeRequest.getStyleText()))
                        .addValue("targetPageType", blankToNull(safeRequest.getTargetPageType()))
                        .addValue("agentMode", blankToNull(agentMode))
                        .addValue("confidence", confidence)
                        .addValue("summary", blankToNull(summary))
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
        response.setSource(source);
        response.setStyleText(blankToNull(safeRequest.getStyleText()));
        response.setTargetPageType(blankToNull(safeRequest.getTargetPageType()));
        response.setAgentMode(blankToNull(agentMode));
        response.setConfidence(confidence);
        response.setSummary(blankToNull(summary));
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

    public List<ProjectThemeProfileApplicationLogResponse> list(
            UUID workspaceId,
            String projectCode,
            int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));

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
                WHERE workspace_id = :workspaceId
                  AND project_code = :projectCode
                ORDER BY created_at DESC
                LIMIT :limit
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectCode", projectCode)
                        .addValue("limit", safeLimit),
                (rs, rowNum) -> {
                    ProjectThemeProfileApplicationLogResponse response = new ProjectThemeProfileApplicationLogResponse();
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

            if (explicitlyUpdated) {
                changedFields.add(field);
                continue;
            }

            if (resultValue != null && !Objects.equals(stringify(beforeValue), stringify(resultValue))) {
                changedFields.add(field);
            }
        }

        return changedFields.stream().distinct().toList();
    }

    private String normalizeSource(
            String source,
            Map<String, Object> suggestion,
            List<String> warnings
    ) {
        String normalized = source == null ? "" : source.trim().toUpperCase(Locale.ROOT);

        if (List.of(
                "MANUAL",
                "STYLE_INTENT_AGENT",
                "STYLE_INTENT_RULE_FALLBACK",
                "REFERENCE_STYLE_AGENT",
                "SYSTEM"
        ).contains(normalized)) {
            return normalized;
        }

        String agentMode = firstNonBlank(
                readString(suggestion, "agentMode"),
                readString(suggestion, "agent_mode")
        );

        if (agentMode != null && agentMode.toUpperCase(Locale.ROOT).contains("FALLBACK")) {
            return "STYLE_INTENT_RULE_FALLBACK";
        }

        for (String warning : warnings) {
            if (warning != null && warning.contains("兜底")) {
                return "STYLE_INTENT_RULE_FALLBACK";
            }
        }

        return "STYLE_INTENT_AGENT";
    }

    private UUID resolveProjectId(
            Map<String, Object> resultProfile,
            Map<String, Object> beforeProfile,
            Map<String, Object> suggestion
    ) {
        for (Map<String, Object> source : List.of(resultProfile, beforeProfile, suggestion)) {
            UUID id = readUuid(source, "projectId");
            if (id != null) {
                return id;
            }
            id = readUuid(source, "project_id");
            if (id != null) {
                return id;
            }
        }

        return null;
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    private List<String> safeList(List<String> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private Object readAny(Map<String, Object> source, String key) {
        if (source == null || key == null) {
            return null;
        }
        if (source.containsKey(key)) {
            return source.get(key);
        }
        return source.get(toSnakeCase(key));
    }

    private String readString(Map<String, Object> source, String key) {
        Object value = readAny(source, key);
        return value == null ? null : String.valueOf(value);
    }

    private Double readDouble(Map<String, Object> source, String key) {
        Object value = readAny(source, key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UUID readUuid(Map<String, Object> source, String key) {
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

    private String toSnakeCase(String value) {
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

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize theme profile application log JSON.", e);
        }
    }

    private Map<String, Object> readJsonMap(Object value) {
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

    private List<String> readJsonStringList(Object value) {
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

    private String toCreatedAtString(Object value) {
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
