package com.codemind.platform.agent.common.worker;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.task.AgentTaskEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AgentTaskExecutionUserResolver {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AgentTaskExecutionUserResolver(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CurrentUser resolve(
            AgentTaskEntity task
    ) {
        Map<String, Object> payload = task.getRequestPayload();

        CurrentUser fromPayload = resolveFromPayload(payload);
        if (fromPayload != null) {
            return fromPayload;
        }

        String username = task.getCreatedBy();
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Agent task has no createdBy user: " + task.getId());
        }

        return resolveFromUserAccount(username);
    }

    private CurrentUser resolveFromPayload(
            Map<String, Object> payload
    ) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        Object userIdValue = payload.get("created_by_user_id");
        Object usernameValue = payload.get("created_by_username");
        Object displayNameValue = payload.get("created_by_display_name");

        if (userIdValue == null || usernameValue == null) {
            return null;
        }

        return new CurrentUser(
                UUID.fromString(String.valueOf(userIdValue)),
                String.valueOf(usernameValue),
                displayNameValue == null ? String.valueOf(usernameValue) : String.valueOf(displayNameValue)
        );
    }

    private CurrentUser resolveFromUserAccount(
            String username
    ) {
        String sql = """
                SELECT
                    id,
                    username,
                    COALESCE(display_name, username) AS display_name
                FROM user_account
                WHERE username = :username
                LIMIT 1
                """;

        return jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("username", username),
                (rs, rowNum) -> new CurrentUser(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("display_name")
                )
        );
    }
}