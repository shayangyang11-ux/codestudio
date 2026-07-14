package com.codemind.platform.agent.requirement.dto;

public class GenerateFullChainRequest {

    private String requirementText;

    private Boolean overwriteExisting = false;

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
}