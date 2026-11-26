package com.codemind.platform.development;

import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy.RequirementArtifactPlan;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy.RequirementNodeType;
import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.uicontract.ConfirmedUiContractSnapshotResponse;
import com.codemind.platform.uicontract.ConfirmedUiContractSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DevelopmentHandoffGateService {

    private static final String UI_CONTRACT = "UI_CONTRACT";

    private static final Set<String> PASS_STATUSES = Set.of(
            "ACTIVE",
            "APPROVED",
            "CONFIRMED"
    );

    private static final Set<String> REVIEW_STATUSES = Set.of(
            "DRAFT",
            "PENDING_REVIEW",
            "NEEDS_REVIEW"
    );

    private static final Set<String> UNUSABLE_STATUSES = Set.of(
            "REJECTED",
            "ARCHIVED",
            "SUPERSEDED"
    );

    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ConfirmedUiContractSnapshotService confirmedUiContractSnapshotService;
    private final ProjectModuleRepository projectModuleRepository;
    private final RequirementNodeGenerationPolicy nodeGenerationPolicy;

    public DevelopmentHandoffGateService(
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            ConfirmedUiContractSnapshotService confirmedUiContractSnapshotService,
            ProjectModuleRepository projectModuleRepository,
            RequirementNodeGenerationPolicy nodeGenerationPolicy
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.confirmedUiContractSnapshotService = confirmedUiContractSnapshotService;
        this.projectModuleRepository = projectModuleRepository;
        this.nodeGenerationPolicy = nodeGenerationPolicy;
    }

    @Transactional(readOnly = true)
    public DevelopmentHandoffReadinessResponse getReadiness(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        return buildReadiness(
                buildContext(workspaceId, projectCode, moduleId)
        );
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

        DevelopmentHandoffReadinessResponse readiness =
                buildReadiness(context);

        List<DevelopmentHandoffArtifactPayload> artifacts =
                new ArrayList<>();

        for (ArtifactRequirement requirement : context.requirements()) {
            ArtifactVersionEntity artifact =
                    context.artifactByRequirementKey()
                            .get(requirement.assetKey());

            ProjectModuleEntity sourceNode =
                    requirement.sourceNode();

            artifacts.add(
                    new DevelopmentHandoffArtifactPayload(
                            requirement.assetKey(),
                            requirement.artifactType(),
                            requirement.title(),
                            requirement.required(),
                            requirement.sourceScope(),
                            sourceNode == null ? null : sourceNode.getId(),
                            sourceNode == null ? null : sourceNode.getModuleKey(),
                            sourceNode == null ? null : sourceNode.getModuleName(),
                            sourceNode == null
                                    ? null
                                    : nodeGenerationPolicy.resolveNodeType(sourceNode).name(),
                            requirement.inherited(),
                            artifact == null ? null : artifact.getId(),
                            artifact == null ? null : artifact.getVersionNo(),
                            artifact == null ? "MISSING" : safeText(artifact.getStatus()),
                            artifact == null ? null : artifact.getContentMarkdown(),
                            artifact == null ? null : artifact.getContent()
                    )
            );
        }

        return new DevelopmentHandoffContextResponse(
                context.project().getId(),
                context.selectedNode().getId(),
                context.selectedNode().getModuleKey(),
                context.selectedNode().getModuleName(),
                context.selectedNode().getModuleType(),
                context.nodeType().name(),
                LocalDateTime.now(),
                readiness,
                artifacts,
                buildConfirmedUiContractPayload(context),
                buildThemeProfileSnapshot(context)
        );
    }

    private BuildContext buildContext(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        ProjectEntity project =
                projectAccessService.resolveAccessibleProjectEntity(
                        workspaceId,
                        projectCode
                );

        ProjectModuleEntity selectedNode =
                projectAccessService.resolveAccessibleModule(
                        project.getId(),
                        moduleId
                );

        List<ProjectModuleEntity> activeNodes =
                projectModuleRepository
                        .findByProjectIdOrderByLevelNoAscSortOrderAsc(project.getId())
                        .stream()
                        .filter(this::isActiveNode)
                        .toList();

        Map<UUID, ProjectModuleEntity> nodeById =
                new LinkedHashMap<>();

        for (ProjectModuleEntity node : activeNodes) {
            if (node != null && node.getId() != null) {
                nodeById.put(node.getId(), node);
            }
        }

        RequirementNodeType nodeType =
                nodeGenerationPolicy.resolveNodeType(selectedNode);

        boolean directDevelopmentAllowed =
                RequirementNodeType.MODULE.equals(nodeType)
                        || RequirementNodeType.PAGE.equals(nodeType);

        ProjectModuleEntity effectiveModule =
                resolveEffectiveModule(selectedNode, nodeType, nodeById);

        List<ProjectModuleEntity> scopedPages =
                resolveScopedPages(selectedNode, nodeType, activeNodes);

        List<ArtifactRequirement> requirements =
                buildArtifactRequirements(
                        selectedNode,
                        nodeType,
                        effectiveModule,
                        scopedPages
                );

        return new BuildContext(
                project,
                selectedNode,
                nodeType,
                directDevelopmentAllowed,
                gateMode(nodeType),
                effectiveModule,
                scopedPages,
                requirements,
                loadArtifacts(project.getId(), requirements),
                loadConfirmedSnapshots(
                        workspaceId,
                        projectCode,
                        scopedPages
                )
        );
    }

    private List<ArtifactRequirement> buildArtifactRequirements(
            ProjectModuleEntity selectedNode,
            RequirementNodeType nodeType,
            ProjectModuleEntity effectiveModule,
            List<ProjectModuleEntity> scopedPages
    ) {
        if (RequirementNodeType.MODULE.equals(nodeType)) {
            List<ArtifactRequirement> requirements =
                    new ArrayList<>();

            appendPlanRequirements(
                    requirements,
                    selectedNode,
                    nodeGenerationPolicy.fullChainDefinitionsFor(selectedNode),
                    "LOCAL_MODULE",
                    false,
                    null
            );

            for (ProjectModuleEntity page : scopedPages) {
                appendPlanRequirements(
                        requirements,
                        page,
                        nodeGenerationPolicy.fullChainDefinitionsFor(page),
                        "DESCENDANT_PAGE",
                        false,
                        safeText(page.getModuleName())
                );
            }

            return List.copyOf(requirements);
        }

        if (RequirementNodeType.PAGE.equals(nodeType)) {
            List<ArtifactRequirement> requirements =
                    new ArrayList<>();

            appendPlanRequirements(
                    requirements,
                    selectedNode,
                    nodeGenerationPolicy.fullChainDefinitionsFor(selectedNode),
                    "LOCAL_PAGE",
                    false,
                    null
            );

            if (effectiveModule != null) {
                appendPlanRequirements(
                        requirements,
                        effectiveModule,
                        nodeGenerationPolicy.fullChainDefinitionsFor(effectiveModule),
                        "INHERITED_MODULE",
                        true,
                        "继承自" + safeText(effectiveModule.getModuleName())
                );
            }

            return List.copyOf(requirements);
        }

        return List.of();
    }

    private void appendPlanRequirements(
            List<ArtifactRequirement> target,
            ProjectModuleEntity sourceNode,
            List<RequirementArtifactPlan> plans,
            String sourceScope,
            boolean inherited,
            String titlePrefix
    ) {
        if (sourceNode == null || plans == null) {
            return;
        }

        for (RequirementArtifactPlan plan : plans) {
            if (plan == null) {
                continue;
            }

            String title =
                    titlePrefix == null || titlePrefix.isBlank()
                            ? plan.title()
                            : titlePrefix + " · " + plan.title();

            target.add(
                    new ArtifactRequirement(
                            buildAssetKey(
                                    sourceNode.getId(),
                                    plan.artifactType(),
                                    sourceScope
                            ),
                            plan.artifactType(),
                            title,
                            true,
                            sourceScope,
                            sourceNode,
                            inherited,
                            plan.order()
                    )
            );
        }
    }

    private Map<String, ArtifactVersionEntity> loadArtifacts(
            UUID projectId,
            List<ArtifactRequirement> requirements
    ) {
        Map<UUID, Map<String, ArtifactVersionEntity>> artifactsByNode =
                new HashMap<>();

        Map<String, ArtifactVersionEntity> result =
                new LinkedHashMap<>();

        for (ArtifactRequirement requirement : requirements) {
            ProjectModuleEntity sourceNode =
                    requirement.sourceNode();

            if (sourceNode == null || sourceNode.getId() == null) {
                result.put(requirement.assetKey(), null);
                continue;
            }

            Map<String, ArtifactVersionEntity> currentByType =
                    artifactsByNode.computeIfAbsent(
                            sourceNode.getId(),
                            nodeId -> loadCurrentArtifactsByType(
                                    projectId,
                                    nodeId
                            )
                    );

            result.put(
                    requirement.assetKey(),
                    currentByType.get(requirement.artifactType())
            );
        }

        return result;
    }

    private Map<String, ArtifactVersionEntity> loadCurrentArtifactsByType(
            UUID projectId,
            UUID nodeId
    ) {
        Map<String, ArtifactVersionEntity> result =
                new LinkedHashMap<>();

        for (ArtifactVersionEntity artifact :
                artifactVersionRepository
                        .findByProjectIdAndModuleIdAndCurrentTrue(
                                projectId,
                                nodeId
                        )) {
            if (artifact == null || artifact.getArtifactType() == null) {
                continue;
            }

            result.put(artifact.getArtifactType(), artifact);
        }

        return result;
    }

    private Map<UUID, ConfirmedUiContractSnapshotResponse> loadConfirmedSnapshots(
            UUID workspaceId,
            String projectCode,
            List<ProjectModuleEntity> pages
    ) {
        Map<UUID, ConfirmedUiContractSnapshotResponse> result =
                new LinkedHashMap<>();

        for (ProjectModuleEntity page : pages) {
            if (page == null || page.getId() == null) {
                continue;
            }

            confirmedUiContractSnapshotService
                    .getCurrent(workspaceId, projectCode, page.getId())
                    .ifPresent(snapshot -> result.put(page.getId(), snapshot));
        }

        return result;
    }

    private DevelopmentHandoffReadinessResponse buildReadiness(
            BuildContext context
    ) {
        if (!context.directDevelopmentAllowed()) {
            return buildNotApplicableReadiness(context);
        }

        List<DevelopmentHandoffAssetResponse> assets =
                new ArrayList<>();

        List<DevelopmentHandoffIssueResponse> blockers =
                new ArrayList<>();

        List<DevelopmentHandoffIssueResponse> warnings =
                new ArrayList<>();

        List<DevelopmentHandoffIssueResponse> passes =
                new ArrayList<>();

        Set<String> requiredArtifactTypes =
                new LinkedHashSet<>();

        Set<String> recommendedArtifactTypes =
                new LinkedHashSet<>();

        if (RequirementNodeType.PAGE.equals(context.nodeType())
                && context.effectiveModule() == null) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "effective-module-missing",
                            "blocker",
                            "页面缺少所属模块",
                            "当前 PAGE 无法解析到所属 MODULE。"
                                    + "页面开发必须继承模块 PRD、流程、数据、接口、测试和开发交接包。",
                            null
                    )
            );
        }

        for (ArtifactRequirement requirement : context.requirements()) {
            if (requirement.required()) {
                requiredArtifactTypes.add(requirement.artifactType());
            } else {
                recommendedArtifactTypes.add(requirement.artifactType());
            }

            ArtifactReadiness artifactReadiness =
                    evaluateArtifact(
                            requirement,
                            context.artifactByRequirementKey()
                                    .get(requirement.assetKey())
                    );

            assets.add(artifactReadiness.asset());

            DevelopmentHandoffIssueResponse issue =
                    artifactReadiness.issue();

            if (issue == null) {
                passes.add(
                        new DevelopmentHandoffIssueResponse(
                                "pass-" + requirement.assetKey(),
                                "pass",
                                requirement.title() + "可用于开发交接",
                                buildPassDescription(requirement),
                                requirement.artifactType()
                        )
                );
            } else if ("blocker".equals(issue.level())) {
                blockers.add(issue);
            } else {
                warnings.add(issue);
            }
        }

        evaluateUiContractSnapshots(context, blockers, passes);

        String readinessLevel =
                blockers.isEmpty()
                        ? warnings.isEmpty() ? "READY" : "WARNING"
                        : "BLOCKED";

        return new DevelopmentHandoffReadinessResponse(
                context.project().getId(),
                context.selectedNode().getId(),
                context.selectedNode().getModuleKey(),
                context.selectedNode().getModuleName(),
                context.selectedNode().getModuleType(),
                context.nodeType().name(),
                nodeTypeLabel(context.nodeType()),
                true,
                context.gateMode(),
                context.effectiveModule() == null
                        ? null
                        : context.effectiveModule().getId(),
                context.effectiveModule() == null
                        ? null
                        : context.effectiveModule().getModuleKey(),
                context.effectiveModule() == null
                        ? null
                        : context.effectiveModule().getModuleName(),
                scopeSummary(context),
                blockers.isEmpty(),
                readinessLevel,
                readinessText(readinessLevel, context.nodeType()),
                readinessDescription(readinessLevel, context),
                calculateScore(blockers, warnings, passes),
                LocalDateTime.now(),
                List.copyOf(requiredArtifactTypes),
                List.copyOf(recommendedArtifactTypes),
                assets,
                blockers,
                warnings,
                passes,
                buildUiContractSnapshotSummary(context)
        );
    }

    private DevelopmentHandoffReadinessResponse buildNotApplicableReadiness(
            BuildContext context
    ) {
        return new DevelopmentHandoffReadinessResponse(
                context.project().getId(),
                context.selectedNode().getId(),
                context.selectedNode().getModuleKey(),
                context.selectedNode().getModuleName(),
                context.selectedNode().getModuleType(),
                context.nodeType().name(),
                nodeTypeLabel(context.nodeType()),
                false,
                context.gateMode(),
                null,
                null,
                null,
                scopeSummary(context),
                false,
                "NOT_APPLICABLE",
                notApplicableTitle(context.nodeType()),
                notApplicableDescription(context.nodeType()),
                0,
                LocalDateTime.now(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                emptySnapshotSummary()
        );
    }

    private ArtifactReadiness evaluateArtifact(
            ArtifactRequirement requirement,
            ArtifactVersionEntity artifact
    ) {
        boolean exists = artifact != null;
        boolean contentReady = hasMeaningfulArtifactContent(artifact);
        String status = artifact == null
                ? "MISSING"
                : normalizeStatus(artifact.getStatus());

        String statusText = "当前有效";
        boolean readyForDevelopment = true;
        DevelopmentHandoffIssueResponse issue = null;

        if (!exists) {
            String level = requirement.required() ? "blocker" : "warning";
            statusText = "缺失";
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "missing-" + requirement.assetKey(),
                    level,
                    requirement.title() + "缺失",
                    buildMissingDescription(requirement),
                    requirement.artifactType()
            );
        } else if (!contentReady) {
            String level = requirement.required() ? "blocker" : "warning";
            statusText = "内容不可用";
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "empty-" + requirement.assetKey(),
                    level,
                    requirement.title() + "内容不可用",
                    requirement.title()
                            + "存在空内容、占位内容或无有效 JSON/Markdown，"
                            + "不能可靠交给 Development Agent。",
                    requirement.artifactType()
            );
        } else if (UNUSABLE_STATUSES.contains(status)) {
            String level = requirement.required() ? "blocker" : "warning";
            statusText = switch (status) {
                case "REJECTED" -> "已退回";
                case "ARCHIVED" -> "已归档";
                case "SUPERSEDED" -> "已被替代";
                default -> status;
            };
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "unusable-" + requirement.assetKey(),
                    level,
                    requirement.title() + "当前不可用",
                    requirement.title()
                            + "当前状态为“"
                            + statusText
                            + "”，请恢复或重新生成当前有效版本。",
                    requirement.artifactType()
            );
        } else if (artifact != null
                && Boolean.FALSE.equals(artifact.getCurrent())) {
            statusText = "非当前版本";
            readyForDevelopment = false;
            issue = new DevelopmentHandoffIssueResponse(
                    "not-current-" + requirement.assetKey(),
                    requirement.required() ? "blocker" : "warning",
                    requirement.title() + "不是当前版本",
                    requirement.title()
                            + "不是当前版本，Development Agent 只能读取当前版本资产。",
                    requirement.artifactType()
            );
        } else if (REVIEW_STATUSES.contains(status)) {
            statusText = switch (status) {
                case "DRAFT" -> "草稿";
                case "PENDING_REVIEW" -> "待审核";
                case "NEEDS_REVIEW" -> "需复核";
                default -> status;
            };
            issue = new DevelopmentHandoffIssueResponse(
                    "review-" + requirement.assetKey(),
                    "warning",
                    requirement.title() + "需要人工复核",
                    requirement.title()
                            + "当前状态为“"
                            + statusText
                            + "”。该资产内容可进入开发准备，"
                            + "但正式交付前应完成复核。",
                    requirement.artifactType()
            );
        } else if (!PASS_STATUSES.contains(status)) {
            statusText = status.isBlank() ? "状态未声明" : status;
            issue = new DevelopmentHandoffIssueResponse(
                    "status-" + requirement.assetKey(),
                    "warning",
                    requirement.title() + "状态需要复核",
                    requirement.title()
                            + "当前状态为“"
                            + statusText
                            + "”，请确认该状态是否允许进入正式开发。",
                    requirement.artifactType()
            );
        } else if ("APPROVED".equals(status)
                || "CONFIRMED".equals(status)) {
            statusText = "已确认";
        }

        ProjectModuleEntity sourceNode = requirement.sourceNode();

        return new ArtifactReadiness(
                new DevelopmentHandoffAssetResponse(
                        requirement.assetKey(),
                        requirement.artifactType(),
                        requirement.title(),
                        requirement.required(),
                        requirement.required() ? "CORE" : "RECOMMENDED",
                        requirement.sourceScope(),
                        sourceNode == null ? null : sourceNode.getId(),
                        sourceNode == null ? null : sourceNode.getModuleKey(),
                        sourceNode == null ? null : sourceNode.getModuleName(),
                        sourceNode == null
                                ? null
                                : nodeGenerationPolicy.resolveNodeType(sourceNode).name(),
                        requirement.inherited(),
                        exists,
                        contentReady,
                        readyForDevelopment,
                        artifact == null ? null : artifact.getId(),
                        artifact == null ? null : artifact.getVersionNo(),
                        status,
                        artifact == null ? false : artifact.getCurrent(),
                        artifact == null ? null : artifact.getUpdatedAt(),
                        statusText
                ),
                issue
        );
    }

    private void evaluateUiContractSnapshots(
            BuildContext context,
            List<DevelopmentHandoffIssueResponse> blockers,
            List<DevelopmentHandoffIssueResponse> passes
    ) {
        for (ProjectModuleEntity page : context.scopedPages()) {
            if (page == null || page.getId() == null) {
                continue;
            }

            ArtifactVersionEntity uiContract =
                    findArtifact(context, page.getId(), UI_CONTRACT);

            if (uiContract == null || uiContract.getId() == null) {
                continue;
            }

            ConfirmedUiContractSnapshotResponse snapshot =
                    context.snapshotByPageId().get(page.getId());

            String pageName = safeText(page.getModuleName());

            if (snapshot == null) {
                blockers.add(
                        new DevelopmentHandoffIssueResponse(
                                "ui-contract-snapshot-missing-" + page.getId(),
                                "blocker",
                                pageName + "交互原型尚未确认",
                                "PAGE 进入真实代码生成前，必须先在交互预览中确认当前 UI_CONTRACT 页面效果。",
                                UI_CONTRACT
                        )
                );
                continue;
            }

            if (!uiContract.getId().equals(snapshot.artifactId())) {
                blockers.add(
                        new DevelopmentHandoffIssueResponse(
                                "ui-contract-snapshot-stale-" + page.getId(),
                                "blocker",
                                pageName + "交互原型确认快照已滞后",
                                "当前 UI_CONTRACT 版本与用户确认快照不一致。"
                                        + "请重新预览并确认当前页面效果。",
                                UI_CONTRACT
                        )
                );
                continue;
            }

            passes.add(
                    new DevelopmentHandoffIssueResponse(
                            "ui-contract-snapshot-pass-" + page.getId(),
                            "pass",
                            pageName + "交互原型已确认",
                            "已锁定当前 UI_CONTRACT 版本，"
                                    + "Development Agent 将按该页面蓝图生成真实代码。",
                            UI_CONTRACT
                    )
            );
        }
    }

    private ArtifactVersionEntity findArtifact(
            BuildContext context,
            UUID sourceNodeId,
            String artifactType
    ) {
        for (ArtifactRequirement requirement : context.requirements()) {
            ProjectModuleEntity sourceNode = requirement.sourceNode();

            if (sourceNode == null
                    || sourceNode.getId() == null
                    || !sourceNode.getId().equals(sourceNodeId)
                    || !artifactType.equals(requirement.artifactType())) {
                continue;
            }

            return context.artifactByRequirementKey()
                    .get(requirement.assetKey());
        }

        return null;
    }

    private DevelopmentUiContractSnapshotSummary buildUiContractSnapshotSummary(
            BuildContext context
    ) {
        if (RequirementNodeType.PAGE.equals(context.nodeType())) {
            ProjectModuleEntity page = context.selectedNode();
            ConfirmedUiContractSnapshotResponse snapshot =
                    context.snapshotByPageId().get(page.getId());
            ArtifactVersionEntity uiContract =
                    findArtifact(context, page.getId(), UI_CONTRACT);

            if (snapshot == null) {
                return emptySnapshotSummary();
            }

            boolean matchesCurrentArtifact =
                    uiContract != null
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

        if (RequirementNodeType.MODULE.equals(context.nodeType())) {
            boolean allExist = true;
            boolean allMatch = true;

            for (ProjectModuleEntity page : context.scopedPages()) {
                ConfirmedUiContractSnapshotResponse snapshot =
                        context.snapshotByPageId().get(page.getId());
                ArtifactVersionEntity uiContract =
                        findArtifact(context, page.getId(), UI_CONTRACT);

                if (snapshot == null) {
                    allExist = false;
                    allMatch = false;
                    continue;
                }

                if (uiContract == null
                        || uiContract.getId() == null
                        || !uiContract.getId().equals(snapshot.artifactId())) {
                    allMatch = false;
                }
            }

            return new DevelopmentUiContractSnapshotSummary(
                    context.scopedPages().isEmpty() || allExist,
                    context.scopedPages().isEmpty() || allMatch,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    context.scopedPages().isEmpty()
                            ? "NOT_REQUIRED"
                            : allMatch ? "CONFIRMED" : "INCOMPLETE",
                    null,
                    null
            );
        }

        return emptySnapshotSummary();
    }

    private DevelopmentUiContractSnapshotSummary emptySnapshotSummary() {
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

    private Map<String, Object> buildConfirmedUiContractPayload(
            BuildContext context
    ) {
        if (RequirementNodeType.PAGE.equals(context.nodeType())) {
            return Optional.ofNullable(
                            context.snapshotByPageId()
                                    .get(context.selectedNode().getId())
                    )
                    .map(ConfirmedUiContractSnapshotResponse::uiContract)
                    .orElse(null);
        }

        if (!RequirementNodeType.MODULE.equals(context.nodeType())
                || context.scopedPages().isEmpty()) {
            return null;
        }

        List<Map<String, Object>> pages = new ArrayList<>();

        for (ProjectModuleEntity page : context.scopedPages()) {
            ConfirmedUiContractSnapshotResponse snapshot =
                    context.snapshotByPageId().get(page.getId());

            if (snapshot == null) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", page.getId().toString());
            item.put("pageId", snapshot.pageId());
            item.put("pageKey", snapshot.pageKey());
            item.put("pageName", snapshot.pageName());
            item.put("snapshotId", snapshot.id());
            item.put("artifactId", snapshot.artifactId());
            item.put("artifactVersionNo", snapshot.artifactVersionNo());
            item.put("status", snapshot.status());
            item.put("confirmedBy", snapshot.confirmedBy());
            item.put("confirmedAt", snapshot.confirmedAt());
            item.put("uiContract", snapshot.uiContract());
            pages.add(item);
        }

        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("scopeType", "MODULE");
        aggregate.put("moduleId", context.selectedNode().getId());
        aggregate.put("moduleKey", context.selectedNode().getModuleKey());
        aggregate.put("moduleName", context.selectedNode().getModuleName());
        aggregate.put("pageCount", context.scopedPages().size());
        aggregate.put("confirmedPageCount", pages.size());
        aggregate.put("pages", pages);
        return aggregate;
    }

    private Map<String, Object> buildThemeProfileSnapshot(
            BuildContext context
    ) {
        for (ProjectModuleEntity page : context.scopedPages()) {
            ConfirmedUiContractSnapshotResponse snapshot =
                    context.snapshotByPageId().get(page.getId());

            if (snapshot != null
                    && snapshot.themeProfileSnapshot() != null
                    && !snapshot.themeProfileSnapshot().isEmpty()) {
                return snapshot.themeProfileSnapshot();
            }
        }

        return null;
    }

    private ProjectModuleEntity resolveEffectiveModule(
            ProjectModuleEntity selectedNode,
            RequirementNodeType nodeType,
            Map<UUID, ProjectModuleEntity> nodeById
    ) {
        if (RequirementNodeType.MODULE.equals(nodeType)) {
            return selectedNode;
        }

        if (!RequirementNodeType.PAGE.equals(nodeType)) {
            return null;
        }

        Set<UUID> visited = new HashSet<>();
        UUID currentId = selectedNode.getParentId();

        while (currentId != null && visited.add(currentId)) {
            ProjectModuleEntity current = nodeById.get(currentId);

            if (current == null) {
                return null;
            }

            if (RequirementNodeType.MODULE.equals(
                    nodeGenerationPolicy.resolveNodeType(current)
            )) {
                return current;
            }

            currentId = current.getParentId();
        }

        return null;
    }

    private List<ProjectModuleEntity> resolveScopedPages(
            ProjectModuleEntity selectedNode,
            RequirementNodeType nodeType,
            List<ProjectModuleEntity> activeNodes
    ) {
        if (RequirementNodeType.PAGE.equals(nodeType)) {
            return List.of(selectedNode);
        }

        if (!RequirementNodeType.MODULE.equals(nodeType)) {
            return List.of();
        }

        Map<UUID, List<ProjectModuleEntity>> childrenByParent =
                new LinkedHashMap<>();

        for (ProjectModuleEntity node : activeNodes) {
            if (node == null || node.getParentId() == null) {
                continue;
            }

            childrenByParent
                    .computeIfAbsent(
                            node.getParentId(),
                            ignored -> new ArrayList<>()
                    )
                    .add(node);
        }

        Comparator<ProjectModuleEntity> comparator =
                Comparator.comparing(
                                ProjectModuleEntity::getLevelNo,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(
                                ProjectModuleEntity::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(
                                ProjectModuleEntity::getModuleName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                        );

        for (List<ProjectModuleEntity> children : childrenByParent.values()) {
            children.sort(comparator);
        }

        List<ProjectModuleEntity> pages = new ArrayList<>();
        ArrayDeque<ProjectModuleEntity> queue =
                new ArrayDeque<>(
                        childrenByParent.getOrDefault(
                                selectedNode.getId(),
                                List.of()
                        )
                );

        while (!queue.isEmpty()) {
            ProjectModuleEntity node = queue.removeFirst();
            RequirementNodeType currentType =
                    nodeGenerationPolicy.resolveNodeType(node);

            if (RequirementNodeType.MODULE.equals(currentType)) {
                // 嵌套 MODULE 是新的开发切片边界，不能被父 MODULE 聚合。
                continue;
            }

            if (RequirementNodeType.PAGE.equals(currentType)) {
                pages.add(node);
            }

            queue.addAll(
                    childrenByParent.getOrDefault(
                            node.getId(),
                            List.of()
                    )
            );
        }

        pages.sort(comparator);
        return List.copyOf(pages);
    }

    private boolean isActiveNode(ProjectModuleEntity node) {
        return node != null
                && (
                        node.getStatus() == null
                                || node.getStatus().isBlank()
                                || "ACTIVE".equalsIgnoreCase(node.getStatus())
                );
    }

    private int calculateScore(
            List<DevelopmentHandoffIssueResponse> blockers,
            List<DevelopmentHandoffIssueResponse> warnings,
            List<DevelopmentHandoffIssueResponse> passes
    ) {
        int total = blockers.size() + warnings.size() + passes.size();

        if (total <= 0) {
            return 100;
        }

        double achieved = passes.size() + warnings.size() * 0.75d;

        return Math.max(
                0,
                Math.min(
                        100,
                        (int) Math.round(achieved * 100.0d / total)
                )
        );
    }

    private String buildPassDescription(ArtifactRequirement requirement) {
        ProjectModuleEntity sourceNode = requirement.sourceNode();
        String sourceName = sourceNode == null
                ? "当前节点"
                : safeText(sourceNode.getModuleName());

        if (requirement.inherited()) {
            return requirement.title()
                    + "存在当前有效内容，已从所属模块“"
                    + sourceName
                    + "”继承到当前 PAGE 的开发上下文。";
        }

        if ("DESCENDANT_PAGE".equals(requirement.sourceScope())) {
            return requirement.title()
                    + "存在当前有效内容，已纳入模块级开发上下文。";
        }

        return requirement.title()
                + "存在当前有效内容，可纳入 Development Agent 开发上下文。";
    }

    private String buildMissingDescription(ArtifactRequirement requirement) {
        if (requirement.inherited()) {
            return requirement.title()
                    + "是当前 PAGE 从所属 MODULE 继承的必要开发输入。"
                    + "请返回所属模块执行“一键准备模块开发资产”或补齐该资产。";
        }

        if ("DESCENDANT_PAGE".equals(requirement.sourceScope())) {
            return requirement.title()
                    + "是模块下 PAGE 的必要页面契约。"
                    + "请在对应页面生成 / 更新页面资产。";
        }

        return requirement.title()
                + "是当前开发切片的必要资产，必须先生成。";
    }

    private String scopeSummary(BuildContext context) {
        return switch (context.nodeType()) {
            case MODULE ->
                    "当前 MODULE 是正式开发切片。门禁会检查模块本地核心资产，"
                            + "并聚合 "
                            + context.scopedPages().size()
                            + " 个下属 PAGE 的页面规范、交互原型和确认快照。";
            case PAGE ->
                    "当前 PAGE 是页面开发入口。门禁检查页面本地 PAGE_SPEC / UI_CONTRACT，"
                            + (
                                    context.effectiveModule() == null
                                            ? "但尚未找到可继承的所属 MODULE。"
                                            : "并继承所属模块“"
                                                    + safeText(context.effectiveModule().getModuleName())
                                                    + "”的模块级开发资产。"
                            );
            case DOMAIN ->
                    "业务域是上下文和索引层，不作为直接代码生成单元。请选择下属 MODULE。";
            case SUBMODULE ->
                    "子模块是场景分组层，不作为直接代码生成单元。请选择上层 MODULE 或具体 PAGE。";
            case FEATURE ->
                    "功能点是父 PAGE 的内联需求片段，不作为独立代码生成单元。请选择所属 PAGE。";
        };
    }

    private String gateMode(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case MODULE -> "MODULE_AGGREGATE";
            case PAGE -> "PAGE_WITH_MODULE_INHERITANCE";
            case DOMAIN -> "CONTEXT_ONLY";
            case SUBMODULE -> "GROUP_ONLY";
            case FEATURE -> "INLINE_ONLY";
        };
    }

    private String nodeTypeLabel(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "业务域";
            case MODULE -> "模块";
            case SUBMODULE -> "子模块 / 业务场景";
            case PAGE -> "页面";
            case FEATURE -> "功能点";
        };
    }

    private String readinessText(
            String readinessLevel,
            RequirementNodeType nodeType
    ) {
        return switch (readinessLevel) {
            case "READY" -> RequirementNodeType.MODULE.equals(nodeType)
                    ? "模块开发输入已就绪"
                    : "页面开发输入已就绪";
            case "WARNING" -> "可进入开发准备，但存在待复核项";
            default -> "暂不能进入正式 Development Agent";
        };
    }

    private String readinessDescription(
            String readinessLevel,
            BuildContext context
    ) {
        if ("BLOCKED".equals(readinessLevel)) {
            return "当前开发范围存在必要资产缺失、内容不可用、"
                    + "页面原型未确认或确认快照滞后等阻断项。";
        }

        if ("WARNING".equals(readinessLevel)) {
            return "必要资产已经具备，但部分资产处于草稿、待审核或需复核状态。"
                    + "警告不会阻断当前开发准备，正式交付前应完成复核。";
        }

        if (RequirementNodeType.MODULE.equals(context.nodeType())) {
            return "模块级资产、下属页面契约和当前确认快照已经形成完整开发输入。";
        }

        return "页面本地契约、所属模块继承资产和当前 UI_CONTRACT 确认快照已经就绪。";
    }

    private String notApplicableTitle(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "业务域不直接进入 Development Agent";
            case SUBMODULE -> "子模块不作为独立开发交接单元";
            case FEATURE -> "功能点已内联到父页面";
            default -> "当前节点不作为直接开发单元";
        };
    }

    private String notApplicableDescription(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN ->
                    "请选择业务域下的 MODULE 执行模块级开发，"
                            + "或选择具体 PAGE 执行页面级开发。";
            case SUBMODULE ->
                    "子模块只组织业务场景和页面。"
                            + "请选择上层 MODULE 或具体 PAGE。";
            case FEATURE ->
                    "功能点的字段、动作、校验、跳转和异常场景已经归入父 PAGE。"
                            + "请选择所属 PAGE 生成真实页面代码。";
            default -> "请选择 MODULE 或 PAGE 作为正式开发入口。";
        };
    }

    private boolean hasMeaningfulArtifactContent(
            ArtifactVersionEntity artifact
    ) {
        if (artifact == null) {
            return false;
        }

        String markdown = artifact.getContentMarkdown();

        if (markdown != null && markdown.trim().length() > 20) {
            return !containsPlaceholder(markdown.trim());
        }

        Map<String, Object> content = artifact.getContent();

        if (content == null || content.isEmpty()) {
            return false;
        }

        String normalized = String.valueOf(content);
        return normalized.length() > 20
                && !containsPlaceholder(normalized);
    }

    private boolean containsPlaceholder(String value) {
        String text = value == null ? "" : value;

        return text.contains("当前模块尚未生成")
                || text.contains("暂无内容")
                || text.contains("占位内容")
                || text.contains("\"placeholder\":true");
    }

    private String buildAssetKey(
            UUID sourceNodeId,
            String artifactType,
            String sourceScope
    ) {
        return safeText(sourceNodeId == null ? null : sourceNodeId.toString())
                + ":"
                + safeText(artifactType)
                + ":"
                + safeText(sourceScope);
    }

    private String normalizeStatus(String value) {
        return safeText(value)
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record ArtifactRequirement(
            String assetKey,
            String artifactType,
            String title,
            boolean required,
            String sourceScope,
            ProjectModuleEntity sourceNode,
            boolean inherited,
            int order
    ) {
    }

    private record ArtifactReadiness(
            DevelopmentHandoffAssetResponse asset,
            DevelopmentHandoffIssueResponse issue
    ) {
    }

    private record BuildContext(
            ProjectEntity project,
            ProjectModuleEntity selectedNode,
            RequirementNodeType nodeType,
            boolean directDevelopmentAllowed,
            String gateMode,
            ProjectModuleEntity effectiveModule,
            List<ProjectModuleEntity> scopedPages,
            List<ArtifactRequirement> requirements,
            Map<String, ArtifactVersionEntity> artifactByRequirementKey,
            Map<UUID, ConfirmedUiContractSnapshotResponse> snapshotByPageId
    ) {
    }
}
