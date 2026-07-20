package com.codemind.platform.theme;

import java.util.List;

public class ProjectStyleAssistantRequest {

    private String instruction;
    private String referenceText;
    private String referenceNotes;
    private String referenceMode;
    private String targetPageType;
    private Boolean allowDarkMode;
    private List<String> attachmentIds;
    private List<String> attachmentNames;

    public ProjectStyleAssistantRequest() {
    }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public String getReferenceText() { return referenceText; }
    public void setReferenceText(String referenceText) { this.referenceText = referenceText; }
    public String getReferenceNotes() { return referenceNotes; }
    public void setReferenceNotes(String referenceNotes) { this.referenceNotes = referenceNotes; }
    public String getReferenceMode() { return referenceMode; }
    public void setReferenceMode(String referenceMode) { this.referenceMode = referenceMode; }
    public String getTargetPageType() { return targetPageType; }
    public void setTargetPageType(String targetPageType) { this.targetPageType = targetPageType; }
    public Boolean getAllowDarkMode() { return allowDarkMode; }
    public void setAllowDarkMode(Boolean allowDarkMode) { this.allowDarkMode = allowDarkMode; }
    public List<String> getAttachmentIds() { return attachmentIds; }
    public void setAttachmentIds(List<String> attachmentIds) { this.attachmentIds = attachmentIds; }
    public List<String> getAttachmentNames() { return attachmentNames; }
    public void setAttachmentNames(List<String> attachmentNames) { this.attachmentNames = attachmentNames; }
}
