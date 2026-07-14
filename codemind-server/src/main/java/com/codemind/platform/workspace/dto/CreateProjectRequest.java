package com.codemind.platform.workspace.dto;

import java.util.Map;

public record CreateProjectRequest(
        String projectCode,
        String projectName,
        String description,
        Map<String, Object> techStack
) {
}