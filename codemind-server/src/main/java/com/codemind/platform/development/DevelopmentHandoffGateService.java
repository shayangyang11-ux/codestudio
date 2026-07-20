package com.codemind.platform.development;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.uicontract.ConfirmedUiContractSnapshotResponse;
import com.codemind.platform.uicontract.ConfirmedUiContractSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DevelopmentHandoffGateService {

    private static final String UI_CONTRACT = "UI_CONTRACT";

    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ConfirmedUiContractSnapshotService confirmedUiContractSnapshotService;

    public DevelopmentHandoffGateService(
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            ConfirmedUiContractSnapshotService confirmedUiContractSnapshotService
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.confirmedUiContractSnapshotService = confirmedUiContractSnapshotService;
    }

    @Transactional(readOnly = true)
    public DevelopmentHandoffReadinessResponse getReadiness(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        BuildContext context = buildContext(
                workspaceId,
                projectCode,
                moduleId
        );

        return buildReadiness(context);
    }

    @Transactional(readOnly = true)
    public DevelopmentHandoffContextResponse getContext(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        BuildContext context = buildContext(
                workspaceId,
                projectCode,
                moduleId
        );

        DevelopmentHandoffReadinessResponse readiness = buildReadiness(context);
        List<DevelopmentHandoffArtifactPayload> artifacts = new ArrayList<>();

        for (ArtifactRequirement requirement : context.requirements()) {
            ArtifactVersionEntity artifact = context.artifactByType()
                    .get(requirement.artifactType());

            artifacts.add(
                    new DevelopmentHandoffArtifactPayload(
                            requirement.artifactType(),
                            requirement.title(),
                            requirement.required(),
                            artifact == null ? null : artifact.getId(),
                            artifact == null ? null : artifact.getVersionNo(),
                            artifact == null ? "MISSING" : safeText(artifact.getStatus()),
                            artifact == null ? null : artifact.getContentMarkdown(),
                            artifact == null ? null : artifact.getContent()
                    )
            );
        }

        Map<String, Object> confirmedUiContract = context.uiContractSnapshot()
                .map(ConfirmedUiContractSnapshotResponse::uiContract)
                .orElse(null);

        Map<String, Object> themeProfileSnapshot = context.uiContractSnapshot()
                .map(ConfirmedUiContractSnapshotResponse::themeProfileSnapshot)
                .orElse(null);

        return new DevelopmentHandoffContextResponse(
                context.project().getId(),
                context.module().getId(),
                context.module().getModuleKey(),
                context.module().getModuleName(),
                context.module().getModuleType(),
                context.nodeType(),
                LocalDateTime.now(),
                readiness,
                artifacts,
                confirmedUiContract,
                themeProfileSnapshot
        );
    }

    private BuildContext buildContext(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        Map<String, ArtifactVersionEntity> artifactByType = new LinkedHashMap<>();

        for (ArtifactVersionEntity artifact : artifactVersionRepository.findByProjectIdAndModuleIdAndCurrentTrue(
                project.getId(),
                module.getId()
        )) {
            artifactByType.put(
                    artifact.getArtifactType(),
                    artifact
            );
        }

        String nodeType = resolveNodeType(module);
        List<ArtifactRequirement> requirements = buildArtifactRequirements(nodeType);

        Optional<ConfirmedUiContractSnapshotResponse> uiContractSnapshot =
                "PAGE".equals(nodeType)
                        ? confirmedUiContractSnapshotService.getCurrent(
                                workspaceId,
                                projectCode,
                                module.getId()
                        )
                        : Optional.empty();

        return new BuildContext(
                project,
                module,
                nodeType,
                requirements,
                artifactByType,
                uiContractSnapshot
        );
    }

    private DevelopmentHandoffReadinessResponse buildReadiness(
            BuildContext context
    ) {
        List<DevelopmentHandoffAssetResponse> assets = new ArrayList<>();
        List<DevelopmentHandoffIssueResponse> blockers = new ArrayList<>();
        List<DevelopmentHandoffIssueResponse> warnings = new ArrayList<>();
        List<DevelopmentHandoffIssueResponse> passes = new ArrayList<>();
        List<String> requiredArtifactTypes = new ArrayList<>();
        List<String> recommendedArtifactTypes = new ArrayList<>();

        for (ArtifactRequirement requirement : context.requirements()) {
            if (requirement.required()) {
                requiredArtifactTypes.add(requirement.artifactType());
            } else {
                recommendedArtifactTypes.add(requirement.artifactType());
            }

            ArtifactVersionEntity artifact = context.artifactByType()
                    .get(requirement.artifactType());

            ArtifactReadiness artifactReadiness = evaluateArtifact(
                    requirement,
                    artifact
            );

            assets.add(artifactReadiness.asset());

            if (artifactReadiness.issue() != null) {
                if ("blocker".equals(artifactReadiness.issue().level())) {
                    blockers.add(artifactReadiness.issue());
                } else {
                    warnings.add(artifactReadiness.issue());
                }
            } else {
                passes.add(
                        new DevelopmentHandoffIssueResponse(
                                "pass-" + requirement.artifactType(),
                                "pass",
                                requirement.title() + "可用于开发交接",
                                requirement.title() + "存在有效内容，可纳入 Development Agent 开发上下文。",
                                requirement.artifactType()
                        )
                );
            }
        }

        DevelopmentUiContractSnapshotSummary snapshotSummary =
                buildUiContractSnapshotSummary(context);

        if ("PAGE".equals(context.nodeType())) {
            ArtifactVersionEntity uiContract = context.artifactByType().get(UI_CONTRACT);

            if (uiContract != null && uiContract.getId() != null) {
                if (!snapshotSummary.exists()) {
                    blockers.add(
                            new DevelopmentHandoffIssueResponse(
                                    "ui-contract-snapshot-missing",
                                    "blocker",
                                    "页面交互原型尚未用户确认",
                                    "PAGE 页面进入 Development Agent 前，必须先在交互预览中确认当前 UI_CONTRACT 页面效果。",
                                    UI_CONTRACT
                            )
                    );
                } else if (!snapshotSummary.matchesCurrentArtifact()) {
                    blockers.add(
                            new DevelopmentHandoffIssueResponse(
                                    "ui-contract-snapshot-stale",
                                    "blocker",
                                    "页面交互原型确认快照已滞后",
                                    "当前 UI_CONTRACT 版本与用户确认快照不一致。请重新预览并确认页面效果。",
                                    UI_CONTRACT
                            )
                    );
                } else {
                    passes.add(
                            new DevelopmentHandoffIssueResponse(
                                    "ui-contract-snapshot-pass",
                                    "pass",
                                    "页面交互原型已确认",
                                    "已保存用户确认快照，Development Agent 可按该版本页面蓝图生成真实代码。",
                                    UI_CONTRACT
                            )
                    );
                }
            }
        }

        int score = Math.max(
                0,
                100 - blockers.size() * 20 - warnings.size() * 5
        );

        String readinessLevel =
                blockers.isEmpty()
                        ? warnings.isEmpty() ? "READY" : "WARNING"
                        : "BLOCKED";

        return new DevelopmentHandoffReadinessResponse(
                context.project().getId(),
                context.module().getId(),
                context.module().getModuleKey(),
                context.module().getModuleName(),
                context.module().getModuleType(),
                context.nodeType(),
                nodeTypeLabel(context.nodeType()),
                blockers.isEmpty(),
                readinessLevel,
                readinessText(readinessLevel),
                readinessDescription(readinessLevel, context.nodeType()),
                score,
                LocalDateTime.now(),
                requiredArtifactTypes,
                recommendedArtifactTypes,
                assets,
                blockers,
                warnings,
                passes,
                snapshotSummary
        );
    }

    private ArtifactReadiness evaluateArtifact(
            ArtifactRequirement requirement,
            ArtifactVersionEntity artifact
    ) {
        boolean exists = artifact != null;
        boolean contentReady = hasMeaningfulArtifactContent(artifact);
        String status = artifact == null ? "MISSING" : safeText(artifact.getStatus());

        String level = "pass";
        String statusText = "已就绪";
        boolean readyForDevelopment = true;
        DevelopmentHandoffIssueResponse issue = null;

        if (!exists) {
            level = requirement.required() ? "blocker" : "warning";
            statusText = "缺失";
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "missing-" + requirement.artifactType(),
                    level,
                    requirement.title() + "缺失",
                    requirement.required()
                            ? requirement.title() + "是当前节点进入开发交接的必要资产，必须先生成。"
                            : requirement.title() + "是建议资产，缺失不会阻断基础交接，但正式开发前建议补齐。",
                    requirement.artifactType()
            );
        } else if (!contentReady) {
            level = requirement.required() ? "blocker" : "warning";
            statusText = "内容为空";
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "empty-" + requirement.artifactType(),
                    level,
                    requirement.title() + "内容不可用",
                    requirement.title() + "存在空内容、占位内容或无有效 JSON/Markdown，不能可靠交给 Development Agent。",
                    requirement.artifactType()
            );
        } else if ("REJECTED".equals(status)) {
            level = requirement.required() ? "blocker" : "warning";
            statusText = "已退回";
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "rejected-" + requirement.artifactType(),
                    level,
                    requirement.title() + "已退回",
                    requirement.title() + "当前处于已退回状态，建议修改并重新确认后再进入开发交接。",
                    requirement.artifactType()
            );
        } else if ("DRAFT".equals(status)
                || "ACTIVE".equals(status)
                || "PENDING_REVIEW".equals(status)
                || "NEEDS_REVIEW".equals(status)) {
            level = "warning";
            statusText = "PENDING_REVIEW".equals(status)
                    ? "待审核"
                    : "NEEDS_REVIEW".equals(status) ? "需复核" : "未确认";
            issue = new DevelopmentHandoffIssueResponse(
                    "status-" + requirement.artifactType(),
                    "warning",
                    requirement.title() + "尚未正式确认",
                    requirement.title() + "当前状态为“" + statusText + "”。可用于预开发判断，但正式开发交接前建议审核通过。",
                    requirement.artifactType()
            );
        } else if ("APPROVED".equals(status)) {
            level = "pass";
            statusText = "已确认";
        } else {
            level = "warning";
            statusText = status;
            issue = new DevelopmentHandoffIssueResponse(
                    "status-" + requirement.artifactType(),
                    "warning",
                    requirement.title() + "状态需要复核",
                    requirement.title() + "当前状态为“" + status + "”，建议确认该状态是否允许进入开发交接。",
                    requirement.artifactType()
            );
        }

        if (artifact != null
                && Boolean.FALSE.equals(artifact.getCurrent())
                && !"blocker".equals(level)) {
            level = "warning";
            statusText = "非当前版本";
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "not-current-" + requirement.artifactType(),
                    "warning",
                    requirement.title() + "不是当前版本",
                    requirement.title() + "不是当前版本，Development Agent 应只读取当前版本资产。",
                    requirement.artifactType()
            );
        }

        DevelopmentHandoffAssetResponse asset = new DevelopmentHandoffAssetResponse(
                requirement.artifactType(),
                requirement.title(),
                requirement.required(),
                requirement.required() ? "CORE" : "RECOMMENDED",
                exists,
                contentReady,
                readyForDevelopment,
                artifact == null ? null : artifact.getId(),
                artifact == null ? null : artifact.getVersionNo(),
                status,
                artifact == null ? false : artifact.getCurrent(),
                artifact == null ? null : artifact.getUpdatedAt(),
                statusText
        );

        return new ArtifactReadiness(
                asset,
                issue
        );
    }

    private DevelopmentUiContractSnapshotSummary buildUiContractSnapshotSummary(
            BuildContext context
    ) {
        Optional<ConfirmedUiContractSnapshotResponse> snapshotOptional =
                context.uiContractSnapshot();

        ArtifactVersionEntity uiContract = context.artifactByType().get(UI_CONTRACT);

        if (snapshotOptional.isEmpty()) {
            return new DevelopmentUiContractSnapshotSummary(
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        ConfirmedUiContractSnapshotResponse snapshot = snapshotOptional.get();
        boolean matchesCurrentArtifact = uiContract != null
                && uiContract.getId() != null
                && uiContract.getId().equals(snapshot.artifactId());

        return new DevelopmentUiContractSnapshotSummary(
                true,
                matchesCurrentArtifact,
                snapshot.id(),
                snapshot.artifactId(),
                snapshot.artifactVersionNo(),
                snapshot.pageId(),
                snapshot.pageKey(),
                snapshot.pageName(),
                snapshot.status(),
                snapshot.confirmedBy(),
                snapshot.confirmedAt()
        );
    }

    private boolean hasMeaningfulArtifactContent(
            ArtifactVersionEntity artifact
    ) {
        if (artifact == null) {
            return false;
        }

        String markdown = artifact.getContentMarkdown();

        if (markdown != null && markdown.trim().length() > 20) {
            String normalized = markdown.trim();

            return !normalized.contains("当前模块尚未生成")
                    && !normalized.contains("暂无内容")
                    && !normalized.contains("TODO")
                    && !normalized.contains("占位");
        }

        Map<String, Object> content = artifact.getContent();

        if (content == null || content.isEmpty()) {
            return false;
        }

        String text = String.valueOf(content);

        return text.length() > 20
                && !text.contains("当前模块尚未生成")
                && !text.contains("暂无内容")
                && !text.contains("TODO")
                && !text.contains("占位");
    }

    private List<ArtifactRequirement> buildArtifactRequirements(
            String nodeType
    ) {
        return switch (nodeType) {
            case "DOMAIN" -> List.of(
                    required("MODULE_PRD", "业务域概览"),
                    required("BUSINESS_FLOW", "业务域流程总览"),
                    required("PAGE_SPEC", "模块/页面地图"),
                    recommended("DATA_MODEL", "数据主题总览"),
                    recommended("API_CONTRACT", "接口域总览"),
                    recommended("TEST_SPEC", "业务域测试范围"),
                    recommended("DEV_HANDOFF_PACKAGE", "业务域交付说明")
            );
            case "SUBMODULE" -> List.of(
                    required("MODULE_PRD", "场景需求说明"),
                    required("BUSINESS_FLOW", "场景业务流程"),
                    required("PAGE_SPEC", "场景页面清单"),
                    recommended("API_CONTRACT", "场景接口清单"),
                    recommended("TEST_SPEC", "场景测试规格"),
                    recommended("DEV_HANDOFF_PACKAGE", "场景开发说明")
            );
            case "PAGE" -> List.of(
                    required("PAGE_SPEC", "页面规范"),
                    required("DATA_MODEL", "页面数据模型"),
                    required("API_CONTRACT", "页面接口契约"),
                    required("UI_CONTRACT", "交互原型"),
                    recommended("MODULE_PRD", "页面需求"),
                    recommended("BUSINESS_FLOW", "页面交互流程"),
                    recommended("TEST_SPEC", "页面测试规格"),
                    recommended("DEV_HANDOFF_PACKAGE", "页面开发交接包")
            );
            case "FEATURE" -> List.of(
                    required("MODULE_PRD", "功能点需求"),
                    required("API_CONTRACT", "功能点接口"),
                    required("TEST_SPEC", "功能点测试"),
                    recommended("BUSINESS_FLOW", "功能点流程"),
                    recommended("DEV_HANDOFF_PACKAGE", "功能点开发说明")
            );
            case "MODULE" -> List.of(
                    required("MODULE_PRD", "模块 PRD"),
                    required("BUSINESS_FLOW", "模块业务流程"),
                    required("PAGE_SPEC", "页面地图"),
                    required("DATA_MODEL", "模块数据模型"),
                    required("API_CONTRACT", "模块接口规划"),
                    recommended("TEST_SPEC", "模块测试规格"),
                    recommended("DEV_HANDOFF_PACKAGE", "模块开发交接包")
            );
            default -> List.of(
                    required("MODULE_PRD", "模块 PRD"),
                    required("BUSINESS_FLOW", "业务流程"),
                    recommended("DEV_HANDOFF_PACKAGE", "开发交接包")
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

    private ArtifactRequirement recommended(
            String artifactType,
            String title
    ) {
        return new ArtifactRequirement(
                artifactType,
                title,
                false
        );
    }

    private String resolveNodeType(
            ProjectModuleEntity module
    ) {
        String rawType = safeText(module.getModuleType())
                .trim()
                .toUpperCase();

        return switch (rawType) {
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> "DOMAIN";
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> "SUBMODULE";
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION" -> "FEATURE";
            default -> "MODULE";
        };
    }

    private String nodeTypeLabel(
            String nodeType
    ) {
        return switch (nodeType) {
            case "DOMAIN" -> "业务域";
            case "SUBMODULE" -> "子模块 / 业务场景";
            case "PAGE" -> "页面";
            case "FEATURE" -> "功能点";
            default -> "模块";
        };
    }

    private String readinessText(
            String readinessLevel
    ) {
        return switch (readinessLevel) {
            case "READY" -> "满足开发交接条件";
            case "WARNING" -> "可进入开发准备，但建议先处理警告";
            default -> "暂不能进入正式开发 Agent";
        };
    }

    private String readinessDescription(
            String readinessLevel,
            String nodeType
    ) {
        if ("BLOCKED".equals(readinessLevel)) {
            return "当前节点存在必要资产缺失、内容不可用、交互原型未确认或确认快照滞后等阻断项。";
        }

        if ("WARNING".equals(readinessLevel)) {
            return "当前没有阻断项，但仍存在未审核、高级资产缺失或状态需复核的问题。";
        }

        if ("PAGE".equals(nodeType)) {
            return "页面正式资产与用户确认的 UI_CONTRACT 快照已满足 Development Agent 读取条件。";
        }

        return "当前节点正式资产已满足开发交接上下文构建条件。";
    }

    private String safeText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value;
    }

    private record ArtifactRequirement(
            String artifactType,
            String title,
            boolean required
    ) {
    }

    private record ArtifactReadiness(
            DevelopmentHandoffAssetResponse asset,
            DevelopmentHandoffIssueResponse issue
    ) {
    }

    private record BuildContext(
            ProjectEntity project,
            ProjectModuleEntity module,
            String nodeType,
            List<ArtifactRequirement> requirements,
            Map<String, ArtifactVersionEntity> artifactByType,
            Optional<ConfirmedUiContractSnapshotResponse> uiContractSnapshot
    ) {
    }
}
