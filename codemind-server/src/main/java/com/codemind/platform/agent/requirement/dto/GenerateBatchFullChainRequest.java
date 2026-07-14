package com.codemind.platform.agent.requirement.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public class GenerateBatchFullChainRequest {

    private List<UUID> moduleIds;

    private String requirementText;

    private Boolean overwriteExisting = false;

    public List<UUID> getModuleIds() {
        return moduleIds;
    }

    public void setModuleIds(List<UUID> moduleIds) {
        this.moduleIds = moduleIds;
    }

    public String getRequirementText() {
        return requirementText;
    }

    public void setRequirementText(String requirementText) {
        this.requirementText = requirementText;
    }

    public Boolean getOverwriteExisting() {
        return overwriteExisting;
    }

    public void setOverwriteExisting(Boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    public boolean overwriteExistingResolved() {
        return Boolean.TRUE.equals(overwriteExisting);
    }

    public List<UUID> moduleIdsResolved() {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>();

        for (UUID moduleId : moduleIds) {
            if (moduleId != null) {
                uniqueIds.add(moduleId);
            }
        }

        return new ArrayList<>(uniqueIds);
    }
}