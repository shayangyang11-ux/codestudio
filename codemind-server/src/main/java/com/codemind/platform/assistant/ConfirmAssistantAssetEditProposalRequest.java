package com.codemind.platform.assistant;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConfirmAssistantAssetEditProposalRequest {

    private Map<String, Object> proposal = new LinkedHashMap<>();
    private String confirmationNote;

    public Map<String, Object> getProposal() {
        return proposal;
    }

    public void setProposal(Map<String, Object> proposal) {
        this.proposal = proposal == null
                ? new LinkedHashMap<>()
                : proposal;
    }

    public String getConfirmationNote() {
        return confirmationNote;
    }

    public void setConfirmationNote(String confirmationNote) {
        this.confirmationNote = confirmationNote;
    }
}
