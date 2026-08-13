package com.codemind.platform.agent.requirement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class GenerateUiContractRequest {

    // A9.10.14.1.1_FORCE_UI_CONTRACT_REGENERATE
    private String requirementText;
    private Map<String, Object> themeProfile;

    @JsonProperty("forceRegenerate")
    @JsonAlias({"force_regenerate", "overwriteExisting", "overwrite_existing"})
    private Boolean forceRegenerate;

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

    public Boolean getForceRegenerate() {
        return forceRegenerate;
    }

    public void setForceRegenerate(
            Boolean forceRegenerate
    ) {
        this.forceRegenerate = forceRegenerate;
    }

    public boolean forceRegenerateResolved() {
        return Boolean.TRUE.equals(forceRegenerate);
    }
}
