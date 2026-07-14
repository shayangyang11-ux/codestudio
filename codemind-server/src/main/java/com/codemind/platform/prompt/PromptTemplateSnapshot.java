package com.codemind.platform.prompt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PromptTemplateSnapshot(
        UUID templateId,
        UUID templateVersionId,
        String templateKey,
        String templateName,
        String taskType,
        String artifactType,
        String version,
        String checksum,
        String systemPrompt,
        String userPromptTemplate,
        Map<String, Object> outputSchema
) {

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("template_id", templateId == null ? null : templateId.toString());
        payload.put("template_version_id", templateVersionId == null ? null : templateVersionId.toString());
        payload.put("template_key", templateKey);
        payload.put("template_name", templateName);
        payload.put("task_type", taskType);
        payload.put("artifact_type", artifactType);
        payload.put("version", version);
        payload.put("checksum", checksum);
        payload.put("system_prompt", systemPrompt);
        payload.put("user_prompt_template", userPromptTemplate);
        payload.put("output_schema", outputSchema);

        return payload;
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("template_id", templateId == null ? null : templateId.toString());
        payload.put("template_version_id", templateVersionId == null ? null : templateVersionId.toString());
        payload.put("template_key", templateKey);
        payload.put("template_name", templateName);
        payload.put("task_type", taskType);
        payload.put("artifact_type", artifactType);
        payload.put("version", version);
        payload.put("checksum", checksum);

        return payload;
    }
}