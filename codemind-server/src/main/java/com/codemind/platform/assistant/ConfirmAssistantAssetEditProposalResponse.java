package com.codemind.platform.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConfirmAssistantAssetEditProposalResponse {

    private UUID artifactId;
    private UUID projectId;
    private UUID moduleId;
    private String artifactType;
    private Integer versionNo;
    private String title;
    private String status;
    private UUID previousArtifactId;
    private Integer previousVersionNo;
    private List<String> affectedArtifacts = new ArrayList<>();
    private int downstreamMarkedReviewCount;
    private String message;

    public UUID getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(UUID artifactId) {
        this.artifactId = artifactId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getModuleId() {
        return moduleId;
    }

    public void setModuleId(UUID moduleId) {
        this.moduleId = moduleId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getPreviousArtifactId() {
        return previousArtifactId;
    }

    public void setPreviousArtifactId(UUID previousArtifactId) {
        this.previousArtifactId = previousArtifactId;
    }

    public Integer getPreviousVersionNo() {
        return previousVersionNo;
    }

    public void setPreviousVersionNo(Integer previousVersionNo) {
        this.previousVersionNo = previousVersionNo;
    }

    public List<String> getAffectedArtifacts() {
        return affectedArtifacts;
    }

    public void setAffectedArtifacts(List<String> affectedArtifacts) {
        this.affectedArtifacts = affectedArtifacts == null ? new ArrayList<>() : affectedArtifacts;
    }

    public int getDownstreamMarkedReviewCount() {
        return downstreamMarkedReviewCount;
    }

    public void setDownstreamMarkedReviewCount(int downstreamMarkedReviewCount) {
        this.downstreamMarkedReviewCount = downstreamMarkedReviewCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
