package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateUiContractRequest;
import com.codemind.platform.agent.requirement.service.UiContractPersistenceService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class UiContractRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final UiContractPersistenceService uiContractPersistenceService;

    public UiContractRequirementTaskExecutor(
            UiContractPersistenceService uiContractPersistenceService
    ) {
        this.uiContractPersistenceService = uiContractPersistenceService;
    }

    @Override
    public TaskType supportTaskType() {
        return TaskType.GENERATE_UI_CONTRACT;
    }

    @Override
    public void execute(AgentTaskEntity task) {
        Map<String, Object> payload = payload(task);

        UUID workspaceId = optionalUuid(payload, "workspace_id");
        String projectCode = requiredString(payload, "project_code");
        UUID moduleId = requiredUuid(payload, "module_id");

        GenerateUiContractRequest request = new GenerateUiContractRequest();
        request.setRequirementText(optionalString(payload, "requirement_text"));
        // A9.10.14.1.1_FORCE_UI_CONTRACT_REGENERATE: carry explicit regenerate/theme metadata from the queued task payload.
        request.setForceRegenerate(
                Boolean.TRUE.equals(payload.get("force_regenerate"))
                        || Boolean.TRUE.equals(payload.get("forceRegenerate"))
        );

        Object rawThemeProfile = payload.get("themeProfile");
        if (!(rawThemeProfile instanceof Map<?, ?>)) {
            rawThemeProfile = payload.get("theme_profile");
        }
        if (rawThemeProfile instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalizedThemeProfile = new java.util.LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    normalizedThemeProfile.put(String.valueOf(key), value);
                }
            });
            request.setThemeProfile(normalizedThemeProfile);
        }

        uiContractPersistenceService.generateAndPersistUiContractForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );
    }
}