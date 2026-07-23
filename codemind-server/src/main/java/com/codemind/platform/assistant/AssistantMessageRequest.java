package com.codemind.platform.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AssistantMessageRequest {

    private String message;
    private UUID selectedNodeId;
    private String selectedArtifactType;
    private String expectedIntent;
    private UUID baseArtifactId;
    private Integer baseVersionNo;
    private String baseChecksum;
    private List<AssistantAttachmentRef> attachments = new ArrayList<>();
    private List<String> selectedDiffItemIds = new ArrayList<>();
    private List<Map<String, Object>> selectedDiffItems = new ArrayList<>();

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

    public UUID getBaseArtifactId() {
        return baseArtifactId;
    }

    public void setBaseArtifactId(UUID baseArtifactId) {
        this.baseArtifactId = baseArtifactId;
    }

    public Integer getBaseVersionNo() {
        return baseVersionNo;
    }

    public void setBaseVersionNo(Integer baseVersionNo) {
        this.baseVersionNo = baseVersionNo;
    }

    public String getBaseChecksum() {
        return baseChecksum;
    }

    public void setBaseChecksum(String baseChecksum) {
        this.baseChecksum = baseChecksum;
    }

    public List<AssistantAttachmentRef> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AssistantAttachmentRef> attachments) {
        this.attachments = attachments == null
                ? new ArrayList<>()
                : new ArrayList<>(attachments);
    }

    public List<String> getSelectedDiffItemIds() {
        return selectedDiffItemIds;
    }

    public void setSelectedDiffItemIds(List<String> selectedDiffItemIds) {
        this.selectedDiffItemIds = selectedDiffItemIds == null
                ? new ArrayList<>()
                : new ArrayList<>(selectedDiffItemIds);
    }

    public List<Map<String, Object>> getSelectedDiffItems() {
        return selectedDiffItems;
    }

    public void setSelectedDiffItems(
            List<Map<String, Object>> selectedDiffItems
    ) {
        if (selectedDiffItems == null) {
            this.selectedDiffItems = new ArrayList<>();
            return;
        }

        this.selectedDiffItems = selectedDiffItems.stream()
                .map(item -> {
                    Map<String, Object> copied = new LinkedHashMap<>();

                    if (item != null) {
                        copied.putAll(item);
                    }

                    return copied;
                })
                .toList();
    }
}
