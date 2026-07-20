package com.codemind.platform.theme;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProjectThemeProfileApplicationLogResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID projectId;
    private String projectCode;
    private String source;
    private String styleText;
    private String targetPageType;
    private String agentMode;
    private Double confidence;
    private String summary;
    private Map<String, Object> beforeProfile;
    private Map<String, Object> suggestion;
    private Map<String, Object> updatePayload;
    private Map<String, Object> resultProfile;
    private List<String> changedFields;
    private List<String> reasons;
    private List<String> warnings;
    private String createdAt;

    public ProjectThemeProfileApplicationLogResponse() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStyleText() { return styleText; }
    public void setStyleText(String styleText) { this.styleText = styleText; }
    public String getTargetPageType() { return targetPageType; }
    public void setTargetPageType(String targetPageType) { this.targetPageType = targetPageType; }
    public String getAgentMode() { return agentMode; }
    public void setAgentMode(String agentMode) { this.agentMode = agentMode; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Map<String, Object> getBeforeProfile() { return beforeProfile; }
    public void setBeforeProfile(Map<String, Object> beforeProfile) { this.beforeProfile = beforeProfile; }
    public Map<String, Object> getSuggestion() { return suggestion; }
    public void setSuggestion(Map<String, Object> suggestion) { this.suggestion = suggestion; }
    public Map<String, Object> getUpdatePayload() { return updatePayload; }
    public void setUpdatePayload(Map<String, Object> updatePayload) { this.updatePayload = updatePayload; }
    public Map<String, Object> getResultProfile() { return resultProfile; }
    public void setResultProfile(Map<String, Object> resultProfile) { this.resultProfile = resultProfile; }
    public List<String> getChangedFields() { return changedFields; }
    public void setChangedFields(List<String> changedFields) { this.changedFields = changedFields; }
    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
