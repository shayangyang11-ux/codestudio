package com.codemind.platform.assistant;

import com.codemind.platform.module.ModuleTreePatchProposalResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AssistantMessageResponse {

    private UUID messageId;
    private String intent;
    private String reply;
    private boolean requiresUserConfirmation;
    private ModuleTreePatchProposalResponse proposal;
    private Map<String, Object> attachmentArtifactDiffProposal;
    private List<AssistantActionResponse> actions = new ArrayList<>();

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public boolean isRequiresUserConfirmation() {
        return requiresUserConfirmation;
    }

    public void setRequiresUserConfirmation(boolean requiresUserConfirmation) {
        this.requiresUserConfirmation = requiresUserConfirmation;
    }

    public ModuleTreePatchProposalResponse getProposal() {
        return proposal;
    }

    public void setProposal(ModuleTreePatchProposalResponse proposal) {
        this.proposal = proposal;
    }

    public Map<String, Object> getAttachmentArtifactDiffProposal() {
        return attachmentArtifactDiffProposal;
    }

    public void setAttachmentArtifactDiffProposal(Map<String, Object> attachmentArtifactDiffProposal) {
        this.attachmentArtifactDiffProposal = attachmentArtifactDiffProposal;
    }

    public List<AssistantActionResponse> getActions() {
        return actions;
    }

    public void setActions(List<AssistantActionResponse> actions) {
        this.actions = actions;
    }
}
