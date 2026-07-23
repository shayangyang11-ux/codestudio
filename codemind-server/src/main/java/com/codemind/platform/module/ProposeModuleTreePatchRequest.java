
package com.codemind.platform.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProposeModuleTreePatchRequest {

    private String instruction;
    private UUID selectedNodeId;
    private UUID baseArtifactId;
    private Integer baseVersionNo;
    private String baseChecksum;
    private List<String> selectedDiffItemIds = new ArrayList<>();
    private List<Map<String, Object>> selectedDiffItems = new ArrayList<>();
    private List<Map<String, Object>> attachmentContexts = new ArrayList<>();
    private Map<String, Object> attachmentSummary = new LinkedHashMap<>();

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public UUID getSelectedNodeId() {
        return selectedNodeId;
    }

    public void setSelectedNodeId(UUID selectedNodeId) {
        this.selectedNodeId = selectedNodeId;
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

    public List<Map<String, Object>> getAttachmentContexts() {
        return attachmentContexts;
    }

    public void setAttachmentContexts(List<Map<String, Object>> attachmentContexts) {
        this.attachmentContexts = attachmentContexts == null
                ? new ArrayList<>()
                : new ArrayList<>(attachmentContexts);
    }

    public Map<String, Object> getAttachmentSummary() {
        return attachmentSummary;
    }

    public void setAttachmentSummary(Map<String, Object> attachmentSummary) {
        this.attachmentSummary = attachmentSummary == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(attachmentSummary);
    }

}
