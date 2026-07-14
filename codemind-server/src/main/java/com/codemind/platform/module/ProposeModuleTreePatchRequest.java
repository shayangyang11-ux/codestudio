package com.codemind.platform.module;

import java.util.UUID;

public class ProposeModuleTreePatchRequest {

    private String instruction;
    private UUID selectedNodeId;

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
}