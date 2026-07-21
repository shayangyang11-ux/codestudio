package com.codemind.platform.assistant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssistantAssetEditProposalRequest {

    private Map<String, Object> requirementChangeProposal = new LinkedHashMap<>();
    private String instruction;
    private UUID selectedNodeId;
    private String selectedArtifactType;

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
}
