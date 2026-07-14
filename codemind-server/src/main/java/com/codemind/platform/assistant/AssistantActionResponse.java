package com.codemind.platform.assistant;

import java.util.LinkedHashMap;
import java.util.Map;

public class AssistantActionResponse {

    private String type;
    private String label;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public AssistantActionResponse() {
    }

    public AssistantActionResponse(
            String type,
            String label,
            Map<String, Object> payload
    ) {
        this.type = type;
        this.label = label;
        this.payload = payload;
    }

    public static AssistantActionResponse of(
            String type,
            String label
    ) {
        return new AssistantActionResponse(
                type,
                label,
                new LinkedHashMap<>()
        );
    }

    public static AssistantActionResponse of(
            String type,
            String label,
            Map<String, Object> payload
    ) {
        return new AssistantActionResponse(
                type,
                label,
                payload
        );
    }

    public String getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}