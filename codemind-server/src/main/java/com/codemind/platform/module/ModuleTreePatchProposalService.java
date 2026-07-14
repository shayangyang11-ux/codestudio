package com.codemind.platform.module;

import com.codemind.platform.agent.common.client.PythonAgentClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ModuleTreePatchProposalService {

    private final ProjectModuleService projectModuleService;
    private final PythonAgentClient pythonAgentClient;

    public ModuleTreePatchProposalService(
            ProjectModuleService projectModuleService,
            PythonAgentClient pythonAgentClient
    ) {
        this.projectModuleService = projectModuleService;
        this.pythonAgentClient = pythonAgentClient;
    }

    public ModuleTreePatchProposalResponse propose(
            UUID workspaceId,
            String projectCode,
            ProposeModuleTreePatchRequest request
    ) {
        if (request == null || request.getInstruction() == null || request.getInstruction().isBlank()) {
            throw new IllegalArgumentException("AI 模块树修改要求不能为空。");
        }

        Object moduleTree = projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );

        Object rawResponse = pythonAgentClient.proposeModuleTreePatch(
                UUID.randomUUID(),
                projectCode,
                request.getSelectedNodeId(),
                request.getInstruction(),
                moduleTree
        );

        Map<String, Object> responseMap = toMap(rawResponse);
        Map<String, Object> content = toMap(responseMap.get("content"));

        String markdown = responseMap.get("content_markdown") == null
                ? null
                : String.valueOf(responseMap.get("content_markdown"));

        String description = content.get("description") == null
                ? request.getInstruction()
                : String.valueOf(content.get("description"));

        String source = content.get("source") == null
                ? "AI_TREE_PATCH"
                : String.valueOf(content.get("source"));

        return new ModuleTreePatchProposalResponse(
                description,
                source,
                content,
                markdown,
                true
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(
            Object value
    ) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(
                            String.valueOf(entry.getKey()),
                            entry.getValue()
                    );
                }
            }

            return result;
        }

        return new LinkedHashMap<>();
    }
}