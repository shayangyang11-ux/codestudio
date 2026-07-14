package com.codemind.platform.assistant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssistantAttachmentRef {

    private UUID attachmentId;
    private String fileName;
    private String mimeType;
    private Long size;
    private String parseStatus;
    private String parsedTextExcerpt;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public UUID getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(UUID attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getParsedTextExcerpt() {
        return parsedTextExcerpt;
    }

    public void setParsedTextExcerpt(String parsedTextExcerpt) {
        this.parsedTextExcerpt = parsedTextExcerpt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}