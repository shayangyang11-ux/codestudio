package com.codemind.platform.agent.common.gateway;

import com.codemind.platform.agent.common.client.PythonAgentClient;
import com.codemind.platform.agent.requirement.dto.*;
import com.codemind.platform.agent.common.log.AgentExecutionLogService;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentGatewayService {

    private final PythonAgentClient pythonAgentClient;
    private final AgentExecutionLogService agentExecutionLogService;

    public AgentGatewayService(
            PythonAgentClient pythonAgentClient,
            AgentExecutionLogService agentExecutionLogService
    ) {
        this.pythonAgentClient = pythonAgentClient;
        this.agentExecutionLogService = agentExecutionLogService;
    }

    public Object generateModuleTree(
            AgentTaskEntity task,
            ProjectEntity project,
            GenerateModuleTreeRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                null,
                "module_tree_gateway",
                "GENERATE_MODULE_TREE",
                request.getRequirementText(),
                () -> pythonAgentClient.generateModuleTree(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        request,
                        contextPackage
                )
        );
    }

    public Object generateModulePrd(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GenerateModulePrdRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "module_prd_gateway",
                "GENERATE_MODULE_PRD",
                request.getRequirementText(),
                () -> pythonAgentClient.generateModulePrd(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }

    public Object generateBusinessFlow(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GenerateBusinessFlowRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "business_flow_gateway",
                "GENERATE_BUSINESS_FLOW",
                request.getRequirementText(),
                () -> pythonAgentClient.generateBusinessFlow(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }

    public Object generatePageSpec(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GeneratePageSpecRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "page_spec_gateway",
                "GENERATE_PAGE_SPEC",
                request.getRequirementText(),
                () -> pythonAgentClient.generatePageSpec(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }

    public Object generateDataModel(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GenerateDataModelRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "data_model_gateway",
                "GENERATE_DATA_MODEL",
                request.getRequirementText(),
                () -> pythonAgentClient.generateDataModel(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }

    public Object generateApiContract(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GenerateApiContractRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "api_contract_gateway",
                "GENERATE_API_CONTRACT",
                request.getRequirementText(),
                () -> pythonAgentClient.generateApiContract(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }

    public Object generateTestSpec(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GenerateTestSpecRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "test_spec_gateway",
                "GENERATE_TEST_SPEC",
                request.getRequirementText(),
                () -> pythonAgentClient.generateTestSpec(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }

    public Object generateUiContract(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GenerateUiContractRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "ui_contract_gateway",
                "GENERATE_UI_CONTRACT",
                request.getRequirementText(),
                () -> pythonAgentClient.generateUiContract(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }

    private Object callProjectAgent(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            String nodeName,
            String taskType,
            String promptText,
            AgentCall agentCall
    ) {
        agentExecutionLogService.record(
                task.getId(),
                project.getId(),
                module == null ? null : module.getId(),
                "requirement_graph",
                nodeName,
                "GATEWAY_CALL",
                "STARTED",
                null,
                truncate(promptText, 800),
                null,
                null,
                null,
                null,
                null,
                null,
                buildMetadata(
                        "endpoint",
                        "/agent/tasks/execute",
                        "task_type",
                        taskType
                )
        );

        try {
            Object response = agentCall.execute();

            agentExecutionLogService.record(
                    task.getId(),
                    project.getId(),
                    module == null ? null : module.getId(),
                    "requirement_graph",
                    nodeName,
                    "GATEWAY_CALL",
                    "SUCCESS",
                    null,
                    null,
                    truncate(String.valueOf(response), 1200),
                    null,
                    null,
                    null,
                    null,
                    null,
                    buildMetadata(
                            "endpoint",
                            "/agent/tasks/execute",
                            "response_type",
                            response == null ? "null" : response.getClass().getName()
                    )
            );

            return response;

        } catch (Exception e) {
            agentExecutionLogService.record(
                    task.getId(),
                    project.getId(),
                    module == null ? null : module.getId(),
                    "requirement_graph",
                    nodeName,
                    "GATEWAY_CALL",
                    "FAILED",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "PYTHON_AGENT_CALL_FAILED",
                    e.getMessage(),
                    buildMetadata(
                            "endpoint",
                            "/agent/tasks/execute",
                            "exception_type",
                            e.getClass().getName()
                    )
            );

            throw e;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    private Map<String, Object> buildMetadata(
            String key1,
            Object value1,
            String key2,
            Object value2
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(key1, value1);
        metadata.put(key2, value2);
        return metadata;
    }

    @FunctionalInterface
    private interface AgentCall {
        Object execute();
    }

    public Object generateDevHandoffPackage(
            AgentTaskEntity task,
            ProjectEntity project,
            ProjectModuleEntity module,
            GenerateDevHandoffPackageRequest request,
            Map<String, Object> contextPackage
    ) {
        return callProjectAgent(
                task,
                project,
                module,
                "dev_handoff_package_gateway",
                "GENERATE_DEV_HANDOFF_PACKAGE",
                request.getRequirementText(),
                () -> pythonAgentClient.generateDevHandoffPackage(
                        task.getId(),
                        project.getId(),
                        project.getProjectCode(),
                        module.getId(),
                        module,
                        request,
                        contextPackage
                )
        );
    }
}