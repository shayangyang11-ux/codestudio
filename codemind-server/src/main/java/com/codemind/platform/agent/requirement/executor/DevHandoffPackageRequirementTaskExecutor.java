package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateDevHandoffPackageRequest;
import com.codemind.platform.agent.requirement.service.DevHandoffPackagePersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class DevHandoffPackageRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final DevHandoffPackagePersistenceService devHandoffPackagePersistenceService;

    public DevHandoffPackageRequirementTaskExecutor(
            DevHandoffPackagePersistenceService devHandoffPackagePersistenceService
    ) {
        this.devHandoffPackagePersistenceService = devHandoffPackagePersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_DEV_HANDOFF_PACKAGE;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GenerateDevHandoffPackageRequest request = new GenerateDevHandoffPackageRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));

        devHandoffPackagePersistenceService.generateAndPersistDevHandoffPackageForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}