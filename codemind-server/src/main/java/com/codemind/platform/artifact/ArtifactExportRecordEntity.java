package com.codemind.platform.artifact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "artifact_export_record")
public class ArtifactExportRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "quality_check_id")
    private UUID qualityCheckId;

    @Column(name = "export_scope", nullable = false, length = 32)
    private String exportScope;

    @Column(name = "export_format", nullable = false, length = 32)
    private String exportFormat;

    @Column(name = "export_status", nullable = false, length = 32)
    private String exportStatus;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "forced_export", nullable = false)
    private Boolean forcedExport;

    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(name = "blocker_count", nullable = false)
    private Integer blockerCount;

    @Column(name = "warning_count", nullable = false)
    private Integer warningCount;

    @Column(name = "export_decision", length = 64)
    private String exportDecision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest_json", columnDefinition = "jsonb")
    private Map<String, Object> manifestJson;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "exported_by", length = 128)
    private String exportedBy;

    @Column(name = "exported_at", nullable = false)
    private LocalDateTime exportedAt;

    @PrePersist
    public void prePersist() {
        if (exportedAt == null) exportedAt = LocalDateTime.now();
        if (fileSizeBytes == null) fileSizeBytes = 0L;
        if (forcedExport == null) forcedExport = false;
        if (blockerCount == null) blockerCount = 0;
        if (warningCount == null) warningCount = 0;
    }

    public UUID getId() { return id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getModuleId() { return moduleId; }
    public void setModuleId(UUID moduleId) { this.moduleId = moduleId; }

    public UUID getQualityCheckId() { return qualityCheckId; }
    public void setQualityCheckId(UUID qualityCheckId) { this.qualityCheckId = qualityCheckId; }

    public String getExportScope() { return exportScope; }
    public void setExportScope(String exportScope) { this.exportScope = exportScope; }

    public String getExportFormat() { return exportFormat; }
    public void setExportFormat(String exportFormat) { this.exportFormat = exportFormat; }

    public String getExportStatus() { return exportStatus; }
    public void setExportStatus(String exportStatus) { this.exportStatus = exportStatus; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public Boolean getForcedExport() { return forcedExport; }
    public void setForcedExport(Boolean forcedExport) { this.forcedExport = forcedExport; }

    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public Integer getBlockerCount() { return blockerCount; }
    public void setBlockerCount(Integer blockerCount) { this.blockerCount = blockerCount; }

    public Integer getWarningCount() { return warningCount; }
    public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }

    public String getExportDecision() { return exportDecision; }
    public void setExportDecision(String exportDecision) { this.exportDecision = exportDecision; }

    public Map<String, Object> getManifestJson() { return manifestJson; }
    public void setManifestJson(Map<String, Object> manifestJson) { this.manifestJson = manifestJson; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getExportedBy() { return exportedBy; }
    public void setExportedBy(String exportedBy) { this.exportedBy = exportedBy; }

    public LocalDateTime getExportedAt() { return exportedAt; }
    public void setExportedAt(LocalDateTime exportedAt) { this.exportedAt = exportedAt; }
}