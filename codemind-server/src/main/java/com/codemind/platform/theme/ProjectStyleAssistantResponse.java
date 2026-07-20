package com.codemind.platform.theme;

import java.util.List;
import java.util.Map;

public class ProjectStyleAssistantResponse {

    private String industry;
    private String themeKey;
    private String visualTone;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String density;
    private String radius;
    private String loginLayout;
    private String navigationStyle;
    private boolean darkModeEnabled;
    private String analysisMode;
    private String agentMode;
    private List<String> attachmentIds;
    private List<String> attachmentNames;
    private Map<String, Object> extractedSignals;
    private double confidence;
    private String summary;
    private List<String> reasons;
    private List<String> warnings;
    private Map<String, Object> updatePayload;

    public ProjectStyleAssistantResponse() {
    }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getThemeKey() { return themeKey; }
    public void setThemeKey(String themeKey) { this.themeKey = themeKey; }
    public String getVisualTone() { return visualTone; }
    public void setVisualTone(String visualTone) { this.visualTone = visualTone; }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public String getDensity() { return density; }
    public void setDensity(String density) { this.density = density; }
    public String getRadius() { return radius; }
    public void setRadius(String radius) { this.radius = radius; }
    public String getLoginLayout() { return loginLayout; }
    public void setLoginLayout(String loginLayout) { this.loginLayout = loginLayout; }
    public String getNavigationStyle() { return navigationStyle; }
    public void setNavigationStyle(String navigationStyle) { this.navigationStyle = navigationStyle; }
    public boolean isDarkModeEnabled() { return darkModeEnabled; }
    public void setDarkModeEnabled(boolean darkModeEnabled) { this.darkModeEnabled = darkModeEnabled; }
    public String getAnalysisMode() { return analysisMode; }
    public void setAnalysisMode(String analysisMode) { this.analysisMode = analysisMode; }
    public String getAgentMode() { return agentMode; }
    public void setAgentMode(String agentMode) { this.agentMode = agentMode; }
    public List<String> getAttachmentIds() { return attachmentIds; }
    public void setAttachmentIds(List<String> attachmentIds) { this.attachmentIds = attachmentIds; }
    public List<String> getAttachmentNames() { return attachmentNames; }
    public void setAttachmentNames(List<String> attachmentNames) { this.attachmentNames = attachmentNames; }
    public Map<String, Object> getExtractedSignals() { return extractedSignals; }
    public void setExtractedSignals(Map<String, Object> extractedSignals) { this.extractedSignals = extractedSignals; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public Map<String, Object> getUpdatePayload() { return updatePayload; }
    public void setUpdatePayload(Map<String, Object> updatePayload) { this.updatePayload = updatePayload; }
}
