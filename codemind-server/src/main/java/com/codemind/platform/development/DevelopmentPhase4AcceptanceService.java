package com.codemind.platform.development;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DevelopmentPhase4AcceptanceService {

    private final DevelopmentHandoffGateService developmentHandoffGateService;
    private final ConfirmedDevelopmentPlanSnapshotService confirmedPlanSnapshotService;

    public DevelopmentPhase4AcceptanceService(
            DevelopmentHandoffGateService developmentHandoffGateService,
            ConfirmedDevelopmentPlanSnapshotService confirmedPlanSnapshotService
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
        this.confirmedPlanSnapshotService = confirmedPlanSnapshotService;
    }

    @Transactional(readOnly = true)
    public DevelopmentPhase4AcceptanceResponse getAcceptance(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        DevelopmentHandoffReadinessResponse readiness =
                developmentHandoffGateService.getReadiness(
                        workspaceId,
                        projectCode,
                        moduleId
                );

        Optional<ConfirmedDevelopmentPlanSnapshotResponse> planSnapshot =
                confirmedPlanSnapshotService.getCurrent(
                        workspaceId,
                        projectCode,
                        moduleId
                );

        boolean assetsReady = readiness.ready();
        boolean uiContractConfirmed = isUiContractAccepted(readiness);
        boolean planConfirmed = isPlanConfirmed(planSnapshot);
        boolean handoffPackageReady = assetsReady && planConfirmed;

        List<DevelopmentPhase4AcceptanceItem> items = new ArrayList<>();

        items.add(
                new DevelopmentPhase4AcceptanceItem(
                        "formal-assets",
                        "正式资产与开发门禁",
                        assetsReady ? "PASS" : "BLOCKED",
                        assetsReady ? "已通过" : "存在开发交接阻断项",
                        "检查当前节点必要资产是否存在、内容是否可用、状态是否允许进入 Development Agent 交接。",
                        true,
                        assetsReady
                )
        );

        boolean pageNode = "PAGE".equalsIgnoreCase(safeText(readiness.nodeType()));

        items.add(
                new DevelopmentPhase4AcceptanceItem(
                        "ui-contract-confirmation",
                        "UI_CONTRACT 用户确认",
                        uiContractConfirmed ? "PASS" : pageNode ? "BLOCKED" : "SKIPPED",
                        pageNode
                                ? uiContractConfirmed ? "页面原型已确认" : "页面原型尚未确认或已滞后"
                                : "非 PAGE 节点不要求 UI_CONTRACT 确认",
                        "PAGE 节点必须以用户确认后的 UI_CONTRACT 快照作为后续真实页面代码蓝图；非 PAGE 节点该项不作为阻断项。",
                        pageNode,
                        uiContractConfirmed
                )
        );

        items.add(
                new DevelopmentPhase4AcceptanceItem(
                        "development-plan-confirmation",
                        "Development Plan 确认快照",
                        planConfirmed ? "PASS" : "BLOCKED",
                        planConfirmed ? "已确认" : "尚未确认开发实施计划",
                        "Development Plan 必须由用户确认后，后续代码生成 Agent 才能读取该计划作为施工依据。",
                        true,
                        planConfirmed
                )
        );

        items.add(
                new DevelopmentPhase4AcceptanceItem(
                        "development-handoff-package",
                        "Development Handoff Package",
                        handoffPackageReady ? "READY" : "BLOCKED",
                        handoffPackageReady ? "已满足生成条件" : "尚未满足生成条件",
                        "交接包基于已确认 Development Plan、开发上下文、资产版本清单和 UI_CONTRACT 快照生成。当前接口为无状态生成，前端生成后会展示交接包摘要。",
                        true,
                        handoffPackageReady
                )
        );

        List<String> blockers = buildBlockers(
                readiness,
                pageNode,
                uiContractConfirmed,
                planConfirmed,
                handoffPackageReady
        );

        int requiredCount = 0;
        int passedRequiredCount = 0;

        for (DevelopmentPhase4AcceptanceItem item : items) {
            if (!item.required()) {
                continue;
            }

            requiredCount++;

            if (item.passed()) {
                passedRequiredCount++;
            }
        }

        int completionPercent =
                requiredCount == 0
                        ? 100
                        : Math.round((passedRequiredCount * 100.0f) / requiredCount);

        boolean readyForNextPhase = blockers.isEmpty();

        String completionLevel =
                readyForNextPhase
                        ? "PHASE_4_ACCEPTED"
                        : completionPercent >= 60 ? "PARTIAL" : "BLOCKED";

        List<String> nextPhases = List.of(
                "Phase 5：Assistant 对话式改需求",
                "Phase 6：ModuleTreePatchAgent 正式接入",
                "Phase 7：文件 / 图片 / 甲方材料驱动需求变更",
                "Phase 8：影响分析 + 选择性重生成",
                "Phase 9：Development Agent Handoff Package / 代码生成接入"
        );

        String summaryText = readyForNextPhase
                ? "Phase 4 的 UI_CONTRACT、用户确认、开发计划确认与开发交接包准备链路已经闭合。可以执行 Git 提交，并进入 Phase 5。"
                : "Phase 4 仍存在未完成项。请先处理阻断项，再提交本阶段代码。";

        return new DevelopmentPhase4AcceptanceResponse(
                readiness.projectId(),
                readiness.moduleId(),
                readiness.moduleKey(),
                readiness.moduleName(),
                readiness.moduleType(),
                readiness.nodeType(),
                "PHASE_4",
                "UI_CONTRACT 与 Development Handoff 基础设施验收",
                readyForNextPhase,
                completionLevel,
                completionPercent,
                LocalDateTime.now(),
                items,
                blockers,
                nextPhases,
                summaryText
        );
    }

    private boolean isUiContractAccepted(
            DevelopmentHandoffReadinessResponse readiness
    ) {
        if (!"PAGE".equalsIgnoreCase(safeText(readiness.nodeType()))) {
            return true;
        }

        DevelopmentUiContractSnapshotSummary snapshot = readiness.uiContractSnapshot();

        return snapshot != null
                && snapshot.exists()
                && snapshot.matchesCurrentArtifact();
    }

    private boolean isPlanConfirmed(
            Optional<ConfirmedDevelopmentPlanSnapshotResponse> snapshot
    ) {
        return snapshot
                .filter(ConfirmedDevelopmentPlanSnapshotResponse::current)
                .filter(item -> "CONFIRMED".equalsIgnoreCase(safeText(item.status())))
                .filter(item -> !safeText(item.planChecksum()).isBlank())
                .isPresent();
    }

    private List<String> buildBlockers(
            DevelopmentHandoffReadinessResponse readiness,
            boolean pageNode,
            boolean uiContractConfirmed,
            boolean planConfirmed,
            boolean handoffPackageReady
    ) {
        List<String> blockers = new ArrayList<>();

        for (DevelopmentHandoffIssueResponse issue : readiness.blockers()) {
            blockers.add(issue.title() + "：" + issue.description());
        }

        if (pageNode && !uiContractConfirmed) {
            blockers.add("PAGE 节点尚未完成 UI_CONTRACT 用户确认，或确认快照已滞后。");
        }

        if (!planConfirmed) {
            blockers.add("Development Plan 尚未由用户确认。");
        }

        if (!handoffPackageReady) {
            blockers.add("Development Handoff Package 尚未满足生成条件。");
        }

        return blockers;
    }

    private String safeText(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }
}
