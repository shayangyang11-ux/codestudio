package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateApiContractRequest;
import com.codemind.platform.agent.requirement.service.ApiContractPersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ApiContractRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final ApiContractPersistenceService apiContractPersistenceService;

    public ApiContractRequirementTaskExecutor(
            ApiContractPersistenceService apiContractPersistenceService
    ) {
        this.apiContractPersistenceService = apiContractPersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_API_CONTRACT;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GenerateApiContractRequest request = new GenerateApiContractRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));

        apiContractPersistenceService.generateAndPersistApiContractForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}