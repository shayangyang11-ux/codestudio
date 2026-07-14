package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateModulePrdRequest;
import com.codemind.platform.agent.requirement.service.ModulePrdPersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ModulePrdRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final ModulePrdPersistenceService modulePrdPersistenceService;

    public ModulePrdRequirementTaskExecutor(
            ModulePrdPersistenceService modulePrdPersistenceService
    ) {
        this.modulePrdPersistenceService = modulePrdPersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_MODULE_PRD;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GenerateModulePrdRequest request = new GenerateModulePrdRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));

        modulePrdPersistenceService.generateAndPersistModulePrdForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}