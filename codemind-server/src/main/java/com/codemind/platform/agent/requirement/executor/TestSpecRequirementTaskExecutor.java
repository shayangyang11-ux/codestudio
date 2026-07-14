package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateTestSpecRequest;
import com.codemind.platform.agent.requirement.service.TestSpecPersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class TestSpecRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final TestSpecPersistenceService testSpecPersistenceService;

    public TestSpecRequirementTaskExecutor(
            TestSpecPersistenceService testSpecPersistenceService
    ) {
        this.testSpecPersistenceService = testSpecPersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_TEST_SPEC;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GenerateTestSpecRequest request = new GenerateTestSpecRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));

        testSpecPersistenceService.generateAndPersistTestSpecForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}