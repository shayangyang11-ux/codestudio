package com.codemind.platform.theme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProjectStyleAssistantAgentClient {

    private final RestTemplate restTemplate;
    private final String agentBaseUrl;

    public ProjectStyleAssistantAgentClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${codemind.agent.base-url}") String agentBaseUrl
    ) {
        this.restTemplate = restTemplateBuilder.build();
        this.agentBaseUrl = agentBaseUrl;
    }

    public Object analyzeStyle(
            ProjectStyleAssistantRequest request,
            ProjectThemeProfileResponse currentProfile
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction", request == null ? null : request.getInstruction());
        payload.put("referenceText", request == null ? null : request.getReferenceText());
        payload.put("referenceNotes", request == null ? null : request.getReferenceNotes());
        payload.put("referenceMode", request == null ? "AUTO" : request.getReferenceMode());
        payload.put("targetPageType", request == null ? null : request.getTargetPageType());
        payload.put("allowDarkMode", request == null ? null : request.getAllowDarkMode());
        payload.put("attachmentIds", request == null || request.getAttachmentIds() == null ? java.util.List.of() : request.getAttachmentIds());
        payload.put("attachmentNames", request == null || request.getAttachmentNames() == null ? java.util.List.of() : request.getAttachmentNames());
        payload.put("currentProfile", buildCurrentProfilePayload(currentProfile));

        String url = agentBaseUrl + "/agent/style/assistant/analyze";
        return restTemplate.postForObject(url, payload, Object.class);
    }

    private Map<String, Object> buildCurrentProfilePayload(
            ProjectThemeProfileResponse profile
    ) {
        if (profile == null) {
            return Map.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
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
        return payload;
    }
}
