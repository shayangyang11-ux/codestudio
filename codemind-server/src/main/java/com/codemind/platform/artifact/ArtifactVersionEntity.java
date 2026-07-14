package com.codemind.platform.artifact;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "artifact_version")
public class ArtifactVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "artifact_type", nullable = false)
    private String artifactType;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "title")
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> content;

    @Column(name = "content_markdown")
    private String contentMarkdown;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "is_current", nullable = false)
    private Boolean current = true;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "prompt_template_id")
    private UUID promptTemplateId;

    @Column(name = "prompt_template_version_id")
    private UUID promptTemplateVersionId;

    @Column(name = "prompt_template_key", length = 128)
    private String promptTemplateKey;

    @Column(name = "prompt_template_version", length = 64)
    private String promptTemplateVersion;

    @Column(name = "prompt_template_checksum", length = 128)
    private String promptTemplateChecksum;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (status == null) {
            status = "DRAFT";
        }

        if (current == null) {
            current = true;
        }

        if (versionNo == null) {
            versionNo = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getModuleId() {
        return moduleId;
    }

    public void setModuleId(UUID moduleId) {
        this.moduleId = moduleId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public void setContentMarkdown(String contentMarkdown) {
        this.contentMarkdown = contentMarkdown;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(Boolean current) {
        this.current = current;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(UUID promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public UUID getPromptTemplateVersionId() {
        return promptTemplateVersionId;
    }

    public void setPromptTemplateVersionId(UUID promptTemplateVersionId) {
        this.promptTemplateVersionId = promptTemplateVersionId;
    }

    public String getPromptTemplateKey() {
        return promptTemplateKey;
    }

    public void setPromptTemplateKey(String promptTemplateKey) {
        this.promptTemplateKey = promptTemplateKey;
    }

    public String getPromptTemplateVersion() {
        return promptTemplateVersion;
    }

    public void setPromptTemplateVersion(String promptTemplateVersion) {
        this.promptTemplateVersion = promptTemplateVersion;
    }

    public String getPromptTemplateChecksum() {
        return promptTemplateChecksum;
    }

    public void setPromptTemplateChecksum(String promptTemplateChecksum) {
        this.promptTemplateChecksum = promptTemplateChecksum;
    }
}