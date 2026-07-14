package com.codemind.platform.audit;

import com.codemind.platform.common.JsonbHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(
            UUID projectId,
            String actorType,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> beforeJson,
            Map<String, Object> afterJson,
            Map<String, Object> metadata
    ) {
        String sql = """
                INSERT INTO audit_log (
                    project_id,
                    actor_type,
                    actor_id,
                    action,
                    resource_type,
                    resource_id,
                    before_json,
                    after_json,
                    metadata
                )
                VALUES (
                    :projectId,
                    :actorType,
                    :actorId,
                    :action,
                    :resourceType,
                    :resourceId,
                    :beforeJson,
                    :afterJson,
                    :metadata
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("actorType", actorType)
                .addValue("actorId", actorId)
                .addValue("action", action)
                .addValue("resourceType", resourceType)
                .addValue("resourceId", resourceId)
                .addValue("beforeJson", JsonbHelper.toJsonb(beforeJson, objectMapper))
                .addValue("afterJson", JsonbHelper.toJsonb(afterJson, objectMapper))
                .addValue("metadata", JsonbHelper.toJsonb(metadata, objectMapper));

        jdbcTemplate.update(sql, params);
    }
}