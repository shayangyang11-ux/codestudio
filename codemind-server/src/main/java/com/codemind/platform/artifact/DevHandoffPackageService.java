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
                warnings.add("建议资产未生成：" + requirement.title() + "（" + requirement.artifactType() + "）。");
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

        Map<String, Object> devBlueprint = buildDevBlueprint(
                module,
                sourceArtifacts,
                blockers,
                warnings
        );

        Map<String, Object> readiness = buildReadiness(
                requirements,
                blockers,
                warnings
        );

        String markdown = buildMarkdown(
                access,
                module,
                generatedAt,
                username,
                moduleTreeBaseline,
                sourceArtifacts,
                artifactSections,
                devBlueprint,
                readiness,
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
                devBlueprint,
                readiness,
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
            case "DOMAIN" -> List.of(
                    optional("MODULE_PRD", "业务域概览"),
                    optional("BUSINESS_FLOW", "跨模块业务流程"),
                    optional("PAGE_MAP", "页面地图"),
                    optional("DATA_MODEL", "业务域数据对象摘要"),
                    optional("TEST_SPEC", "业务域验收摘要")
            );
            case "PAGE" -> List.of(
                    required("PAGE_SPEC", "页面规范"),
                    required("UI_CONTRACT", "UI 契约"),
                    optional("API_CONTRACT", "页面接口摘要"),
                    optional("DATA_MODEL", "页面相关数据摘要"),
                    optional("TEST_SPEC", "页面测试摘要"),
                    optional("MODULE_PRD", "页面需求摘要"),
                    optional("BUSINESS_FLOW", "页面流程摘要")
            );
            case "FEATURE" -> List.of(
                    optional("MODULE_PRD", "功能点说明"),
                    optional("BUSINESS_FLOW", "功能点流程"),
                    optional("API_CONTRACT", "功能点接口"),
                    optional("TEST_SPEC", "功能点测试规格")
            );
            case "SUBMODULE" -> List.of(
                    optional("MODULE_PRD", "场景需求说明"),
                    optional("BUSINESS_FLOW", "场景业务流程"),
                    optional("PAGE_SPEC", "场景页面清单"),
                    optional("API_CONTRACT", "场景接口清单"),
                    optional("DATA_MODEL", "场景数据模型"),
                    optional("TEST_SPEC", "场景测试规格")
            );
            default -> List.of(
                    required("MODULE_PRD", "模块 PRD"),
                    required("BUSINESS_FLOW", "业务流程"),
                    required("PAGE_SPEC", "页面地图/页面规范"),
                    required("DATA_MODEL", "数据模型"),
                    required("API_CONTRACT", "接口规划"),
                    required("TEST_SPEC", "测试规格"),
                    optional("UI_CONTRACT", "页面 UI 契约")
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
        item.put("usedFor", usedFor(requirement.artifactType()));
        item.put("importance", requirement.required() ? "CORE" : "RECOMMENDED");

        if (artifact != null) {
            String markdown = resolveArtifactMarkdown(artifact);
            item.put("artifactId", artifact.getId() == null ? null : artifact.getId().toString());
            item.put("versionNo", artifact.getVersionNo());
            item.put("status", safeText(artifact.getStatus()));
            item.put("checksum", safeText(artifact.getChecksum()));
            item.put("title", firstNonBlank(artifact.getTitle(), requirement.title()));
            item.put("contentPreview", excerpt(markdown, 700));
            item.put("contentChars", markdown.length());
            item.put("createdAt", artifact.getCreatedAt() == null ? null : artifact.getCreatedAt().toString());
            item.put("updatedAt", artifact.getUpdatedAt() == null ? null : artifact.getUpdatedAt().toString());
        }

        return item;
    }

    private Map<String, Object> buildDevBlueprint(
            ProjectModuleEntity module,
            List<Map<String, Object>> sourceArtifacts,
            List<String> blockers,
            List<String> warnings
    ) {
        String nodeType = normalizeNodeType(module == null ? null : module.getModuleType());
        String nodeKey = safeNodeKey(module);
        String nodeName = safeTitle(module == null ? null : module.getModuleName());

        Map<String, Object> blueprint = new LinkedHashMap<>();
        Map<String, Object> targetNode = new LinkedHashMap<>();
        Map<String, Object> stackPolicy = new LinkedHashMap<>();
        List<Map<String, Object>> sourceReferences = new ArrayList<>();

        targetNode.put("nodeId", module == null || module.getId() == null ? null : module.getId().toString());
        targetNode.put("nodeKey", nodeKey);
        targetNode.put("nodeName", nodeName);
        targetNode.put("nodeType", nodeType);
        targetNode.put("modulePath", safeText(module == null ? null : module.getModulePath()));
        targetNode.put("parentId", module == null || module.getParentId() == null ? null : module.getParentId().toString());

        stackPolicy.put("languageNeutral", true);
        stackPolicy.put("targetStack", null);
        stackPolicy.put("supportedStackModes", List.of(
                "NODEJS_FULLSTACK",
                "JAVA_SPRING_BOOT",
                "PYTHON_FASTAPI"
        ));
        stackPolicy.put("decisionOwner", "Dev Agent");
        stackPolicy.put("note", "需求交接包不绑定具体技术栈；后续 Dev Agent 根据 targetStack 转换为具体项目结构、框架、数据库和测试实现。");

        for (Map<String, Object> item : sourceArtifacts) {
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("sourceKey", "src_" + safeText(item.get("artifactType")).toLowerCase(Locale.ROOT));
            reference.put("artifactType", item.get("artifactType"));
            reference.put("title", item.get("title"));
            reference.put("versionNo", item.get("versionNo"));
            reference.put("status", item.get("status"));
            reference.put("included", item.get("included"));
            reference.put("required", item.get("required"));
            reference.put("usedFor", item.get("usedFor"));
            reference.put("contentPreview", item.get("contentPreview"));
            sourceReferences.add(reference);
        }

        blueprint.put("blueprintVersion", "2.0");
        blueprint.put("blueprintKey", nodeKey + "_dev_blueprint");
        blueprint.put("blueprintName", nodeName + "语言无关开发蓝图");
        blueprint.put("scopeType", nodeType);
        blueprint.put("targetNode", targetNode);
        blueprint.put("businessGoal", "基于当前需求资产实现 " + nodeName + " 的核心业务能力，并允许后续转换为 Node.js、Java、Python 等不同技术栈实现。");
        blueprint.put("stackPolicy", stackPolicy);
        blueprint.put("generationBoundaries", generationBoundaries(nodeType));
        blueprint.put("sourceReferences", sourceReferences);
        blueprint.put("capabilityMap", buildCapabilityMap(nodeType, sourceArtifacts));
        blueprint.put("readiness", Map.of(
                "readyForDevAgent", blockers.isEmpty(),
                "blockerCount", blockers.size(),
                "warningCount", warnings.size()
        ));

        return blueprint;
    }

    private Map<String, Object> buildCapabilityMap(
            String nodeType,
            List<Map<String, Object>> sourceArtifacts
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String> artifactTypes = sourceArtifacts.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("included")))
                .map(item -> safeText(item.get("artifactType")))
                .filter(text -> !text.isBlank())
                .toList();

        map.put("pages", artifactTypes.contains("PAGE_SPEC") || artifactTypes.contains("UI_CONTRACT")
                ? List.of("根据 PAGE_SPEC / UI_CONTRACT 生成页面结构、状态、交互和视觉一致性约束。")
                : List.of());
        map.put("features", List.of("根据当前节点需求说明和业务流程生成核心功能能力。"));
        map.put("apis", artifactTypes.contains("API_CONTRACT")
                ? List.of("根据 API_CONTRACT 生成接口能力、请求响应、错误场景和权限约束。")
                : List.of());
        map.put("dataEntities", artifactTypes.contains("DATA_MODEL")
                ? List.of("根据 DATA_MODEL 生成数据实体语义、字段约束、关系和生命周期。")
                : List.of());
        map.put("acceptanceCriteria", artifactTypes.contains("TEST_SPEC")
                ? List.of("根据 TEST_SPEC 生成验收场景、异常场景、权限场景和边界场景。")
                : List.of("至少保留主流程验收和人工确认项。"));
        map.put("scopeHint", nodeType);

        return map;
    }

    private List<String> generationBoundaries(
            String nodeType
    ) {
        List<String> result = new ArrayList<>();
        result.add("不得将需求资产中的预览场景数据作为最终业务实现。");
        result.add("不得在需求交接包中写死框架、ORM、数据库方言、测试框架或目录结构。");
        result.add("代码生成阶段必须遵守 sourceReferences 中列出的资产版本和状态。");

        if ("PAGE".equals(nodeType)) {
            result.add("页面实现必须以 PAGE_SPEC 和 UI_CONTRACT 为主，不能重新设计页面结构和交互。 ");
        } else if ("MODULE".equals(nodeType) || "SUBMODULE".equals(nodeType)) {
            result.add("模块/场景级实现应先生成开发计划，再分解到页面、接口、数据和测试。 ");
        }

        return result;
    }

    private Map<String, Object> buildReadiness(
            List<ArtifactRequirement> requirements,
            List<String> blockers,
            List<String> warnings
    ) {
        List<String> required = requirements.stream()
                .filter(ArtifactRequirement::required)
                .map(ArtifactRequirement::artifactType)
                .toList();

        List<String> recommended = requirements.stream()
                .filter(requirement -> !requirement.required())
                .map(ArtifactRequirement::artifactType)
                .toList();

        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("readyForDevAgent", blockers.isEmpty());
        readiness.put("readinessLevel", blockers.isEmpty() ? (warnings.isEmpty() ? "READY" : "WARNING") : "BLOCKED");
        readiness.put("blockers", blockers);
        readiness.put("warnings", warnings);
        readiness.put("requiredSourceArtifacts", required);
        readiness.put("recommendedSourceArtifacts", recommended);
        readiness.put("policy", "READY 表示可以进入 Dev Agent；WARNING 表示可以进入但需人工关注；BLOCKED 表示不建议进入代码生成。 ");

        return readiness;
    }

    private Map<String, Object> buildContent(
            ProjectAccessService.ProjectAccess access,
            ProjectModuleEntity module,
            LocalDateTime generatedAt,
            String username,
            ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline,
            List<Map<String, Object>> sourceArtifacts,
            Map<String, Object> devBlueprint,
            Map<String, Object> readiness,
            List<String> blockers,
            List<String> warnings,
            GenerateDevHandoffPackageRequest request,
            String markdown
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> treeBaseline = new LinkedHashMap<>();

        metadata.put("schemaVersion", "2.0");
        metadata.put("artifactType", DEV_HANDOFF_PACKAGE);
        metadata.put("packageRole", "LANGUAGE_NEUTRAL_DEV_BLUEPRINT");
        metadata.put("generatedAt", generatedAt.toString());
        metadata.put("generatedBy", username);
        metadata.put("projectId", access.projectId().toString());
        metadata.put("projectCode", access.projectCode());
        metadata.put("workspaceId", access.workspaceId().toString());
        metadata.put("generationMode", "DETERMINISTIC_DEV_BLUEPRINT_SNAPSHOT");
        metadata.put("includeUnapproved", request == null || request.includeUnapproved() == null
                || Boolean.TRUE.equals(request.includeUnapproved()));
        metadata.put("note", safeText(request == null ? null : request.note()));

        treeBaseline.put("hash", moduleTreeBaseline.hash());
        treeBaseline.put("nodeCount", moduleTreeBaseline.nodeCount());
        treeBaseline.put("summary", moduleTreeBaseline.summary());

        content.put("metadata", metadata);
        content.put("targetNode", devBlueprint.get("targetNode"));
        content.put("moduleTreeBaseline", treeBaseline);
        content.put("devBlueprint", devBlueprint);
        content.put("sourceReferenceMatrix", devBlueprint.get("sourceReferences"));
        content.put("sourceArtifacts", sourceArtifacts);
        content.put("readiness", readiness);
        content.put("blockers", blockers);
        content.put("warnings", warnings);
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
            Map<String, Object> devBlueprint,
            Map<String, Object> readiness,
            List<String> blockers,
            List<String> warnings,
            String note
    ) {
        StringBuilder markdown = new StringBuilder();
        String generatedAtText = generatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String nodeType = normalizeNodeType(module == null ? null : module.getModuleType());

        markdown.append("# 开发交接包：")
                .append(safeTitle(module.getModuleName()))
                .append("\n\n");

        markdown.append("> 本交接包是语言无关 Dev Blueprint，用于后续 Dev Agent 按目标技术栈生成真实系统。它不是具体技术栈代码方案，也不是把预览数据作为最终业务实现。\n\n");

        markdown.append("## 1. 交付范围\n\n")
                .append("- 项目：`").append(access.projectCode()).append("`\n")
                .append("- 节点：").append(safeTitle(module.getModuleName())).append("\n")
                .append("- 节点类型：`").append(nodeType).append("`\n")
                .append("- 节点路径：").append(firstNonBlank(module.getModulePath(), "未记录")).append("\n")
                .append("- 生成时间：").append(generatedAtText).append("\n")
                .append("- 生成者：").append(firstNonBlank(username, "未知")).append("\n")
                .append("- 模块树基线：").append(moduleTreeBaseline.summary()).append("\n")
                .append("- 技术栈策略：语言无关，具体 Node.js / Java / Python 等由 Dev Agent targetStack 决定。\n\n");

        if (note != null && !note.isBlank()) {
            markdown.append("### 生成备注\n\n")
                    .append(note.trim())
                    .append("\n\n");
        }

        markdown.append("## 2. Dev Agent 就绪度\n\n")
                .append("- 就绪状态：").append(Boolean.TRUE.equals(readiness.get("readyForDevAgent")) ? "可进入 Dev Agent" : "不建议进入 Dev Agent").append("\n")
                .append("- 就绪等级：`").append(safeText(readiness.get("readinessLevel"))).append("`\n")
                .append("- 必需来源资产：").append(joinList((List<?>) readiness.get("requiredSourceArtifacts"))).append("\n")
                .append("- 建议来源资产：").append(joinList((List<?>) readiness.get("recommendedSourceArtifacts"))).append("\n\n");

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

        markdown.append("\n## 3. 语言无关开发蓝图\n\n")
                .append("- 蓝图名称：").append(safeText(devBlueprint.get("blueprintName"))).append("\n")
                .append("- 开发范围：`").append(safeText(devBlueprint.get("scopeType"))).append("`\n")
                .append("- 业务目标：").append(safeText(devBlueprint.get("businessGoal"))).append("\n\n");

        Object stackPolicy = devBlueprint.get("stackPolicy");
        if (stackPolicy instanceof Map<?, ?> stackPolicyMap) {
            markdown.append("### 技术栈策略\n\n")
                    .append("- 是否语言无关：").append(Boolean.TRUE.equals(stackPolicyMap.get("languageNeutral")) ? "是" : "否").append("\n")
                    .append("- 支持目标栈：").append(joinList((List<?>) stackPolicyMap.get("supportedStackModes"))).append("\n")
                    .append("- 决策方：").append(safeText(stackPolicyMap.get("decisionOwner"))).append("\n")
                    .append("- 说明：").append(safeText(stackPolicyMap.get("note"))).append("\n\n");
        }

        markdown.append("### 生成边界\n\n");
        Object boundaries = devBlueprint.get("generationBoundaries");
        if (boundaries instanceof List<?> boundaryList && !boundaryList.isEmpty()) {
            for (Object item : boundaryList) {
                markdown.append("- ").append(safeText(item)).append("\n");
            }
        } else {
            markdown.append("- 不得越过当前节点范围生成无关功能。\n");
        }

        markdown.append("\n## 4. 来源资产引用矩阵\n\n")
                .append("| 资产 | 必需 | 纳入 | 审批状态 | 版本 | 用途 |\n")
                .append("|---|---:|---:|---|---:|---|\n");

        for (Map<String, Object> item : sourceArtifacts) {
            markdown.append("| ")
                    .append(safeText(item.get("title")))
                    .append(" (`")
                    .append(safeText(item.get("artifactType")))
                    .append("`) | ")
                    .append(Boolean.TRUE.equals(item.get("required")) ? "是" : "否")
                    .append(" | ")
                    .append(Boolean.TRUE.equals(item.get("included")) ? "是" : "否")
                    .append(" | ")
                    .append(firstNonBlank(safeText(item.get("status")), "-") )
                    .append(" | ")
                    .append(item.get("versionNo") == null ? "-" : item.get("versionNo"))
                    .append(" | ")
                    .append(joinList((List<?>) item.get("usedFor")))
                    .append(" |\n");
        }

        markdown.append("\n## 5. 来源资产摘要\n\n")
                .append("以下只展示摘要，避免把上游资产全文拼接到交接包中。代码生成阶段应读取结构化 sourceReferenceMatrix 和被锁定的资产版本。\n\n");

        artifactSections.stream()
                .sorted(Comparator.comparing(section -> orderOf(section.requirement().artifactType())))
                .forEach(section -> appendArtifactSummary(markdown, section));

        markdown.append("## 6. 进入 Dev Agent 前检查\n\n")
                .append("- [ ] 确认本交接包对应的模块树基线未变化。\n")
                .append("- [ ] 确认来源资产版本和状态满足开发要求。\n")
                .append("- [ ] 确认 PAGE_SPEC / UI_CONTRACT 与页面实现范围一致。\n")
                .append("- [ ] 确认 API_CONTRACT、DATA_MODEL、TEST_SPEC 已覆盖当前节点最小开发闭环。\n")
                .append("- [ ] 选择 targetStack 后，由 Dev Agent 将语言无关蓝图转换为具体技术栈项目。\n\n");

        markdown.append("## 7. 后续建议\n\n")
                .append("1. 本交接包建议审批为 APPROVED 后再交给 Dev Agent。\n")
                .append("2. 若任一上游资产重新生成或保存新版本，应重新生成 DEV_HANDOFF_PACKAGE。\n")
                .append("3. Node.js 分支应读取 devBlueprint，而不是重新解析冗长需求文档全文。\n");

        return markdown.toString();
    }

    private void appendArtifactSummary(
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

        markdown.append(excerpt(content, 1000).isBlank() ? "暂无可读摘要。" : excerpt(content, 1000))
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
                    + " 个 Dev Agent 阻断项，请修复后重新生成或在评审中确认。";
        }

        if (!warnings.isEmpty()) {
            return "已生成开发交接包 V" + artifact.getVersionNo()
                    + "，存在 " + warnings.size()
                    + " 个待复核提醒。";
        }

        return "已生成开发交接包 V" + artifact.getVersionNo()
                + "，可进入评审或后续 Dev Agent 输入阶段。";
    }

    private String normalizeNodeType(
            String value
    ) {
        String normalized = safeText(value).toUpperCase(Locale.ROOT);

        if (List.of("DOMAIN", "BUSINESS_DOMAIN").contains(normalized)) {
            return "DOMAIN";
        }

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
            case "UI_CONTRACT" -> 40;
            case "API_CONTRACT" -> 50;
            case "DATA_MODEL" -> 60;
            case "TEST_SPEC" -> 70;
            default -> 100;
        };
    }

    private List<String> usedFor(
            String artifactType
    ) {
        return switch (safeText(artifactType)) {
            case "MODULE_PRD" -> List.of("业务目标", "功能范围", "业务规则");
            case "BUSINESS_FLOW" -> List.of("流程顺序", "分支异常", "状态变化");
            case "PAGE_SPEC" -> List.of("页面能力", "字段操作", "页面状态");
            case "UI_CONTRACT" -> List.of("页面布局", "控件层级", "交互反馈");
            case "API_CONTRACT" -> List.of("接口能力", "请求响应", "错误场景");
            case "DATA_MODEL" -> List.of("实体语义", "字段约束", "数据关系");
            case "TEST_SPEC" -> List.of("验收场景", "边界条件", "测试数据");
            default -> List.of("开发输入");
        };
    }

    private String excerpt(
            String value,
            int maxLength
    ) {
        String text = safeText(value).replace('\r', ' ').trim();

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    private String joinList(
            List<?> values
    ) {
        if (values == null || values.isEmpty()) {
            return "无";
        }

        return values.stream()
                .map(this::safeText)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
    }

    private String safeNodeKey(
            ProjectModuleEntity module
    ) {
        String key = safeText(module == null ? null : module.getModuleKey());
        if (!key.isBlank()) {
            return key;
        }

        String name = safeText(module == null ? null : module.getModuleName());
        if (!name.isBlank()) {
            return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "_");
        }

        return "current_node";
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


