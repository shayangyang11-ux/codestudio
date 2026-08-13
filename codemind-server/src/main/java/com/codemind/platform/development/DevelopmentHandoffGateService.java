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

    private static final String MODULE_PRD = "MODULE_PRD";
    private static final String PAGE_SPEC = "PAGE_SPEC";
    private static final String UI_CONTRACT = "UI_CONTRACT";
    private static final String MODULE_APP_CONTRACT = "MODULE_APP_CONTRACT";
    private static final String MODULE_PROTOTYPE_SNAPSHOT = "MODULE_PROTOTYPE_SNAPSHOT";

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

        Map<String, Object> confirmedUiContractPayload =
                buildConfirmedUiContractPayload(context);
        Map<String, Object> themeProfileSnapshot =
                buildThemeProfileSnapshot(context);
        Map<String, Object> developmentBuildContextPayload =
                buildDevelopmentBuildContextPayload(
                        context,
                        readiness,
                        artifacts,
                        confirmedUiContractPayload,
                        themeProfileSnapshot
                );

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
                confirmedUiContractPayload,
                developmentBuildContextPayload,
                themeProfileSnapshot
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
                RequirementNodeType.MODULE.equals(nodeType);

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
                            !"DESCENDANT_PAGE".equals(sourceScope),
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

        // A9.10.14.4.3_PRD_PROTOTYPE_SYNC_LOOP
        evaluatePrdPrototypeSemanticConsistency(
                context,
                blockers,
                warnings,
                passes
        );

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


    private void evaluatePrdPrototypeSemanticConsistency(
            BuildContext context,
            List<DevelopmentHandoffIssueResponse> blockers,
            List<DevelopmentHandoffIssueResponse> warnings,
            List<DevelopmentHandoffIssueResponse> passes
    ) {
        if (!RequirementNodeType.MODULE.equals(context.nodeType())) {
            return;
        }

        ArtifactVersionEntity snapshotArtifact =
                findCurrentArtifact(
                        context,
                        context.selectedNode().getId(),
                        MODULE_PROTOTYPE_SNAPSHOT
                );

        if (snapshotArtifact == null || snapshotArtifact.getUpdatedAt() == null) {
            return;
        }

        LocalDateTime baselineAt = snapshotArtifact.getUpdatedAt();

        ArtifactVersionEntity modulePrd =
                findCurrentArtifact(
                        context,
                        context.selectedNode().getId(),
                        MODULE_PRD
                );

        if (isArtifactNewerThan(modulePrd, baselineAt)) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "semantic-stale-module-prd-" + context.selectedNode().getId(),
                            "blocker",
                            "模块 PRD 晚于开发基线",
                            "MODULE_PRD 在模块汇总原型确认为开发基线之后发生过更新。"
                                    + "请重新检查受影响 PAGE_SPEC/UI_CONTRACT，并重新确认模块汇总原型开发基线。",
                            MODULE_PRD
                    )
            );
        }

        ArtifactVersionEntity moduleAppContract =
                findCurrentArtifact(
                        context,
                        context.selectedNode().getId(),
                        MODULE_APP_CONTRACT
                );

        if (isArtifactNewerThan(moduleAppContract, baselineAt)) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "semantic-stale-module-app-contract-" + context.selectedNode().getId(),
                            "blocker",
                            "模块应用契约晚于开发基线",
                            "MODULE_APP_CONTRACT 在模块汇总原型确认为开发基线之后发生过更新。"
                                    + "请重新打开完整模块预览并确认为开发基线。",
                            MODULE_APP_CONTRACT
                    )
            );
        }

        List<String> pageSpecNewerThanUi = new ArrayList<>();
        List<String> pageSpecNewerThanBaseline = new ArrayList<>();
        List<String> uiNeedsPrdReview = new ArrayList<>();

        for (ProjectModuleEntity page : context.scopedPages()) {
            if (page == null || page.getId() == null) {
                continue;
            }

            ArtifactVersionEntity pageSpec =
                    findCurrentArtifact(
                            context,
                            page.getId(),
                            PAGE_SPEC
                    );

            ArtifactVersionEntity uiContract =
                    findCurrentArtifact(
                            context,
                            page.getId(),
                            UI_CONTRACT
                    );

            String pageName = safeText(page.getModuleName()).isBlank()
                    ? page.getId().toString()
                    : safeText(page.getModuleName());

            if (pageSpec != null
                    && uiContract != null
                    && pageSpec.getUpdatedAt() != null
                    && uiContract.getUpdatedAt() != null
                    && pageSpec.getUpdatedAt().isAfter(uiContract.getUpdatedAt())
                    && !isPageSpecBackgroundSyncAbsorbedByUi(pageSpec, uiContract)) {
                pageSpecNewerThanUi.add(pageName);
            }

            if (isArtifactNewerThan(pageSpec, baselineAt)) {
                pageSpecNewerThanBaseline.add(pageName);
            }

            if (uiContract != null
                    && mapValue(uiContract.getContent()) != null
                    && mapValue(uiContract.getContent()).containsKey("_prd_prototype_semantic_sync")) {
                Map<String, Object> sync =
                        mapValue(mapValue(uiContract.getContent()).get("_prd_prototype_semantic_sync"));

                String status = sync == null ? "" : textValue(sync.get("status"), "");

                if ("PRD_SYNC_PROPOSAL_REQUIRED".equals(status)
                        || "PRD_SYNC_REVIEW_REQUIRED".equals(status)) {
                    uiNeedsPrdReview.add(pageName);
                }
            }
        }

        if (!pageSpecNewerThanUi.isEmpty()) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "semantic-page-spec-newer-than-ui-contract",
                            "blocker",
                            "页面规范晚于交互原型",
                            "以下页面的 PAGE_SPEC 更新时间晚于 UI_CONTRACT，说明原型可能没有吸收最新页面规范："
                                    + String.join("、", pageSpecNewerThanUi)
                                    + "。请重新生成或同步修订对应交互原型后，再确认开发基线。",
                            PAGE_SPEC
                    )
            );
        }

        if (!pageSpecNewerThanBaseline.isEmpty()) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "semantic-page-spec-newer-than-module-baseline",
                            "blocker",
                            "页面规范晚于模块开发基线",
                            "以下页面的 PAGE_SPEC 在模块汇总原型确认之后发生过更新："
                                    + String.join("、", pageSpecNewerThanBaseline)
                                    + "。请同步 UI_CONTRACT 并重新确认模块汇总原型开发基线。",
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
        }

        if (!uiNeedsPrdReview.isEmpty()) {
            warnings.add(
                    new DevelopmentHandoffIssueResponse(
                            "semantic-ui-contract-prd-sync-review",
                            "warning",
                            "交互原型存在 PRD 同步建议",
                            "以下页面的交互原型保存后产生了 PRD/PAGE_SPEC 同步建议："
                                    + String.join("、", uiNeedsPrdReview)
                                    + "。正式进入开发前建议确认这些建议是否已经吸收到 PRD 或页面规范。",
                            UI_CONTRACT
                    )
            );
        }

        if (modulePrd != null
                && pageSpecNewerThanUi.isEmpty()
                && pageSpecNewerThanBaseline.isEmpty()
                && !isArtifactNewerThan(modulePrd, baselineAt)) {
            passes.add(
                    new DevelopmentHandoffIssueResponse(
                            "semantic-prd-prototype-consistency-pass-" + snapshotArtifact.getId(),
                            "pass",
                            "PRD / 页面规范 / 交互原型版本链路一致",
                            "当前 MODULE_PRD、PAGE_SPEC、UI_CONTRACT 与 MODULE_PROTOTYPE_SNAPSHOT 未发现明显时间顺序冲突。",
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
        }
    }

    private boolean isArtifactNewerThan(
            ArtifactVersionEntity artifact,
            LocalDateTime baselineAt
    ) {
        return artifact != null
                && artifact.getUpdatedAt() != null
                && baselineAt != null
                && artifact.getUpdatedAt().isAfter(baselineAt);
    }

    private boolean isPageSpecBackgroundSyncAbsorbedByUi(
            ArtifactVersionEntity pageSpec,
            ArtifactVersionEntity uiContract
    ) {
        if (pageSpec == null || uiContract == null) {
            return false;
        }

        Map<String, Object> uiContent = mapValue(uiContract.getContent());
        if (uiContent == null || uiContent.isEmpty()) {
            return false;
        }

        Map<String, Object> metadata = mapValue(uiContent.get("metadata"));
        Map<String, Object> sync = mapValue(uiContent.get("_prd_prototype_semantic_sync"));
        Map<String, Object> rootBackgroundSync = mapValue(uiContent.get("_page_spec_background_sync"));
        Map<String, Object> nestedBackgroundSync = sync == null
                ? null
                : mapValue(sync.get("pageSpecBackgroundSync"));

        if (sync != null) {
            String status = textValue(sync.get("status"), "");
            if (!"PAGE_SPEC_BACKGROUND_SYNC_APPLIED".equals(status)) {
                return false;
            }
        }

        Integer pageSpecVersionNo = pageSpec.getVersionNo();
        UUID pageSpecArtifactId = pageSpec.getId();

        if (matchesArtifactVersion(metadata == null ? null : metadata.get("absorbedPageSpecVersionNo"), pageSpecVersionNo)
                || matchesArtifactVersion(rootBackgroundSync == null ? null : rootBackgroundSync.get("pageSpecVersionNo"), pageSpecVersionNo)
                || matchesArtifactVersion(nestedBackgroundSync == null ? null : nestedBackgroundSync.get("pageSpecVersionNo"), pageSpecVersionNo)) {
            return true;
        }

        return matchesArtifactId(metadata == null ? null : metadata.get("absorbedPageSpecArtifactId"), pageSpecArtifactId)
                || matchesArtifactId(rootBackgroundSync == null ? null : rootBackgroundSync.get("pageSpecArtifactId"), pageSpecArtifactId)
                || matchesArtifactId(nestedBackgroundSync == null ? null : nestedBackgroundSync.get("pageSpecArtifactId"), pageSpecArtifactId);
    }

    private boolean matchesArtifactVersion(
            Object value,
            Integer expected
    ) {
        if (value == null || expected == null) {
            return false;
        }

        if (value instanceof Number number) {
            return number.intValue() == expected;
        }

        try {
            return Integer.parseInt(String.valueOf(value).trim()) == expected;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesArtifactId(
            Object value,
            UUID expected
    ) {
        if (value == null || expected == null) {
            return false;
        }

        UUID actual = uuidValue(value);
        return expected.equals(actual);
    }


    private void evaluateUiContractSnapshots(
            BuildContext context,
            List<DevelopmentHandoffIssueResponse> blockers,
            List<DevelopmentHandoffIssueResponse> passes
    ) {
        if (!RequirementNodeType.MODULE.equals(context.nodeType())) {
            return;
        }

        ArtifactVersionEntity snapshotArtifact =
                findCurrentArtifact(
                        context,
                        context.selectedNode().getId(),
                        MODULE_PROTOTYPE_SNAPSHOT
                );

        if (snapshotArtifact == null || snapshotArtifact.getId() == null) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "module-prototype-snapshot-missing-" + context.selectedNode().getId(),
                            "blocker",
                            "模块汇总原型尚未确认为开发基线",
                            "Development Agent 只能使用需求 Agent 中已确认的模块汇总原型快照。"
                                    + "请先打开完整模块预览，检查页面、导航和动作流后，点击“确认为开发基线”。",
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
            return;
        }

        Map<String, Object> snapshot = mapValue(snapshotArtifact.getContent());
        if (snapshot == null || snapshot.isEmpty()) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "module-prototype-snapshot-empty-" + snapshotArtifact.getId(),
                            "blocker",
                            "模块汇总原型确认快照为空",
                            "当前 MODULE_PROTOTYPE_SNAPSHOT 没有可读取的页面与应用壳内容，不能驱动 Development Agent。",
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
            return;
        }

        List<Map<String, Object>> pages = mapListValue(snapshot.get("pages"));
        if (pages.isEmpty()) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "module-prototype-snapshot-pages-empty-" + snapshotArtifact.getId(),
                            "blocker",
                            "模块开发基线没有页面",
                            "模块汇总原型确认快照中没有页面清单，不能生成 Node.js 模块工程。",
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
            return;
        }

        long usableCount =
                pages.stream()
                        .filter(page -> Boolean.TRUE.equals(page.get("hasUiContract"))
                                && mapValue(page.get("uiContract")) != null
                                && !mapValue(page.get("uiContract")).isEmpty())
                        .count();

        if (usableCount <= 0) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "module-prototype-snapshot-no-page-ui-" + snapshotArtifact.getId(),
                            "blocker",
                            "模块开发基线没有可用页面原型",
                            "请至少生成并保存一个 PAGE_UI_CONTRACT，然后在完整模块预览中确认为开发基线。",
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
            return;
        }

        Map<String, Object> moduleAppArtifact =
                mapValue(snapshot.get("moduleAppArtifact"));
        UUID lockedModuleAppArtifactId =
                uuidValue(moduleAppArtifact == null ? null : moduleAppArtifact.get("artifactId"));

        ArtifactVersionEntity currentModuleApp =
                findCurrentArtifact(
                        context,
                        context.selectedNode().getId(),
                        MODULE_APP_CONTRACT
                );

        if (lockedModuleAppArtifactId != null
                && currentModuleApp != null
                && currentModuleApp.getId() != null
                && !lockedModuleAppArtifactId.equals(currentModuleApp.getId())) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "module-app-contract-stale-" + context.selectedNode().getId(),
                            "blocker",
                            "模块汇总原型开发基线已过期",
                            "MODULE_APP_CONTRACT 已更新，但当前开发基线仍锁定旧版本。"
                                    + "请重新打开完整模块预览并确认为开发基线。",
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
            return;
        }

        Set<String> stalePages = new LinkedHashSet<>();

        for (Map<String, Object> page : pages) {
            if (!Boolean.TRUE.equals(page.get("hasUiContract"))) {
                continue;
            }

            UUID nodeId = uuidValue(page.get("nodeId"));
            UUID lockedArtifactId = uuidValue(page.get("artifactId"));

            if (nodeId == null || lockedArtifactId == null) {
                stalePages.add(textValue(page.get("pageName"), "未知页面"));
                continue;
            }

            ArtifactVersionEntity currentUiContract =
                    findCurrentArtifact(context, nodeId, UI_CONTRACT);

            if (currentUiContract == null
                    || currentUiContract.getId() == null
                    || !lockedArtifactId.equals(currentUiContract.getId())) {
                stalePages.add(textValue(page.get("pageName"), "未知页面"));
            }
        }

        if (!stalePages.isEmpty()) {
            blockers.add(
                    new DevelopmentHandoffIssueResponse(
                            "module-prototype-snapshot-page-stale-" + snapshotArtifact.getId(),
                            "blocker",
                            "模块开发基线中的页面原型已过期",
                            "以下页面的 PAGE_UI_CONTRACT 已在确认后发生变化，需要重新确认模块汇总原型："
                                    + String.join("、", stalePages),
                            MODULE_PROTOTYPE_SNAPSHOT
                    )
            );
            return;
        }

        passes.add(
                new DevelopmentHandoffIssueResponse(
                        "module-prototype-snapshot-confirmed-" + snapshotArtifact.getId(),
                        "pass",
                        "模块汇总原型已确认为开发基线",
                        "Development Agent 将基于 MODULE_PROTOTYPE_SNAPSHOT 生成代码；"
                                + usableCount + "/" + pages.size()
                                + " 个页面包含可用 PAGE_UI_CONTRACT，未包含原型的页面只允许占位提示。",
                        MODULE_PROTOTYPE_SNAPSHOT
                )
        );
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
        if (!RequirementNodeType.MODULE.equals(context.nodeType())) {
            return emptySnapshotSummary();
        }

        ArtifactVersionEntity snapshotArtifact =
                findCurrentArtifact(
                        context,
                        context.selectedNode().getId(),
                        MODULE_PROTOTYPE_SNAPSHOT
                );

        if (snapshotArtifact == null || snapshotArtifact.getId() == null) {
            return emptySnapshotSummary();
        }

        Map<String, Object> snapshot = mapValue(snapshotArtifact.getContent());
        List<Map<String, Object>> pages =
                snapshot == null ? List.of() : mapListValue(snapshot.get("pages"));
        long usableCount = pages.stream()
                .filter(page -> Boolean.TRUE.equals(page.get("hasUiContract")))
                .count();

        boolean partial = !pages.isEmpty() && usableCount < pages.size();

        return new DevelopmentUiContractSnapshotSummary(
                true,
                true,
                snapshotArtifact.getId(),
                snapshotArtifact.getId(),
                snapshotArtifact.getVersionNo(),
                context.selectedNode().getId() == null
                        ? null
                        : context.selectedNode().getId().toString(),
                context.selectedNode().getModuleKey(),
                context.selectedNode().getModuleName(),
                partial
                        ? "PARTIAL_MODULE_PROTOTYPE_CONFIRMED"
                        : "MODULE_PROTOTYPE_CONFIRMED",
                null,
                snapshotArtifact.getUpdatedAt()
        );
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

    private Map<String, Object> buildModulePrototypeSnapshotPayload(
            BuildContext context
    ) {
        if (!RequirementNodeType.MODULE.equals(context.nodeType())) {
            return null;
        }

        ArtifactVersionEntity artifact =
                findCurrentArtifact(
                        context,
                        context.selectedNode().getId(),
                        MODULE_PROTOTYPE_SNAPSHOT
                );

        if (artifact == null || artifact.getId() == null) {
            return null;
        }

        Map<String, Object> content = mapValue(artifact.getContent());
        if (content == null || content.isEmpty()) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>(content);
        payload.put("snapshotArtifactId", artifact.getId());
        payload.put("snapshotVersionNo", artifact.getVersionNo());
        payload.put("snapshotUpdatedAt", artifact.getUpdatedAt());
        payload.put("snapshotStatus", artifact.getStatus());
        payload.putIfAbsent("artifactType", MODULE_PROTOTYPE_SNAPSHOT);
        payload.putIfAbsent("source", "MODULE_PROTOTYPE_SNAPSHOT");
        return payload;
    }

    private ArtifactVersionEntity findCurrentArtifact(
            BuildContext context,
            UUID nodeId,
            String artifactType
    ) {
        if (context == null
                || context.project() == null
                || nodeId == null
                || artifactType == null
                || artifactType.isBlank()) {
            return null;
        }

        return artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        context.project().getId(),
                        nodeId,
                        artifactType
                )
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(
            Object value
    ) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        return null;
    }

    private List<Map<String, Object>> mapListValue(
            Object value
    ) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object item : raw) {
            Map<String, Object> map = mapValue(item);
            if (map != null) {
                result.add(map);
            }
        }

        return result;
    }

    private String textValue(
            Object value,
            String fallback
    ) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private UUID uuidValue(
            Object value
    ) {
        if (value instanceof UUID uuid) {
            return uuid;
        }

        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildConfirmedUiContractPayload(
            BuildContext context
    ) {
        Map<String, Object> modulePrototypeSnapshot =
                buildModulePrototypeSnapshotPayload(context);

        if (modulePrototypeSnapshot == null || modulePrototypeSnapshot.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> pages =
                mapListValue(modulePrototypeSnapshot.get("pages"));

        List<Map<String, Object>> confirmedPages =
                new ArrayList<>();

        for (Map<String, Object> page : pages) {
            Map<String, Object> uiContract = mapValue(page.get("uiContract"));

            if (!Boolean.TRUE.equals(page.get("hasUiContract"))
                    || uiContract == null
                    || uiContract.isEmpty()) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pageId", uuidValue(page.get("nodeId")));
            item.put("pageKey", textValue(page.get("pageKey"), ""));
            item.put("pageName", textValue(page.get("pageName"), ""));
            item.put("routePath", textValue(page.get("routePath"), ""));
            item.put("artifactId", uuidValue(page.get("artifactId")));
            item.put("artifactVersionNo", page.get("artifactVersionNo"));
            item.put("source", "MODULE_PROTOTYPE_SNAPSHOT");
            item.put("uiContract", uiContract);
            confirmedPages.add(item);
        }

        if (confirmedPages.isEmpty()) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactType", "MODULE_PROTOTYPE_SNAPSHOT");
        payload.put("source", "MODULE_PROTOTYPE_SNAPSHOT");
        payload.put("moduleId", context.selectedNode().getId());
        payload.put("moduleKey", context.selectedNode().getModuleKey());
        payload.put("moduleName", context.selectedNode().getModuleName());
        payload.put("snapshotArtifactId", modulePrototypeSnapshot.get("snapshotArtifactId"));
        payload.put("snapshotVersionNo", modulePrototypeSnapshot.get("snapshotVersionNo"));
        payload.put("partial", modulePrototypeSnapshot.get("partial"));
        payload.put("moduleAppContract", mapValue(modulePrototypeSnapshot.get("moduleAppContract")));
        payload.put("pages", confirmedPages);
        return payload;
    }

    private Map<String, Object> buildThemeProfileSnapshot(
            BuildContext context
    ) {
        Map<String, Object> modulePrototypeSnapshot =
                buildModulePrototypeSnapshotPayload(context);

        if (modulePrototypeSnapshot == null || modulePrototypeSnapshot.isEmpty()) {
            return null;
        }

        Map<String, Object> themeProfile =
                mapValue(modulePrototypeSnapshot.get("projectThemeProfile"));

        if (themeProfile != null && !themeProfile.isEmpty()) {
            return themeProfile;
        }

        return mapValue(modulePrototypeSnapshot.get("themeProfileSnapshot"));
    }


    private Map<String, Object> buildDevelopmentBuildContextPayload(
            BuildContext context,
            DevelopmentHandoffReadinessResponse readiness,
            List<DevelopmentHandoffArtifactPayload> artifacts,
            Map<String, Object> confirmedUiContractPayload,
            Map<String, Object> themeProfileSnapshot
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractVersion", "A9.10.6_DEVELOPMENT_BUILD_CONTEXT_V1");
        payload.put("targetStack", "NODEJS_FULLSTACK");
        payload.put("userFacingMode", "ONE_CLICK_DEVELOPMENT");
        payload.put(
                "goal",
                "把用户确认后的需求、页面原型、业务语义和验收目标组合成 Development Agent 可直接消费的开发上下文。"
        );

        Map<String, Object> project = new LinkedHashMap<>();
        project.put("projectId", context.project().getId());
        project.put("projectCode", context.project().getProjectCode());
        project.put("projectName", context.project().getProjectName());
        payload.put("project", project);

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("nodeId", context.selectedNode().getId());
        scope.put("nodeKey", context.selectedNode().getModuleKey());
        scope.put("nodeName", context.selectedNode().getModuleName());
        scope.put("nodeType", context.nodeType().name());
        scope.put("nodeTypeLabel", nodeTypeLabel(context.nodeType()));
        scope.put("moduleType", context.selectedNode().getModuleType());
        scope.put("directDevelopmentAllowed", context.directDevelopmentAllowed());
        scope.put("scopeSummary", scopeSummary(context));
        scope.put("effectiveModule", buildDevelopmentNodePayload(context.effectiveModule()));
        payload.put("scope", scope);

        Map<String, Object> modulePrototypeSnapshot =
                buildModulePrototypeSnapshotPayload(context);

        if (modulePrototypeSnapshot != null && !modulePrototypeSnapshot.isEmpty()) {
            payload.put("modulePrototypeSnapshot", modulePrototypeSnapshot);
            payload.put("moduleAppContract", mapValue(modulePrototypeSnapshot.get("moduleAppContract")));
        }

        List<Map<String, Object>> pageItems =
                buildDevelopmentBuildContextPages(context);

        Map<String, Object> pages = new LinkedHashMap<>();
        pages.put("count", pageItems.size());
        pages.put("confirmedCount", pageItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("confirmed")))
                .count());
        pages.put("items", pageItems);
        payload.put("pages", pages);

        Map<String, Object> requirements = new LinkedHashMap<>();
        requirements.put("sourceMode", "DYNAMIC_REQUIREMENT_ASSETS");
        requirements.put("artifacts", buildDevelopmentArtifactSummaries(artifacts));
        requirements.put(
                "productRequirements",
                buildDevelopmentArtifactSummaries(
                        artifacts,
                        Set.of(
                                "MODULE_PRD",
                                "PAGE_SPEC",
                                "BUSINESS_FLOW",
                                "DEV_HANDOFF_PACKAGE"
                        )
                )
        );
        requirements.put(
                "domainModel",
                buildDevelopmentArtifactSummaries(artifacts, Set.of("DATA_MODEL"))
        );
        requirements.put(
                "apiBehavior",
                buildDevelopmentArtifactSummaries(artifacts, Set.of("API_CONTRACT"))
        );
        requirements.put(
                "acceptance",
                buildDevelopmentArtifactSummaries(artifacts, Set.of("TEST_SPEC"))
        );
        payload.put("requirements", requirements);

        Map<String, Object> acceptance = new LinkedHashMap<>();
        acceptance.put("ready", readiness != null && readiness.ready());
        acceptance.put("readinessLevel", readiness == null ? null : readiness.readinessLevel());
        acceptance.put("score", readiness == null ? 0 : readiness.score());
        acceptance.put(
                "systemChecks",
                List.of(
                        "页面能打开",
                        "表单能提交",
                        "列表能查询",
                        "数据能新增、编辑、删除",
                        "前端真实调用 Node.js 后端 API",
                        "后端真实读写本地数据层",
                        "npm run build / npm test / acceptance 能跑"
                )
        );
        acceptance.put("blockers", readiness == null ? List.of() : readiness.blockers());
        acceptance.put("warnings", readiness == null ? List.of() : readiness.warnings());
        payload.put("acceptance", acceptance);

        Map<String, Object> sourceTrace = new LinkedHashMap<>();
        sourceTrace.put("createdBy", "DevelopmentHandoffGateService");
        sourceTrace.put("createdAt", LocalDateTime.now());
        sourceTrace.put("artifactVersions", buildDevelopmentSourceArtifactVersions(artifacts));
        sourceTrace.put("uiContractSnapshot", readiness == null ? null : readiness.uiContractSnapshot());
        sourceTrace.put("confirmedUiContractSource", confirmedUiContractPayload == null ? "NONE" : "MODULE_PROTOTYPE_SNAPSHOT");
        payload.put("sourceTrace", sourceTrace);

        Map<String, Object> ux = new LinkedHashMap<>();
        ux.put("principle", "用户只需要确认原型并点击生成，复杂资产聚合由系统自动完成。");
        ux.put(
                "userSteps",
                List.of(
                        "确认模块汇总原型开发基线",
                        "在 Development Agent 手动更新 Node.js 系统代码",
                        "运行构建和验收",
                        "按失败日志自动修复"
                )
        );
        ux.put(
                "hiddenComplexity",
                List.of(
                        "资产版本追踪",
                        "页面契约聚合",
                        "API 和数据语义归并",
                        "Development Agent 上下文裁剪",
                        "构建验收和修复循环"
                )
        );
        payload.put("userExperience", ux);

        if (confirmedUiContractPayload != null) {
            payload.put("confirmedUiContract", confirmedUiContractPayload);
        }
        if (themeProfileSnapshot != null && !themeProfileSnapshot.isEmpty()) {
            payload.put("themeProfileSnapshot", themeProfileSnapshot);
        }

        return payload;
    }

    private Map<String, Object> buildDevelopmentNodePayload(
            ProjectModuleEntity node
    ) {
        if (node == null) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", node.getId());
        payload.put("nodeKey", node.getModuleKey());
        payload.put("nodeName", node.getModuleName());
        payload.put("moduleType", node.getModuleType());
        payload.put("nodeType", nodeGenerationPolicy.resolveNodeType(node).name());
        payload.put("parentId", node.getParentId());
        payload.put("levelNo", node.getLevelNo());
        payload.put("sortOrder", node.getSortOrder());
        return payload;
    }

    private List<Map<String, Object>> buildDevelopmentBuildContextPages(
            BuildContext context
    ) {
        Map<String, Object> modulePrototypeSnapshot =
                buildModulePrototypeSnapshotPayload(context);

        if (modulePrototypeSnapshot == null || modulePrototypeSnapshot.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> snapshotPages =
                mapListValue(modulePrototypeSnapshot.get("pages"));

        List<Map<String, Object>> pages = new ArrayList<>();

        for (Map<String, Object> page : snapshotPages) {
            Map<String, Object> uiContract = mapValue(page.get("uiContract"));
            boolean hasUiContract =
                    Boolean.TRUE.equals(page.get("hasUiContract"))
                            && uiContract != null
                            && !uiContract.isEmpty();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("node", null);
            item.put("pageId", uuidValue(page.get("nodeId")));
            item.put("pageKey", textValue(page.get("pageKey"), ""));
            item.put("pageName", textValue(page.get("pageName"), ""));
            item.put("routePath", textValue(page.get("routePath"), ""));
            item.put("pageNodeType", "PAGE");
            item.put("confirmed", hasUiContract);
            item.put("snapshotId", modulePrototypeSnapshot.get("snapshotArtifactId"));
            item.put("artifactId", uuidValue(page.get("artifactId")));
            item.put("artifactVersionNo", page.get("artifactVersionNo"));
            item.put("status", hasUiContract
                    ? "MODULE_PROTOTYPE_CONFIRMED"
                    : textValue(page.get("status"), "NO_PAGE_UI_CONTRACT"));
            item.put("uiContract", hasUiContract ? uiContract : null);
            item.put("missingReason", hasUiContract ? null : page.get("missingReason"));
            pages.add(item);
        }

        return pages;
    }

    private List<Map<String, Object>> buildDevelopmentArtifactSummaries(
            List<DevelopmentHandoffArtifactPayload> artifacts
    ) {
        return buildDevelopmentArtifactSummaries(artifacts, null);
    }

    private List<Map<String, Object>> buildDevelopmentArtifactSummaries(
            List<DevelopmentHandoffArtifactPayload> artifacts,
            Set<String> artifactTypes
    ) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (DevelopmentHandoffArtifactPayload artifact : artifacts) {
            if (artifact == null) {
                continue;
            }

            String artifactType = safeText(artifact.artifactType());
            if (artifactTypes != null && !artifactTypes.contains(artifactType)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("assetKey", artifact.assetKey());
            item.put("artifactType", artifactType);
            item.put("title", artifact.title());
            item.put("required", artifact.required());
            item.put("sourceScope", artifact.sourceScope());
            item.put("sourceNodeId", artifact.sourceNodeId());
            item.put("sourceNodeKey", artifact.sourceNodeKey());
            item.put("sourceNodeName", artifact.sourceNodeName());
            item.put("sourceNodeType", artifact.sourceNodeType());
            item.put("inherited", artifact.inherited());
            item.put("artifactId", artifact.artifactId());
            item.put("versionNo", artifact.versionNo());
            item.put("status", artifact.status());
            item.put("contentMarkdown", limitText(artifact.contentMarkdown(), 8_000));
            item.put("content", artifact.content());
            result.add(item);
        }

        return result;
    }

    private List<Map<String, Object>> buildDevelopmentSourceArtifactVersions(
            List<DevelopmentHandoffArtifactPayload> artifacts
    ) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (DevelopmentHandoffArtifactPayload artifact : artifacts) {
            if (artifact == null || artifact.artifactId() == null) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("assetKey", artifact.assetKey());
            item.put("artifactType", artifact.artifactType());
            item.put("artifactId", artifact.artifactId());
            item.put("versionNo", artifact.versionNo());
            item.put("status", artifact.status());
            item.put("sourceNodeId", artifact.sourceNodeId());
            item.put("sourceNodeName", artifact.sourceNodeName());
            result.add(item);
        }

        return result;
    }

    private String limitText(
            String value,
            int maxChars
    ) {
        String text = safeText(value);

        if (text.length() <= maxChars) {
            return text;
        }

        return text.substring(0, Math.max(0, maxChars)) + "...";
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
            case PAGE -> "PAGE 不直接生成 Node.js 工程";
            case FEATURE -> "功能点已内联到父页面";
            default -> "当前节点不作为直接开发单元";
        };
    }

    private String notApplicableDescription(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN ->
                    "请选择业务域下的 MODULE 执行模块级开发。";
            case SUBMODULE ->
                    "子模块只组织业务场景和页面。"
                            + "请选择上层 MODULE 作为开发工程入口。";
            case PAGE ->
                    "PAGE 只提供 PAGE_UI_CONTRACT 页面蓝图。"
                            + "请在所属 MODULE 的完整模块预览中确认为开发基线，"
                            + "再由 Development Agent 更新模块级 Node.js 工程。";
            case FEATURE ->
                    "功能点的字段、动作、校验、跳转和异常场景已经归入父 PAGE。"
                            + "请在所属 MODULE 中确认模块汇总原型。";
            default -> "请选择 MODULE 作为正式开发入口。";
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
