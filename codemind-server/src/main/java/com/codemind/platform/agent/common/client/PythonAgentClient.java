
package com.codemind.platform.agent.common.client;



import com.codemind.platform.agent.requirement.dto.GeneratePageSpecRequest;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.agent.requirement.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PythonAgentClient {

    private final RestTemplate restTemplate;
    private final String agentBaseUrl;

    public PythonAgentClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${codemind.agent.base-url}") String agentBaseUrl
    ) {
        this.restTemplate = restTemplateBuilder.build();
        this.agentBaseUrl = agentBaseUrl;
    }

    public Object generateModuleTree(
            UUID taskId,
            UUID projectId,
            String projectCode,
            GenerateModuleTreeRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("requirement_text", request.getRequirementText());
        input.put("modeling_mode", request.getModelingMode());

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                null,
                "GENERATE_MODULE_TREE",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());
        payload.put("modeling_mode", request.getModelingMode());

        return execute(payload);
    }

    public Object generateModulePrd(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GenerateModulePrdRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = buildModuleInput(
                module,
                request.getRequirementText(),
                "MODULE_PRD"
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                "GENERATE_MODULE_PRD",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());

        return execute(payload);
    }

    public Object generateBusinessFlow(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GenerateBusinessFlowRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = buildModuleInput(
                module,
                request.getRequirementText(),
                "BUSINESS_FLOW"
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                "GENERATE_BUSINESS_FLOW",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());

        return execute(payload);
    }
    public Object generatePageSpec(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GeneratePageSpecRequest request,
            Map<String, Object> contextPackage
    ) {
        return generatePageArtifact(
                taskId,
                projectId,
                projectCode,
                moduleId,
                module,
                request,
                contextPackage,
                "GENERATE_PAGE_SPEC",
                "PAGE_SPEC"
        );
    }

    public Object generatePageArtifact(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GeneratePageSpecRequest request,
            Map<String, Object> contextPackage,
            String taskType,
            String artifactType
    ) {
        String pythonTaskType = "GENERATE_PAGE_MAP".equals(taskType)
                ? "GENERATE_PAGE_SPEC"
                : taskType;

        String requirementText = request.getRequirementText();

        if ("PAGE_MAP".equals(artifactType)) {
            requirementText = (
                    "【PAGE_MAP 生成模式】\n" +
                    "请生成 MODULE 级页面地图 PAGE_MAP，不要生成单页 PAGE_SPEC。\n" +
                    "输出重点：页面清单、pageKey、routePath、页面职责、入口出口、主流程、异常流程、sourcePageKey、actionId、targetPageKey、targetRoute、navigationType、navigationStatus、fallbackBehavior。\n" +
                    "跨 MODULE 跳转允许占位，但必须标记 RESOLVED_PAGE_ONLY 或 UNRESOLVED_PAGE。\n\n" +
                    String.valueOf(requirementText)
            );
        }

        Map<String, Object> input = buildModuleInput(
                module,
                requirementText,
                artifactType
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                pythonTaskType,
                input,
                contextPackage
        );

        payload.put("requirement_text", requirementText);
        payload.put("artifact_type", artifactType);
        payload.put("original_task_type", taskType);
        payload.put("python_task_type", pythonTaskType);

        return execute(payload);
    }



    public Object generateDataModel(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GenerateDataModelRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = buildModuleInput(
                module,
                request.getRequirementText(),
                "DATA_MODEL"
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                "GENERATE_DATA_MODEL",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());

        return execute(payload);
    }

    public Object generateApiContract(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GenerateApiContractRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = buildModuleInput(
                module,
                request.getRequirementText(),
                "API_CONTRACT"
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                "GENERATE_API_CONTRACT",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());

        return execute(payload);
    }

    public Object generateTestSpec(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GenerateTestSpecRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = buildModuleInput(
                module,
                request.getRequirementText(),
                "TEST_SPEC"
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                "GENERATE_TEST_SPEC",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());

        return execute(payload);
    }

    public Object generateUiContract(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GenerateUiContractRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = buildModuleInput(
                module,
                request.getRequirementText(),
                "UI_CONTRACT"
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                "GENERATE_UI_CONTRACT",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());

        return execute(payload);
    }

    private Map<String, Object> buildModuleInput(
            ProjectModuleEntity module,
            String requirementText,
            String targetArtifactType
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("requirement_text", requirementText);
        input.put("current_module", buildCurrentModulePayload(module));
        input.put("target_artifact_type", targetArtifactType);
        return input;
    }

    private Map<String, Object> buildCurrentModulePayload(
            ProjectModuleEntity module
    ) {
        Map<String, Object> currentModule = new LinkedHashMap<>();

        currentModule.put("module_id", module.getId().toString());
        currentModule.put("module_key", module.getModuleKey());
        currentModule.put("module_name", module.getModuleName());
        currentModule.put("module_type", module.getModuleType());
        currentModule.put("module_path", module.getModulePath());
        currentModule.put("level_no", module.getLevelNo());
        currentModule.put("sort_order", module.getSortOrder());
        currentModule.put("extra", module.getExtra() == null ? Map.of() : module.getExtra());

        return currentModule;
    }

    private Map<String, Object> buildBasePayload(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            String taskType,
            Map<String, Object> input,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId.toString());
        payload.put("project_id", projectId.toString());
        payload.put("project_code", projectCode);
        payload.put("module_id", moduleId == null ? null : moduleId.toString());
        payload.put("task_type", taskType);
        payload.put("input", input == null ? Map.of() : input);
        payload.put("context_package", contextPackage == null ? Map.of() : contextPackage);
        return payload;
    }

    private Object execute(Map<String, Object> payload) {
        String url = agentBaseUrl + "/agent/tasks/execute";
        return restTemplate.postForObject(url, payload, Object.class);
    }


    // A9.10.14.4.3.15.6.24J2_PROTOTYPE_EDIT_PLANNER_V1
    @SuppressWarnings("unchecked")
    public Map<String, Object> planPrototypeEdit(Map<String, Object> payload) {
        String url = agentBaseUrl + "/agent/runtime/prototype-edit/plan";
        Object response = restTemplate.postForObject(url, payload == null ? Map.of() : payload, Object.class);
        if (response instanceof Map<?, ?> responseMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : responseMap.entrySet()) {
                if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("raw_response", response);
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLlmRuntimeStatus() {
        String url = agentBaseUrl + "/agent/runtime/llm/status";

        Object response = restTemplate.getForObject(url, Object.class);

        if (response instanceof Map<?, ?> responseMap) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : responseMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            return result;
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("raw_response", response);
        return fallback;
    }


    @SuppressWarnings("unchecked")
    public Map<String, Object> getModelRoutingStatus() {
        String url = agentBaseUrl + "/agent/runtime/model-routing/status";

        Object response = restTemplate.getForObject(url, Object.class);

        if (response instanceof Map<?, ?> responseMap) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : responseMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            return result;
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("raw_response", response);
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateDevelopmentNodejsCodeProposal(Map<String, Object> payload) {
        String url = agentBaseUrl + "/agent/development/codegen/nodejs/proposal";

        Object response = restTemplate.postForObject(url, payload == null ? Map.of() : payload, Object.class);

        if (response instanceof Map<?, ?> responseMap) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : responseMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            return result;
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("raw_response", response);
        return fallback;
    }




    @SuppressWarnings("unchecked")
    public Map<String, Object> generateDevelopmentNodejsRepairProposal(Map<String, Object> payload) {
        String url = agentBaseUrl + "/agent/development/codegen/nodejs/repair";

        Object response = restTemplate.postForObject(url, payload == null ? Map.of() : payload, Object.class);

        if (response instanceof Map<?, ?> responseMap) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : responseMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            return result;
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("raw_response", response);
        return fallback;
    }


    public Object proposeModuleTreePatch(
            UUID taskId,
            String projectCode,
            UUID selectedNodeId,
            String instruction,
            Object moduleTree
    ) {
        return proposeModuleTreePatch(
                taskId,
                projectCode,
                selectedNodeId,
                instruction,
                moduleTree,
                null,
                null
        );
    }

    public Object proposeModuleTreePatch(
            UUID taskId,
            String projectCode,
            UUID selectedNodeId,
            String instruction,
            Object moduleTree,
            Object attachmentContexts,
            Object attachmentSummary
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("instruction", instruction);
        input.put("selected_node_id", selectedNodeId == null ? null : selectedNodeId.toString());
        input.put("module_tree", moduleTree);
        input.put("attachment_contexts", attachmentContexts == null ? java.util.List.of() : attachmentContexts);
        input.put("attachment_summary", attachmentSummary == null ? Map.of() : attachmentSummary);

        Map<String, Object> contextPackage = new LinkedHashMap<>();
        contextPackage.put("module_tree", moduleTree);
        contextPackage.put("attachment_contexts", attachmentContexts == null ? java.util.List.of() : attachmentContexts);
        contextPackage.put("attachment_summary", attachmentSummary == null ? Map.of() : attachmentSummary);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId.toString());
        payload.put("project_id", projectCode);
        payload.put("project_code", projectCode);
        payload.put("module_id", selectedNodeId == null ? null : selectedNodeId.toString());
        payload.put("task_type", "PROPOSE_MODULE_TREE_PATCH");
        payload.put("input", input);
        payload.put("context_package", contextPackage);
        payload.put("requirement_text", instruction);

        String url = agentBaseUrl + "/agent/tasks/propose-module-tree-patch";

        return restTemplate.postForObject(url, payload, Object.class);
    }


    public Object classifyAssistantIntent(
            UUID taskId,
            String projectCode,
            UUID selectedNodeId,
            String selectedArtifactType,
            String messageText,
            Object moduleTree,
            Object attachmentContexts,
            boolean moduleTreeExists
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", messageText);
        input.put("selected_node_id", selectedNodeId == null ? null : selectedNodeId.toString());
        input.put("selected_artifact_type", selectedArtifactType);
        input.put("module_tree_exists", moduleTreeExists);
        input.put("module_tree", moduleTree);
        input.put("attachment_contexts", attachmentContexts);

        Map<String, Object> contextPackage = new LinkedHashMap<>();
        contextPackage.put("module_tree_exists", moduleTreeExists);
        contextPackage.put("module_tree", moduleTree);
        contextPackage.put("attachment_contexts", attachmentContexts);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId.toString());
        payload.put("project_id", projectCode);
        payload.put("project_code", projectCode);
        payload.put("module_id", selectedNodeId == null ? null : selectedNodeId.toString());
        payload.put("task_type", "CLASSIFY_ASSISTANT_INTENT");
        payload.put("input", input);
        payload.put("context_package", contextPackage);
        payload.put("requirement_text", messageText);

        String url = agentBaseUrl + "/agent/tasks/classify-assistant-intent";

        return restTemplate.postForObject(url, payload, Object.class);
    }


    public Object reviewAssistantArtifact(
            UUID taskId,
            String projectCode,
            UUID selectedNodeId,
            String selectedArtifactType,
            String instruction,
            Object reviewInput
    ) {
        Map<String, Object> input = new LinkedHashMap<>();

        if (reviewInput instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    input.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        input.put("instruction", instruction);
        input.put("selected_node_id", selectedNodeId == null ? null : selectedNodeId.toString());
        input.put("selected_artifact_type", selectedArtifactType);

        Map<String, Object> contextPackage = new LinkedHashMap<>();
        contextPackage.put("review_input", input);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", taskId.toString());
        payload.put("project_id", projectCode);
        payload.put("project_code", projectCode);
        payload.put("module_id", selectedNodeId == null ? null : selectedNodeId.toString());
        payload.put("task_type", "REVIEW_ASSISTANT_ARTIFACT");
        payload.put("input", input);
        payload.put("context_package", contextPackage);
        payload.put("requirement_text", instruction);

        String url = agentBaseUrl + "/agent/tasks/review-artifact";

        return restTemplate.postForObject(url, payload, Object.class);
    }

    public Object generateDevHandoffPackage(
            UUID taskId,
            UUID projectId,
            String projectCode,
            UUID moduleId,
            ProjectModuleEntity module,
            GenerateDevHandoffPackageRequest request,
            Map<String, Object> contextPackage
    ) {
        Map<String, Object> input = buildModuleInput(
                module,
                request.getRequirementText(),
                "DEV_HANDOFF_PACKAGE"
        );

        Map<String, Object> payload = buildBasePayload(
                taskId,
                projectId,
                projectCode,
                moduleId,
                "GENERATE_DEV_HANDOFF_PACKAGE",
                input,
                contextPackage
        );

        payload.put("requirement_text", request.getRequirementText());

        return execute(payload);
    }
}


