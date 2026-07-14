package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateDataModelRequest;
import com.codemind.platform.agent.requirement.service.DataModelPersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class DataModelRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final DataModelPersistenceService dataModelPersistenceService;

    public DataModelRequirementTaskExecutor(
            DataModelPersistenceService dataModelPersistenceService
    ) {
        this.dataModelPersistenceService = dataModelPersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_DATA_MODEL;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GenerateDataModelRequest request = new GenerateDataModelRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));

        dataModelPersistenceService.generateAndPersistDataModelForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}