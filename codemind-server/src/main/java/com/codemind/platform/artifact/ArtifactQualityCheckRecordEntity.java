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
@Table(name = "artifact_quality_check_record")
public class ArtifactQualityCheckRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "module_id")
    private UUID moduleId;

    @Column(name = "check_scope", nullable = false, length = 32)
    private String checkScope;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "asset_count", nullable = false)
    private Integer assetCount;

    @Column(name = "blocker_count", nullable = false)
    private Integer blockerCount;

    @Column(name = "warning_count", nullable = false)
    private Integer warningCount;

    @Column(name = "pass_count", nullable = false)
    private Integer passCount;

    @Column(name = "export_decision", nullable = false, length = 64)
    private String exportDecision;

    @Column(name = "forced_export", nullable = false)
    private Boolean forcedExport;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> resultJson;

    @Column(name = "checked_by", length = 128)
    private String checkedBy;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @PrePersist
    public void prePersist() {
        if (checkedAt == null) checkedAt = LocalDateTime.now();
        if (forcedExport == null) forcedExport = false;
        if (source == null) source = "MANUAL_CHECK";
        if (assetCount == null) assetCount = 0;
        if (blockerCount == null) blockerCount = 0;
        if (warningCount == null) warningCount = 0;
        if (passCount == null) passCount = 0;
    }

    public UUID getId() { return id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getModuleId() { return moduleId; }
    public void setModuleId(UUID moduleId) { this.moduleId = moduleId; }

    public String getCheckScope() { return checkScope; }
    public void setCheckScope(String checkScope) { this.checkScope = checkScope; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getAssetCount() { return assetCount; }
    public void setAssetCount(Integer assetCount) { this.assetCount = assetCount; }

    public Integer getBlockerCount() { return blockerCount; }
    public void setBlockerCount(Integer blockerCount) { this.blockerCount = blockerCount; }

    public Integer getWarningCount() { return warningCount; }
    public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }

    public Integer getPassCount() { return passCount; }
    public void setPassCount(Integer passCount) { this.passCount = passCount; }

    public String getExportDecision() { return exportDecision; }
    public void setExportDecision(String exportDecision) { this.exportDecision = exportDecision; }

    public Boolean getForcedExport() { return forcedExport; }
    public void setForcedExport(Boolean forcedExport) { this.forcedExport = forcedExport; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Map<String, Object> getResultJson() { return resultJson; }
    public void setResultJson(Map<String, Object> resultJson) { this.resultJson = resultJson; }

    public String getCheckedBy() { return checkedBy; }
    public void setCheckedBy(String checkedBy) { this.checkedBy = checkedBy; }

    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}