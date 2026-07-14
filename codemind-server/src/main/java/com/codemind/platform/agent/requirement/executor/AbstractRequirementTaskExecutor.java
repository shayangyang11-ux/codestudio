package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.task.AgentTaskEntity;

import java.util.Map;
import java.util.UUID;

public abstract class AbstractRequirementTaskExecutor implements RequirementTaskExecutor {

    protected Map<String, Object> payload(AgentTaskEntity task) {
        Map<String, Object> payload = task.getRequestPayload();

        if (payload == null || payload.isEmpty()) {
            throw new IllegalStateException("Agent task request_payload is empty: " + task.getId());
        }

        return payload;
    }

    protected String requiredString(
            Map<String, Object> payload,
            String key
    ) {
        Object value = payload.get(key);

        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException("Missing required request_payload field: " + key);
        }

        return String.valueOf(value);
    }

    protected String optionalString(
            Map<String, Object> payload,
            String key
    ) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    protected UUID requiredUuid(
            Map<String, Object> payload,
            String key
    ) {
        return UUID.fromString(requiredString(payload, key));
    }

    protected UUID optionalUuid(
            Map<String, Object> payload,
            String key
    ) {
        String value = optionalString(payload, key);

        if (value == null || value.isBlank()) {
            return null;
        }

        return UUID.fromString(value);
    }

    protected String username(AgentTaskEntity task) {
        if (task.getCreatedBy() == null || task.getCreatedBy().isBlank()) {
            return "system";
        }

        return task.getCreatedBy();
    }
}