package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GeneratePageSpecRequest;
import com.codemind.platform.agent.requirement.service.PageSpecPersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class PageMapRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final PageSpecPersistenceService pageSpecPersistenceService;

    public PageMapRequirementTaskExecutor(
            PageSpecPersistenceService pageSpecPersistenceService
    ) {
        this.pageSpecPersistenceService = pageSpecPersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_PAGE_MAP;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GeneratePageSpecRequest request = new GeneratePageSpecRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));

        pageSpecPersistenceService.generateAndPersistPageMapForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}
