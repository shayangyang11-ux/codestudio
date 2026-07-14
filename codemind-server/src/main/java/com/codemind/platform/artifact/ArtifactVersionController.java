package com.codemind.platform.artifact;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}")
public class ArtifactVersionController {

    private final ProjectAccessService projectAccessService;
    private final ArtifactService artifactService;
    private final CurrentUserProvider currentUserProvider;

    public ArtifactVersionController(
            ProjectAccessService projectAccessService,
            ArtifactService artifactService,
            CurrentUserProvider currentUserProvider
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactService = artifactService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/modules/{moduleId}/artifacts/versions")
    public ArtifactVersionListResponse listModuleArtifactVersions(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam String artifactType
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        List<ArtifactVersionSummaryResponse> versions = artifactService
                .listModuleArtifactVersions(
                        project.getId(),
                        module.getId(),
                        artifactType
                )
                .stream()
                .map(this::toSummaryResponse)
                .toList();

        return new ArtifactVersionListResponse(
                project.getId(),
                module.getId(),
                artifactType,
                versions
        );
    }

    @GetMapping("/modules/{moduleId}/artifacts/versions/{artifactId}")
    public ArtifactVersionDetailResponse getModuleArtifactVersion(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @PathVariable UUID artifactId,
            @RequestParam String artifactType
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        ArtifactVersionEntity artifact = artifactService.getModuleArtifactVersion(
                project.getId(),
                module.getId(),
                artifactType,
                artifactId
        );

        return toDetailResponse(artifact);
    }

    @PostMapping("/modules/{moduleId}/artifacts/versions/{artifactId}/rollback")
    public ArtifactVersionDetailResponse rollbackModuleArtifactVersion(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @PathVariable UUID artifactId,
            @RequestParam String artifactType
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactVersionEntity artifact = artifactService.rollbackModuleArtifactToVersion(
                project.getId(),
                module.getId(),
                artifactType,
                artifactId,
                currentUser.username()
        );

        return toDetailResponse(artifact);
    }

    @PostMapping("/modules/{moduleId}/artifacts/versions/{artifactId}/submit-review")
    public ArtifactVersionDetailResponse submitModuleArtifactReview(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @PathVariable UUID artifactId,
            @RequestParam String artifactType,
            @RequestBody(required = false) ReviewActionRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactVersionEntity artifact = artifactService.submitModuleArtifactReview(
                project.getId(),
                module.getId(),
                artifactType,
                artifactId,
                currentUser.username(),
                request == null ? "" : request.reason()
        );

        return toDetailResponse(artifact);
    }

    @PostMapping("/modules/{moduleId}/artifacts/versions/{artifactId}/approve")
    public ArtifactVersionDetailResponse approveModuleArtifactVersion(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @PathVariable UUID artifactId,
            @RequestParam String artifactType,
            @RequestBody(required = false) ReviewActionRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactVersionEntity artifact = artifactService.approveModuleArtifactVersion(
                project.getId(),
                module.getId(),
                artifactType,
                artifactId,
                currentUser.username(),
                request == null ? "" : request.reason()
        );

        return toDetailResponse(artifact);
    }

    @PostMapping("/modules/{moduleId}/artifacts/versions/{artifactId}/reject")
    public ArtifactVersionDetailResponse rejectModuleArtifactVersion(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @PathVariable UUID artifactId,
            @RequestParam String artifactType,
            @RequestBody(required = false) ReviewActionRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactVersionEntity artifact = artifactService.rejectModuleArtifactVersion(
                project.getId(),
                module.getId(),
                artifactType,
                artifactId,
                currentUser.username(),
                request == null ? "" : request.reason()
        );

        return toDetailResponse(artifact);
    }

    @PostMapping("/modules/{moduleId}/artifacts/versions/{artifactId}/archive")
    public ArtifactVersionDetailResponse archiveModuleArtifactVersion(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @PathVariable UUID artifactId,
            @RequestParam String artifactType,
            @RequestBody(required = false) ReviewActionRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactVersionEntity artifact = artifactService.archiveModuleArtifactVersion(
                project.getId(),
                module.getId(),
                artifactType,
                artifactId,
                currentUser.username(),
                request == null ? "" : request.reason()
        );

        return toDetailResponse(artifact);
    }

    @PostMapping("/modules/{moduleId}/artifacts/quality-checks")
    public ArtifactQualityCheckRecordResponse runModuleArtifactQualityCheck(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactQualityCheckRecordEntity record =
                artifactService.runAndPersistModuleQualityCheck(
                        project.getId(),
                        module.getId(),
                        currentUser.username()
                );

        return toQualityCheckRecordResponse(record);
    }

    @GetMapping("/modules/{moduleId}/artifacts/quality-checks/latest")
    public ArtifactQualityCheckRecordResponse getLatestModuleArtifactQualityCheck(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        ArtifactQualityCheckRecordEntity record =
                artifactService.getLatestModuleQualityCheck(
                        project.getId(),
                        module.getId()
                );

        return toQualityCheckRecordResponse(record);
    }

    @GetMapping("/modules/{moduleId}/artifacts/export.zip")
    public ResponseEntity<byte[]> exportModuleArtifactsZip(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactService.ModuleArtifactZipExport export =
                artifactService.exportCurrentModuleArtifactsZip(
                        project.getId(),
                        projectCode,
                        module.getId(),
                        module.getModuleKey(),
                        module.getModuleName(),
                        currentUser.username(),
                        force
                );

        ContentDisposition contentDisposition = ContentDisposition
                .attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(export.content().length)
                .body(export.content());
    }

    @GetMapping("/modules/{moduleId}/artifacts/export.docx")
    public ResponseEntity<byte[]> exportModuleArtifactsDocx(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactService.ModuleArtifactDocxExport export =
                artifactService.exportCurrentModuleArtifactsDocx(
                        project.getId(),
                        projectCode,
                        module.getId(),
                        module.getModuleKey(),
                        module.getModuleName(),
                        currentUser.username(),
                        force
                );

        ContentDisposition contentDisposition = ContentDisposition
                .attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ))
                .contentLength(export.content().length)
                .body(export.content());
    }

    @GetMapping("/artifacts/export-project.zip")
    public ResponseEntity<byte[]> exportProjectArtifactsZip(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        ArtifactService.ProjectArtifactZipExport export =
                artifactService.exportProjectArtifactsZip(
                        project.getId(),
                        projectCode,
                        project.getProjectName(),
                        currentUser.username(),
                        force
                );

        ContentDisposition contentDisposition = ContentDisposition
                .attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(export.content().length)
                .body(export.content());
    }

    private ArtifactVersionSummaryResponse toSummaryResponse(
            ArtifactVersionEntity artifact
    ) {
        return new ArtifactVersionSummaryResponse(
                artifact.getId(),
                artifact.getProjectId(),
                artifact.getModuleId(),
                artifact.getTaskId(),
                artifact.getArtifactType(),
                artifact.getVersionNo(),
                artifact.getTitle(),
                artifact.getStatus(),
                artifact.getCurrent(),
                artifact.getCreatedBy(),
                artifact.getCreatedAt(),
                artifact.getUpdatedAt(),
                artifact.getPromptTemplateId(),
                artifact.getPromptTemplateVersionId(),
                artifact.getPromptTemplateKey(),
                artifact.getPromptTemplateVersion(),
                artifact.getPromptTemplateChecksum()
        );
    }

    private ArtifactVersionDetailResponse toDetailResponse(
            ArtifactVersionEntity artifact
    ) {
        return new ArtifactVersionDetailResponse(
                true,
                artifact.getId(),
                artifact.getProjectId(),
                artifact.getModuleId(),
                artifact.getTaskId(),
                artifact.getArtifactType(),
                artifact.getVersionNo(),
                artifact.getTitle(),
                artifact.getStatus(),
                artifact.getCurrent(),
                artifact.getContent(),
                artifact.getContentMarkdown(),
                artifact.getCreatedBy(),
                artifact.getCreatedAt(),
                artifact.getUpdatedAt(),
                artifact.getPromptTemplateId(),
                artifact.getPromptTemplateVersionId(),
                artifact.getPromptTemplateKey(),
                artifact.getPromptTemplateVersion(),
                artifact.getPromptTemplateChecksum()
        );
    }

    public record ArtifactVersionListResponse(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            List<ArtifactVersionSummaryResponse> versions
    ) {
    }

    public record ArtifactVersionSummaryResponse(
            UUID artifactId,
            UUID projectId,
            UUID moduleId,
            UUID taskId,
            String artifactType,
            Integer versionNo,
            String title,
            String status,
            Boolean current,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            UUID promptTemplateId,
            UUID promptTemplateVersionId,
            String promptTemplateKey,
            String promptTemplateVersion,
            String promptTemplateChecksum
    ) {
    }

    public record ArtifactVersionDetailResponse(
            boolean exists,
            UUID artifactId,
            UUID projectId,
            UUID moduleId,
            UUID taskId,
            String artifactType,
            Integer versionNo,
            String title,
            String status,
            Boolean current,
            Map<String, Object> content,
            String contentMarkdown,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            UUID promptTemplateId,
            UUID promptTemplateVersionId,
            String promptTemplateKey,
            String promptTemplateVersion,
            String promptTemplateChecksum
    ) {
    }

    public record ReviewActionRequest(
            String reason
    ) {
    }

    private ArtifactQualityCheckRecordResponse toQualityCheckRecordResponse(
            ArtifactQualityCheckRecordEntity record
    ) {
        return new ArtifactQualityCheckRecordResponse(
                record.getId(),
                record.getProjectId(),
                record.getModuleId(),
                record.getCheckScope(),
                record.getScore(),
                record.getAssetCount(),
                record.getBlockerCount(),
                record.getWarningCount(),
                record.getPassCount(),
                record.getExportDecision(),
                record.getForcedExport(),
                record.getSource(),
                record.getResultJson(),
                record.getCheckedBy(),
                record.getCheckedAt()
        );
    }

    public record ArtifactQualityCheckRecordResponse(
            UUID id,
            UUID projectId,
            UUID moduleId,
            String checkScope,
            Integer score,
            Integer assetCount,
            Integer blockerCount,
            Integer warningCount,
            Integer passCount,
            String exportDecision,
            Boolean forcedExport,
            String source,
            Map<String, Object> resultJson,
            String checkedBy,
            LocalDateTime checkedAt
    ) {
    }
}