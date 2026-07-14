package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateBusinessFlowRequest;
import com.codemind.platform.agent.requirement.service.BusinessFlowPersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class BusinessFlowRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final BusinessFlowPersistenceService businessFlowPersistenceService;

    public BusinessFlowRequirementTaskExecutor(
            BusinessFlowPersistenceService businessFlowPersistenceService
    ) {
        this.businessFlowPersistenceService = businessFlowPersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_BUSINESS_FLOW;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GenerateBusinessFlowRequest request = new GenerateBusinessFlowRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));

        businessFlowPersistenceService.generateAndPersistBusinessFlowForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}