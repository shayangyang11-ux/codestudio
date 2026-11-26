package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.agent.requirement.dto.GenerateModulePrdRequest;
import com.codemind.platform.agent.requirement.service.ModulePrdPersistenceService;
import com.codemind.platform.module.ProjectModuleService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ModulePrdRequirementTaskExecutor extends AbstractRequirementTaskExecutor {

    private final ModulePrdPersistenceService modulePrdPersistenceService;
    private final ProjectModuleService projectModuleService;

    public ModulePrdRequirementTaskExecutor(
            ModulePrdPersistenceService modulePrdPersistenceService,
            ProjectModuleService projectModuleService
    ) {
        this.modulePrdPersistenceService = modulePrdPersistenceService;
        this.projectModuleService = projectModuleService;
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

        Map<String, Object> result = modulePrdPersistenceService.generateAndPersistModulePrdForExistingTask(
                task.getId(),
                workspaceId,
                projectCode,
                moduleId,
                request,
                username(task)
        );

        autoMaterializeFeaturePageFromModulePrdIfRequested(task, payload, moduleId, result);
    }
    // A9.10.14.4.3.15.4.15.3.2_AUTO_MATERIALIZE_FEATURE_PAGE_FROM_PRD
    private void autoMaterializeFeaturePageFromModulePrdIfRequested(
            AgentTaskEntity task,
            Map<String, Object> payload,
            UUID moduleId,
            Map<String, Object> result
    ) {
        if (!isAutoMaterializeFeaturePageFromModulePrdRequested(payload)) {
            return;
        }

        if (result == null) {
            return;
        }

        Map<String, Object> artifactContent = asObjectMap(result.get("content"));
        if (artifactContent.isEmpty()) {
            result.put("featurePageMaterializationSkipped", true);
            result.put("featurePageMaterializationReason", "MODULE_PRD content is empty.");
            return;
        }

        UUID projectId = task.getProjectId() != null
                ? task.getProjectId()
                : optionalUuid(payload, "project_id");

        if (projectId == null) {
            result.put("featurePageMaterializationSkipped", true);
            result.put("featurePageMaterializationReason", "Project id is missing.");
            return;
        }

        try {
            Map<String, Object> materialization = projectModuleService.completeRequirementModuleChildrenFromModulePrd(
                    projectId,
                    moduleId,
                    artifactContent,
                    username(task)
            );

            result.put("featurePageMaterialized", true);
            result.put("featurePageMaterialization", materialization);
            result.put("featurePageMaterializationSource", "MODULE_PRD_STRUCTURED_REQUIREMENTS");
        } catch (Exception ex) {
            result.put("featurePageMaterialized", false);
            result.put("featurePageMaterializationSkipped", true);
            result.put("featurePageMaterializationError", ex.getMessage());
        }
    }

    private boolean isAutoMaterializeFeaturePageFromModulePrdRequested(
            Map<String, Object> payload
    ) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }

        if (truthy(payload.get("autoMaterializeFeaturePageFromModulePrd"))
                || truthy(payload.get("auto_materialize_feature_page_from_module_prd"))) {
            return true;
        }

        String orchestrationType = text(payload.get("orchestration_type"), payload.get("orchestrationType"));
        if ("FULL_CHAIN".equalsIgnoreCase(orchestrationType)) {
            return true;
        }

        String blueprintMode = text(
                payload.get("fullRequirementBlueprintMode"),
                payload.get("full_requirement_blueprint_mode")
        );

        return "AUTO_MODULE_PRD_TO_FEATURE_PAGE".equalsIgnoreCase(blueprintMode);
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        String text = text(value);
        return "true".equalsIgnoreCase(text)
                || "1".equals(text)
                || "yes".equalsIgnoreCase(text)
                || "Y".equalsIgnoreCase(text);
    }

    private Map<String, Object> asObjectMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> {
                if (key != null) {
                    result.put(String.valueOf(key), item);
                }
            });
            return result;
        }

        return Map.of();
    }

    private String text(Object... values) {
        if (values == null) {
            return "";
        }

        for (Object value : values) {
            if (value == null) {
                continue;
            }

            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }

        return "";
    }


}