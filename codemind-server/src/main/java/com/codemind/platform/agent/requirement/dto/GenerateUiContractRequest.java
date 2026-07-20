package com.codemind.platform.agent.requirement.dto;

import java.util.Map;

public class GenerateUiContractRequest {

    private String requirementText;
    private Map<String, Object> themeProfile;

    public GenerateUiContractRequest() {
    }

    public GenerateUiContractRequest(
            String requirementText,
            Map<String, Object> themeProfile
    ) {
        this.requirementText = requirementText;
        this.themeProfile = themeProfile;
    }

    public String getRequirementText() {
        return requirementText;
    }

    public void setRequirementText(
            String requirementText
    ) {
        this.requirementText = requirementText;
    }

    public Map<String, Object> getThemeProfile() {
        return themeProfile;
    }

    public void setThemeProfile(
            Map<String, Object> themeProfile
    ) {
        this.themeProfile = themeProfile;
    }
}
