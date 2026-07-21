package com.codemind.platform.artifact;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.project.ProjectAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ManualArtifactEditService {

    private static final List<String> MANUAL_EDITABLE_ARTIFACT_TYPES = List.of(
            "MODULE_PRD",
            "BUSINESS_FLOW",
            "PAGE_SPEC",
            "TEST_SPEC"
    );

    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public ManualArtifactEditService(
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ManualArtifactEditResponse saveManualDocumentEdit(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            ManualArtifactEditRequest request
    ) {
        ProjectAccessService.ProjectAccess access =
                projectAccessService.resolveAccessibleProject(workspaceId, projectCode);

        projectAccessService.resolveAccessibleModule(
                access.projectId(),
                moduleId
        );

        String artifactType = normalizeArtifactType(request.artifactType());

        if (!MANUAL_EDITABLE_ARTIFACT_TYPES.contains(artifactType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前资产类型不允许手动编辑文档视图：" + artifactType
            );
        }

        String contentMarkdown = safeTrim(request.contentMarkdown());

        if (contentMarkdown.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "文档内容不能为空。"
            );
        }

        ArtifactVersionEntity previous = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        access.projectId(),
                        moduleId,
                        artifactType
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "当前节点没有可编辑的资产版本：" + artifactType
                ));

        if (request.artifactId() != null && !request.artifactId().isBlank()) {
            UUID requestedArtifactId = parseUuid(
                    request.artifactId(),
                    "artifactId"
            );

            if (!requestedArtifactId.equals(previous.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "当前资产版本已变化，请刷新后再编辑。"
                );
            }
        }

        String previousMarkdown = safeTrim(previous.getContentMarkdown());

        if (normalizeMarkdown(previousMarkdown).equals(normalizeMarkdown(contentMarkdown))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "文档内容没有变化，无需生成新版本。"
            );
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        String username = currentUser.username();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> content = copyContent(previous.getContent());
        content.put(
                "_manual_document_edit",
                buildManualEditMetadata(
                        previous,
                        now,
                        username,
                        request.reason()
                )
        );

        int maxVersionNo = artifactVersionRepository.findMaxModuleArtifactVersionNo(
                access.projectId(),
                moduleId,
                artifactType
        );

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                access.projectId(),
                moduleId,
                artifactType
        );

        ArtifactVersionEntity saved = new ArtifactVersionEntity();
        saved.setProjectId(access.projectId());
        saved.setModuleId(moduleId);
        saved.setTaskId(null);
        saved.setArtifactType(artifactType);
        saved.setVersionNo(maxVersionNo + 1);
        saved.setTitle(previous.getTitle());
        saved.setContent(content);
        saved.setContentMarkdown(contentMarkdown);
        saved.setStatus("PENDING_REVIEW");
        saved.setCurrent(true);
        saved.setCreatedBy(username);
        copyPromptTemplateMetadata(previous, saved);

        ArtifactVersionEntity persisted = artifactVersionRepository.save(saved);

        List<String> downstreamArtifactTypes = resolveDownstreamArtifactTypes(artifactType);

        int downstreamMarkedCount = 0;

        if (!downstreamArtifactTypes.isEmpty()) {
            downstreamMarkedCount = artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                    access.projectId(),
                    List.of(moduleId),
                    downstreamArtifactTypes
            );
        }

        return toResponse(
                persisted,
                downstreamArtifactTypes,
                downstreamMarkedCount
        );
    }

    private String normalizeArtifactType(
            String value
    ) {
        String artifactType = safeTrim(value).toUpperCase();

        if (artifactType.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缺少 artifactType。"
            );
        }

        return artifactType;
    }

    private UUID parseUuid(
            String value,
            String fieldName
    ) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "非法 " + fieldName + "：" + value
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyContent(
            Map<String, Object> content
    ) {
        if (content == null || content.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return objectMapper.convertValue(
                content,
                LinkedHashMap.class
        );
    }

    private Map<String, Object> buildManualEditMetadata(
            ArtifactVersionEntity previous,
            LocalDateTime editedAt,
            String editedBy,
            String reason
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "1.0");
        metadata.put("editMode", "MANUAL_DOCUMENT_VIEW");
        metadata.put("sourceArtifactId", previous.getId().toString());
        metadata.put("sourceVersionNo", previous.getVersionNo());
        metadata.put("editedAt", editedAt.toString());
        metadata.put("editedBy", editedBy);
        metadata.put("reason", safeTrim(reason));
        metadata.put(
                "cardViewPolicy",
                "MANUAL_MARKDOWN_SECTIONS"
        );
        metadata.put(
                "note",
                "当前版本由文档视图手动编辑生成，卡片视图应根据 contentMarkdown 重新拆分展示。"
        );
        return metadata;
    }

    private void copyPromptTemplateMetadata(
            ArtifactVersionEntity source,
            ArtifactVersionEntity target
    ) {
        target.setPromptTemplateId(source.getPromptTemplateId());
        target.setPromptTemplateVersionId(source.getPromptTemplateVersionId());
        target.setPromptTemplateKey(source.getPromptTemplateKey());
        target.setPromptTemplateVersion(source.getPromptTemplateVersion());
        target.setPromptTemplateChecksum(source.getPromptTemplateChecksum());
    }

    private List<String> resolveDownstreamArtifactTypes(
            String artifactType
    ) {
        return switch (artifactType) {
            case "MODULE_PRD" -> List.of(
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "BUSINESS_FLOW" -> List.of(
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "PAGE_SPEC" -> List.of(
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "TEST_SPEC" -> List.of(
                    "DEV_HANDOFF_PACKAGE"
            );
            default -> List.of();
        };
    }

    private ManualArtifactEditResponse toResponse(
            ArtifactVersionEntity artifact,
            List<String> downstreamArtifactTypes,
            int downstreamMarkedCount
    ) {
        return new ManualArtifactEditResponse(
                artifact.getId(),
                artifact.getProjectId(),
                artifact.getModuleId(),
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
                downstreamArtifactTypes,
                downstreamMarkedCount,
                "文档编辑已保存为新版本 V" + artifact.getVersionNo() + "，当前状态为新版本待确认；下游资产已标记为受上游影响。"
        );
    }

    private String normalizeMarkdown(
            String value
    ) {
        return safeTrim(value).replace("\r\n", "\n");
    }

    private String safeTrim(
            String value
    ) {
        return value == null ? "" : value.trim();
    }
}



