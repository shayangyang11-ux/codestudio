package com.codemind.platform.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AssistantAssetEditProposalRequest {

    private Map<String, Object> requirementChangeProposal = new LinkedHashMap<>();
    private String instruction;
    private UUID selectedNodeId;
    private String selectedArtifactType;
    private UUID baseArtifactId;
    private Integer baseVersionNo;
    private String baseChecksum;
    private String baseModuleTreeHash;
    private Integer baseModuleTreeNodeCount;
    private List<String> selectedDiffItemIds = new ArrayList<>();
    private List<Map<String, Object>> selectedDiffItems = new ArrayList<>();

    public Map<String, Object> getRequirementChangeProposal() {
        return requirementChangeProposal;
    }

    public void setRequirementChangeProposal(Map<String, Object> requirementChangeProposal) {
        this.requirementChangeProposal = requirementChangeProposal == null
                ? new LinkedHashMap<>()
                : requirementChangeProposal;
    }

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

    public String getSelectedArtifactType() {
        return selectedArtifactType;
    }

    public void setSelectedArtifactType(String selectedArtifactType) {
        this.selectedArtifactType = selectedArtifactType;
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

    public String getBaseModuleTreeHash() {
        return baseModuleTreeHash;
    }

    public void setBaseModuleTreeHash(String baseModuleTreeHash) {
        this.baseModuleTreeHash = baseModuleTreeHash;
    }

    public Integer getBaseModuleTreeNodeCount() {
        return baseModuleTreeNodeCount;
    }

    public void setBaseModuleTreeNodeCount(Integer baseModuleTreeNodeCount) {
        this.baseModuleTreeNodeCount = baseModuleTreeNodeCount;
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
