package com.codemind.platform.prompt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "prompt_template_version")
public class PromptTemplateVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "version_label", nullable = false, length = 64)
    private String versionLabel;

    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;

    @Column(name = "user_prompt_template", nullable = false)
    private String userPromptTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", columnDefinition = "jsonb")
    private Map<String, Object> outputSchema;

    @Column(name = "checksum", nullable = false, length = 128)
    private String checksum;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "is_current", nullable = false)
    private Boolean current;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "deprecated_at")
    private LocalDateTime deprecatedAt;

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (effectiveFrom == null) effectiveFrom = now;
        if (status == null) status = "ACTIVE";
        if (current == null) current = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public Integer getVersionNo() { return versionNo; }
    public String getVersionLabel() { return versionLabel; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPromptTemplate() { return userPromptTemplate; }
    public Map<String, Object> getOutputSchema() { return outputSchema; }
    public String getChecksum() { return checksum; }
    public String getStatus() { return status; }
    public Boolean getCurrent() { return current; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public LocalDateTime getDeprecatedAt() { return deprecatedAt; }
    public String getChangeSummary() { return changeSummary; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}