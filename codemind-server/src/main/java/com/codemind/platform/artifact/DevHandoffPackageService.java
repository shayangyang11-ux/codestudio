package com.codemind.platform.artifact;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ModuleTreeBaselineSupport;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectAccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DevHandoffPackageService {

    private static final String DEV_HANDOFF_PACKAGE = "DEV_HANDOFF_PACKAGE";
    private static final String STATUS_APPROVED = "APPROVED";

    private final ProjectAccessService projectAccessService;
    private final ProjectModuleRepository projectModuleRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public DevHandoffPackageService(
            ProjectAccessService projectAccessService,
            ProjectModuleRepository projectModuleRepository,
            ArtifactVersionRepository artifactVersionRepository,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.projectAccessService = projectAccessService;
        this.projectModuleRepository = projectModuleRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DevHandoffPackageResponse generate(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateDevHandoffPackageRequest request
    ) {
        ProjectAccessService.ProjectAccess access =
                projectAccessService.resolveAccessibleProject(
                        workspaceId,
                        projectCode
                );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                access.projectId(),
                moduleId
        );

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        String username = currentUser.username();
        LocalDateTime generatedAt = LocalDateTime.now();

        boolean includeUnapproved = request == null || request.includeUnapproved() == null
                || Boolean.TRUE.equals(request.includeUnapproved());

        List<ProjectModuleEntity> modules = projectModuleRepository
                .findByProjectIdOrderByLevelNoAscSortOrderAsc(
                        access.projectId()
                );

        ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline =
                ModuleTreeBaselineSupport.capture(modules);

        Map<String, ArtifactVersionEntity> currentArtifactsByType =
                loadCurrentArtifactsByType(
                        access.projectId(),
                        moduleId
                );

        List<ArtifactRequirement> requirements = resolveArtifactRequirements(module);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> sourceArtifacts = new ArrayList<>();
        List<ArtifactSection> artifactSections = new ArrayList<>();

        for (ArtifactRequirement requirement : requirements) {
            ArtifactVersionEntity artifact = currentArtifactsByType.get(requirement.artifactType());
            boolean exists = artifact != null;
            boolean approved = exists && STATUS_APPROVED.equalsIgnoreCase(safeText(artifact.getStatus()));
            boolean included = exists && (approved || includeUnapproved);

            if (requirement.required() && !exists) {
                blockers.add("缺少必需资产：" + requirement.title() + "（" + requirement.artifactType() + "）。");
            }

            if (exists && requirement.required() && !approved && !includeUnapproved) {
                blockers.add("必需资产尚未批准：" + requirement.title() + "（" + requirement.artifactType() + " / " + safeText(artifact.getStatus()) + "）。");
            }

            if (exists && !approved) {
                warnings.add("资产尚未批准，已作为待复核材料纳入交接包：" + requirement.title() + "（" + requirement.artifactType() + " / " + safeText(artifact.getStatus()) + "）。");
            }

            if (!exists && !requirement.required()) {
                warnings.add("可选资产未生成：" + requirement.title() + "（" + requirement.artifactType() + "）。");
            }

            Map<String, Object> sourceManifest = buildSourceArtifactManifest(
                    requirement,
                    artifact,
                    included,
                    approved
            );

            sourceArtifacts.add(sourceManifest);

            if (included && artifact != null) {
                artifactSections.add(
                        new ArtifactSection(
                                requirement,
                                artifact,
                                approved
                        )
                );
            }
        }

        String markdown = buildMarkdown(
                access,
                module,
                generatedAt,
                username,
                moduleTreeBaseline,
                sourceArtifacts,
                artifactSections,
                blockers,
                warnings,
                request == null ? null : request.note()
        );

        Map<String, Object> content = buildContent(
                access,
                module,
                generatedAt,
                username,
                moduleTreeBaseline,
                sourceArtifacts,
                blockers,
                warnings,
                request,
                markdown
        );

        int maxVersionNo = artifactVersionRepository.findMaxModuleArtifactVersionNo(
                access.projectId(),
                moduleId,
                DEV_HANDOFF_PACKAGE
        );

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                access.projectId(),
                moduleId,
                DEV_HANDOFF_PACKAGE
        );

        ArtifactVersionEntity saved = new ArtifactVersionEntity();
        saved.setProjectId(access.projectId());
        saved.setModuleId(moduleId);
        saved.setTaskId(null);
        saved.setArtifactType(DEV_HANDOFF_PACKAGE);
        saved.setVersionNo(maxVersionNo + 1);
        saved.setTitle("开发交接包 - " + safeTitle(module.getModuleName()));
        saved.setContent(content);
        saved.setContentMarkdown(markdown);
        saved.setStatus(blockers.isEmpty() ? "PENDING_REVIEW" : "NEEDS_REVIEW");
        saved.setCurrent(true);
        saved.setChecksum(sha256Hex(markdown));
        saved.setCreatedBy(username);

        ArtifactVersionEntity persisted = artifactVersionRepository.save(saved);

        return new DevHandoffPackageResponse(
                persisted.getId(),
                persisted.getProjectId(),
                persisted.getModuleId(),
                persisted.getArtifactType(),
                persisted.getVersionNo(),
                persisted.getTitle(),
                persisted.getStatus(),
                persisted.getCurrent(),
                persisted.getContent(),
                persisted.getContentMarkdown(),
                sourceArtifacts,
                blockers,
                warnings,
                buildResponseMessage(persisted, blockers, warnings)
        );
    }

    private Map<String, ArtifactVersionEntity> loadCurrentArtifactsByType(
            UUID projectId,
            UUID moduleId
    ) {
        List<ArtifactVersionEntity> artifacts = artifactVersionRepository
                .findByProjectIdAndModuleIdAndCurrentTrue(
                        projectId,
                        moduleId
                );

        Map<String, ArtifactVersionEntity> result = new LinkedHashMap<>();

        for (ArtifactVersionEntity artifact : artifacts) {
            if (artifact == null || artifact.getArtifactType() == null) {
                continue;
            }

            result.put(
                    artifact.getArtifactType(),
                    artifact
            );
        }

        return result;
    }

    private List<ArtifactRequirement> resolveArtifactRequirements(
            ProjectModuleEntity module
    ) {
        String nodeType = normalizeNodeType(module == null ? null : module.getModuleType());

        return switch (nodeType) {
            case "PAGE" -> List.of(
                    required("PAGE_SPEC", "页面规范"),
                    optional("MODULE_PRD", "页面需求"),
                    optional("BUSINESS_FLOW", "页面流程"),
                    optional("DATA_MODEL", "页面数据模型"),
                    required("API_CONTRACT", "页面接口契约"),
                    optional("TEST_SPEC", "页面测试规格"),
                    required("UI_CONTRACT", "交互原型")
            );
            case "FEATURE" -> List.of(
                    required("MODULE_PRD", "功能点需求"),
                    optional("BUSINESS_FLOW", "功能点流程"),
                    optional("PAGE_SPEC", "所属页面规范"),
                    optional("DATA_MODEL", "功能点数据模型"),
                    required("API_CONTRACT", "功能点接口契约"),
                    required("TEST_SPEC", "功能点测试规格"),
                    optional("UI_CONTRACT", "相关交互契约")
            );
            case "SUBMODULE" -> List.of(
                    required("MODULE_PRD", "场景需求说明"),
                    required("BUSINESS_FLOW", "场景业务流程"),
                    required("PAGE_SPEC", "场景页面清单"),
                    optional("DATA_MODEL", "场景数据模型"),
                    optional("API_CONTRACT", "场景接口清单"),
                    optional("TEST_SPEC", "场景测试规格"),
                    optional("UI_CONTRACT", "场景交互原型")
            );
            default -> List.of(
                    required("MODULE_PRD", "模块 PRD"),
                    required("BUSINESS_FLOW", "业务流程"),
                    required("PAGE_SPEC", "页面地图/页面规范"),
                    optional("DATA_MODEL", "数据模型"),
                    required("API_CONTRACT", "接口规划"),
                    optional("TEST_SPEC", "测试规格"),
                    optional("UI_CONTRACT", "交互原型")
            );
        };
    }

    private ArtifactRequirement required(
            String artifactType,
            String title
    ) {
        return new ArtifactRequirement(
                artifactType,
                title,
                true
        );
    }

    private ArtifactRequirement optional(
            String artifactType,
            String title
    ) {
        return new ArtifactRequirement(
                artifactType,
                title,
                false
        );
    }

    private Map<String, Object> buildSourceArtifactManifest(
            ArtifactRequirement requirement,
            ArtifactVersionEntity artifact,
            boolean included,
            boolean approved
    ) {
        Map<String, Object> item = new LinkedHashMap<>();

        item.put("artifactType", requirement.artifactType());
        item.put("title", requirement.title());
        item.put("required", requirement.required());
        item.put("exists", artifact != null);
        item.put("included", included);
        item.put("approved", approved);

        if (artifact != null) {
            item.put("artifactId", artifact.getId() == null ? null : artifact.getId().toString());
            item.put("versionNo", artifact.getVersionNo());
            item.put("status", safeText(artifact.getStatus()));
            item.put("checksum", safeText(artifact.getChecksum()));
            item.put("title", firstNonBlank(artifact.getTitle(), requirement.title()));
            item.put("contentPreview", excerpt(resolveArtifactMarkdown(artifact), 500));
            item.put("createdAt", artifact.getCreatedAt() == null ? null : artifact.getCreatedAt().toString());
            item.put("updatedAt", artifact.getUpdatedAt() == null ? null : artifact.getUpdatedAt().toString());
        }

        return item;
    }

    private Map<String, Object> buildContent(
            ProjectAccessService.ProjectAccess access,
            ProjectModuleEntity module,
            LocalDateTime generatedAt,
            String username,
            ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline,
            List<Map<String, Object>> sourceArtifacts,
            List<String> blockers,
            List<String> warnings,
            GenerateDevHandoffPackageRequest request,
            String markdown
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> targetNode = new LinkedHashMap<>();
        Map<String, Object> treeBaseline = new LinkedHashMap<>();
        Map<String, Object> readiness = new LinkedHashMap<>();

        metadata.put("schemaVersion", "1.0");
        metadata.put("artifactType", DEV_HANDOFF_PACKAGE);
        metadata.put("generatedAt", generatedAt.toString());
        metadata.put("generatedBy", username);
        metadata.put("projectId", access.projectId().toString());
        metadata.put("projectCode", access.projectCode());
        metadata.put("workspaceId", access.workspaceId().toString());
        metadata.put("generationMode", "DETERMINISTIC_REQUIREMENT_ASSET_SNAPSHOT");
        metadata.put("includeUnapproved", request == null || request.includeUnapproved() == null
                || Boolean.TRUE.equals(request.includeUnapproved()));
        metadata.put("note", safeText(request == null ? null : request.note()));

        targetNode.put("nodeId", module.getId() == null ? null : module.getId().toString());
        targetNode.put("nodeName", safeText(module.getModuleName()));
        targetNode.put("nodeKey", safeText(module.getModuleKey()));
        targetNode.put("nodeType", safeText(module.getModuleType()));
        targetNode.put("modulePath", safeText(module.getModulePath()));
        targetNode.put("parentId", module.getParentId() == null ? null : module.getParentId().toString());

        treeBaseline.put("hash", moduleTreeBaseline.hash());
        treeBaseline.put("nodeCount", moduleTreeBaseline.nodeCount());
        treeBaseline.put("summary", moduleTreeBaseline.summary());

        readiness.put("ready", blockers.isEmpty());
        readiness.put("blockers", blockers);
        readiness.put("warnings", warnings);

        content.put("metadata", metadata);
        content.put("targetNode", targetNode);
        content.put("moduleTreeBaseline", treeBaseline);
        content.put("sourceArtifacts", sourceArtifacts);
        content.put("readiness", readiness);
        content.put("contentMarkdown", markdown);

        return content;
    }

    private String buildMarkdown(
            ProjectAccessService.ProjectAccess access,
            ProjectModuleEntity module,
            LocalDateTime generatedAt,
            String username,
            ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline,
            List<Map<String, Object>> sourceArtifacts,
            List<ArtifactSection> artifactSections,
            List<String> blockers,
            List<String> warnings,
            String note
    ) {
        StringBuilder markdown = new StringBuilder();
        String generatedAtText = generatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        markdown.append("# 开发交接包：")
                .append(safeTitle(module.getModuleName()))
                .append("\n\n");

        markdown.append("> 本交接包由 CodeMind Requirement Agent 汇总当前节点需求资产生成，用于后续人类开发或代码生成 Agent。\n\n");

        markdown.append("## 1. 交付范围\n\n")
                .append("- 项目：`").append(access.projectCode()).append("`\n")
                .append("- 节点：").append(safeTitle(module.getModuleName())).append("\n")
                .append("- 节点类型：`").append(safeText(module.getModuleType())).append("`\n")
                .append("- 节点路径：").append(firstNonBlank(module.getModulePath(), "未记录")).append("\n")
                .append("- 生成时间：").append(generatedAtText).append("\n")
                .append("- 生成者：").append(firstNonBlank(username, "未知")).append("\n")
                .append("- 模块树基线：").append(moduleTreeBaseline.summary()).append("\n\n");

        if (note != null && !note.isBlank()) {
            markdown.append("### 生成备注\n\n")
                    .append(note.trim())
                    .append("\n\n");
        }

        markdown.append("## 2. 交付完整性检查\n\n");

        if (blockers.isEmpty()) {
            markdown.append("- 当前没有必需资产缺失阻断项。\n");
        } else {
            for (String blocker : blockers) {
                markdown.append("- [阻断] ").append(blocker).append("\n");
            }
        }

        for (String warning : warnings) {
            markdown.append("- [提醒] ").append(warning).append("\n");
        }

        markdown.append("\n## 3. 来源资产清单\n\n")
                .append("| 资产 | 必需 | 存在 | 纳入交接包 | 审批状态 | 版本 | 说明 |\n")
                .append("|---|---:|---:|---:|---|---:|---|\n");

        for (Map<String, Object> item : sourceArtifacts) {
            markdown.append("| ")
                    .append(safeText(item.get("title")))
                    .append(" (`")
                    .append(safeText(item.get("artifactType")))
                    .append("`) | ")
                    .append(Boolean.TRUE.equals(item.get("required")) ? "是" : "否")
                    .append(" | ")
                    .append(Boolean.TRUE.equals(item.get("exists")) ? "是" : "否")
                    .append(" | ")
                    .append(Boolean.TRUE.equals(item.get("included")) ? "是" : "否")
                    .append(" | ")
                    .append(firstNonBlank(safeText(item.get("status")), "-") )
                    .append(" | ")
                    .append(item.get("versionNo") == null ? "-" : item.get("versionNo"))
                    .append(" | ")
                    .append(Boolean.TRUE.equals(item.get("approved")) ? "已批准" : "待复核")
                    .append(" |\n");
        }

        markdown.append("\n## 4. 开发执行输入\n\n")
                .append("开发前请优先阅读以下资产。若某资产状态不是 APPROVED，应先由产品/业务负责人确认后再进入编码。\n\n");

        artifactSections.stream()
                .sorted(Comparator.comparing(section -> orderOf(section.requirement().artifactType())))
                .forEach(section -> appendArtifactSection(markdown, section));

        markdown.append("## 5. 开发交接清单\n\n")
                .append("- [ ] 确认本交接包对应的模块树基线未变化。\n")
                .append("- [ ] 确认 PAGE_SPEC / UI_CONTRACT 与页面实现范围一致。\n")
                .append("- [ ] 确认 API_CONTRACT 与后端接口路由、请求、响应、错误码一致。\n")
                .append("- [ ] 确认 DATA_MODEL 与数据库/实体字段一致。\n")
                .append("- [ ] 确认 TEST_SPEC 已覆盖主流程、异常流程和权限边界。\n")
                .append("- [ ] 编码前锁定本交接包版本，避免基于过期需求资产生成代码。\n\n");

        markdown.append("## 6. 后续建议\n\n")
                .append("1. 本交接包进入评审后，建议审批为 APPROVED 再交给开发 Agent。\n")
                .append("2. 若任一上游资产重新生成或保存新版本，应重新生成 DEV_HANDOFF_PACKAGE。\n")
                .append("3. 代码生成阶段应读取本交接包的 sourceArtifacts 和 moduleTreeBaseline 作为输入边界。\n");

        return markdown.toString();
    }

    private void appendArtifactSection(
            StringBuilder markdown,
            ArtifactSection section
    ) {
        ArtifactRequirement requirement = section.requirement();
        ArtifactVersionEntity artifact = section.artifact();
        String content = resolveArtifactMarkdown(artifact);

        markdown.append("### ")
                .append(requirement.title())
                .append(" (`")
                .append(requirement.artifactType())
                .append("` / V")
                .append(artifact.getVersionNo())
                .append(" / ")
                .append(firstNonBlank(artifact.getStatus(), "未知状态"))
                .append(")\n\n");

        if (!section.approved()) {
            markdown.append("> 注意：该资产尚未 APPROVED，进入开发前需要人工复核。\n\n");
        }

        markdown.append(content.isBlank() ? "暂无可读内容。" : content.trim())
                .append("\n\n");
    }

    private String resolveArtifactMarkdown(
            ArtifactVersionEntity artifact
    ) {
        if (artifact == null) {
            return "";
        }

        if (artifact.getContentMarkdown() != null && !artifact.getContentMarkdown().isBlank()) {
            return artifact.getContentMarkdown();
        }

        if (artifact.getContent() == null || artifact.getContent().isEmpty()) {
            return "";
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(artifact.getContent());
        } catch (JsonProcessingException ex) {
            return String.valueOf(artifact.getContent());
        }
    }

    private String buildResponseMessage(
            ArtifactVersionEntity artifact,
            List<String> blockers,
            List<String> warnings
    ) {
        if (!blockers.isEmpty()) {
            return "已生成开发交接包 V" + artifact.getVersionNo()
                    + "，但存在 " + blockers.size()
                    + " 个交付阻断项，请修复后重新生成或在评审中确认。";
        }

        if (!warnings.isEmpty()) {
            return "已生成开发交接包 V" + artifact.getVersionNo()
                    + "，存在 " + warnings.size()
                    + " 个待复核提醒。";
        }

        return "已生成开发交接包 V" + artifact.getVersionNo()
                + "，可进入评审或后续开发 Agent 输入阶段。";
    }

    private String normalizeNodeType(
            String value
    ) {
        String normalized = safeText(value).toUpperCase(Locale.ROOT);

        if (List.of("PAGE", "PAGE_MODULE", "SCREEN", "VIEW").contains(normalized)) {
            return "PAGE";
        }

        if (List.of("FEATURE", "FUNCTION", "ACTION", "OPERATION").contains(normalized)) {
            return "FEATURE";
        }

        if (List.of("SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE").contains(normalized)) {
            return "SUBMODULE";
        }

        return "MODULE";
    }

    private int orderOf(
            String artifactType
    ) {
        return switch (artifactType) {
            case "MODULE_PRD" -> 10;
            case "BUSINESS_FLOW" -> 20;
            case "PAGE_SPEC" -> 30;
            case "DATA_MODEL" -> 40;
            case "API_CONTRACT" -> 50;
            case "TEST_SPEC" -> 60;
            case "UI_CONTRACT" -> 70;
            default -> 100;
        };
    }

    private String excerpt(
            String value,
            int maxLength
    ) {
        String text = safeText(value).replace('\n', ' ').replace('\r', ' ').trim();

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    private String safeText(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeTitle(
            String value
    ) {
        String text = safeText(value);
        return text.isBlank() ? "未命名节点" : text;
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        if (second != null && !second.isBlank()) {
            return second;
        }

        return "";
    }

    private String sha256Hex(
            String value
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    safeText(value).getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder result = new StringBuilder(bytes.length * 2);

            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "当前 JDK 不支持 SHA-256。"
            );
        }
    }

    private record ArtifactRequirement(
            String artifactType,
            String title,
            boolean required
    ) {
    }

    private record ArtifactSection(
            ArtifactRequirement requirement,
            ArtifactVersionEntity artifact,
            boolean approved
    ) {
    }
}
