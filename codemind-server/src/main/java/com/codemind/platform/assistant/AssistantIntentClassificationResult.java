package com.codemind.platform.assistant;

import java.util.ArrayList;
import java.util.List;

public class AssistantIntentClassificationResult {

    private String intent;
    private Double confidence;
    private String source;
    private String reason;
    private String normalizedMessage;
    private String suggestedReply;
    private boolean requiresUserConfirmation;
    private String artifactType;
    private String taskType;
    private boolean fullChain;
    private boolean coreOnly = true;
    private List<String> riskNotes = new ArrayList<>();

    public static AssistantIntentClassificationResult of(
            String intent,
            String source,
            double confidence,
            String reason
    ) {
        AssistantIntentClassificationResult result = new AssistantIntentClassificationResult();
        result.setIntent(intent);
        result.setSource(source);
        result.setConfidence(confidence);
        result.setReason(reason);
        result.setNormalizedMessage("");
        result.setSuggestedReply("");
        result.setRequiresUserConfirmation(false);
        result.setFullChain(false);
        result.setCoreOnly(true);
        result.setRiskNotes(new ArrayList<>());
        return result;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNormalizedMessage() {
        return normalizedMessage;
    }

    public void setNormalizedMessage(String normalizedMessage) {
        this.normalizedMessage = normalizedMessage;
    }

    public String getSuggestedReply() {
        return suggestedReply;
    }

    public void setSuggestedReply(String suggestedReply) {
        this.suggestedReply = suggestedReply;
    }

    public boolean isRequiresUserConfirmation() {
        return requiresUserConfirmation;
    }

    public void setRequiresUserConfirmation(boolean requiresUserConfirmation) {
        this.requiresUserConfirmation = requiresUserConfirmation;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public boolean isFullChain() {
        return fullChain;
    }

    public void setFullChain(boolean fullChain) {
        this.fullChain = fullChain;
    }

    public boolean isCoreOnly() {
        return coreOnly;
    }

    public void setCoreOnly(boolean coreOnly) {
        this.coreOnly = coreOnly;
    }

    public List<String> getRiskNotes() {
        return riskNotes;
    }

    public void setRiskNotes(List<String> riskNotes) {
        this.riskNotes = riskNotes == null ? new ArrayList<>() : riskNotes;
    }
}