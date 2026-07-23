package com.codemind.platform.assistant;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectAccessService.ProjectAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantAttachmentArtifactDiffServiceTest {

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectModuleRepository projectModuleRepository;

    @Mock
    private ArtifactVersionRepository artifactVersionRepository;

    private AssistantAttachmentArtifactDiffService service;

    @BeforeEach
    void setUp() {
        service = new AssistantAttachmentArtifactDiffService(
                projectAccessService,
                projectModuleRepository,
                artifactVersionRepository,
                new ObjectMapper()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildDiffResponseClassifiesCoveredMissingAndNumericConflictItems() {
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        ProjectAccess access = new ProjectAccess(
                projectId,
                "demo-project",
                workspaceId,
                "tenant-demo",
                "workspace-demo"
        );

        ProjectModuleEntity module = org.mockito.Mockito.mock(ProjectModuleEntity.class);
        when(module.getId()).thenReturn(moduleId);
        when(module.getProjectId()).thenReturn(projectId);
        when(module.getModuleName()).thenReturn("登录页");
        when(module.getModuleType()).thenReturn("PAGE");
        when(module.getModuleKey()).thenReturn("login-page");
        when(module.getModulePath()).thenReturn("/account/login");

        ArtifactVersionEntity artifact = org.mockito.Mockito.mock(ArtifactVersionEntity.class);
        when(artifact.getId()).thenReturn(artifactId);
        when(artifact.getTitle()).thenReturn("登录页页面规范");
        when(artifact.getVersionNo()).thenReturn(5);
        when(artifact.getStatus()).thenReturn("APPROVED");
        when(artifact.getChecksum()).thenReturn("sha256-current-v5");
        when(artifact.getContentMarkdown()).thenReturn("""
                # 登录页页面规范

                系统支持短信验证码登录。
                验证码有效期为 5 分钟。
                """);

        when(projectAccessService.resolveAccessibleProject(workspaceId, "demo-project"))
                .thenReturn(access);
        when(projectModuleRepository.findById(moduleId))
                .thenReturn(Optional.of(module));
        when(artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        "PAGE_SPEC"
                ))
                .thenReturn(Optional.of(artifact));

        AssistantMessageRequest request = new AssistantMessageRequest();
        request.setSelectedNodeId(moduleId);
        request.setSelectedArtifactType("page_spec");

        AssistantAttachmentParsedContext attachment = new AssistantAttachmentParsedContext();
        attachment.setAttachmentId(UUID.randomUUID());
        attachment.setFileName("login-change.md");
        attachment.setMimeType("text/markdown");
        attachment.setSize(256L);
        attachment.setParseStatus("PARSED");
        attachment.setParsedTextExcerpt("""
                - 支持 短信 验证码 登录
                - 新增 邮箱 验证码 登录
                - 验证码有效期调整为 3 分钟
                """);

        AssistantMessageResponse response = service.buildDiffResponse(
                workspaceId,
                "demo-project",
                request,
                "请对比附件与当前页面规范",
                List.of(attachment)
        );

        assertThat(response.getIntent()).isEqualTo("ANALYZE_ATTACHMENT");
        assertThat(response.isRequiresUserConfirmation()).isTrue();
        assertThat(response.getActions())
                .extracting(AssistantActionResponse::getType)
                .containsExactly("ATTACHMENT_ARTIFACT_DIFF_PROPOSAL");

        Map<String, Object> proposal = response.getAttachmentArtifactDiffProposal();
        assertThat(proposal).isNotNull();
        assertThat(proposal.get("targetArtifactType")).isEqualTo("PAGE_SPEC");

        Map<String, Object> targetArtifact =
                (Map<String, Object>) proposal.get("targetArtifact");
        assertThat(targetArtifact)
                .containsEntry("artifactId", artifactId.toString())
                .containsEntry("versionNo", 5)
                .containsEntry("checksum", "sha256-current-v5");

        List<Map<String, Object>> diffItems =
                (List<Map<String, Object>>) proposal.get("diffItems");

        assertThat(diffItems)
                .extracting(item -> item.get("type"))
                .contains("ALREADY_COVERED", "MISSING", "POTENTIAL_CONFLICT");

        Map<String, Object> numericConflict = diffItems.stream()
                .filter(item -> "POTENTIAL_CONFLICT".equals(item.get("type")))
                .findFirst()
                .orElseThrow();

        assertThat(String.valueOf(numericConflict.get("attachmentExcerpt")))
                .contains("3 分钟");
        assertThat(String.valueOf(numericConflict.get("currentEvidence")))
                .contains("5 分钟");
    }

    @Test
    void buildDiffResponseRequiresSelectedNodeBeforeRepositoryAccess() {
        AssistantMessageRequest request = new AssistantMessageRequest();
        request.setSelectedArtifactType("PAGE_SPEC");

        AssistantAttachmentParsedContext attachment = new AssistantAttachmentParsedContext();
        attachment.setParsedTextExcerpt("新增邮箱验证码登录");

        assertThatThrownBy(() -> service.buildDiffResponse(
                UUID.randomUUID(),
                "demo-project",
                request,
                "对比差异",
                List.of(attachment)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选择一个模块树节点");
    }
}
