package com.codemind.platform.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AssistantMessageRequest {

    private String message;
    private UUID selectedNodeId;
    private String selectedArtifactType;
    private String expectedIntent;
    private List<AssistantAttachmentRef> attachments = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getSelectedNodeId() {
        return selectedNodeId;
    }

    public void setSelectedNodeId(UUID selectedNodeId) {
        this.selectedNodeId = selectedNodeId;
    }

    public String getSelectedArtifactType() {
        return selectedArtifactType;
    }

    public void setSelectedArtifactType(String selectedArtifactType) {
        this.selectedArtifactType = selectedArtifactType;
    }

    public String getExpectedIntent() {
        return expectedIntent;
    }

    public void setExpectedIntent(String expectedIntent) {
        this.expectedIntent = expectedIntent;
    }

    public List<AssistantAttachmentRef> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AssistantAttachmentRef> attachments) {
        this.attachments = attachments;
    }
}