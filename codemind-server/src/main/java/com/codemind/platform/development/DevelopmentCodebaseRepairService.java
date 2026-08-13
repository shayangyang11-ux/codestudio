package com.codemind.platform.development;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DevelopmentCodebaseRepairService {

    private static final int MAX_CURRENT_FILES = 70;
    private static final int MAX_CURRENT_FILE_CHARS = 80_000;
    private static final int MAX_TOTAL_CURRENT_CHARS = 900_000;
    private static final int CONTEXT_PATH_HINT_LIMIT = 40; // A9.10.14.4.3.15.4.8.3 acceptance failure / repair context hints
    private static final Set<String> REPAIR_SOURCE_SUFFIXES = Set.of(
            ".js", ".vue", ".css", ".html", ".json", ".md", ".mjs", ".cjs"
    );
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", ".turbo", ".next", "coverage", "logs", ".codemind"
    );

    private final DevelopmentPlanService developmentPlanService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final DevelopmentAiCodegenProposalService aiCodegenProposalService;
    private final DevelopmentCodeProjectService codeProjectService;
    private final DevelopmentCodebaseAcceptanceService acceptanceService;

    public DevelopmentCodebaseRepairService(
            DevelopmentPlanService developmentPlanService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            DevelopmentAiCodegenProposalService aiCodegenProposalService,
            DevelopmentCodeProjectService codeProjectService,
            DevelopmentCodebaseAcceptanceService acceptanceService
    ) {
        this.developmentPlanService = developmentPlanService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.aiCodegenProposalService = aiCodegenProposalService;
        this.codeProjectService = codeProjectService;
        this.acceptanceService = acceptanceService;
    }

    public DevelopmentCodebaseRepairResponse proposeRepair(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodebaseRepairRunRequest request
    ) {
        return buildRepair(workspaceId, projectCode, moduleId, request, false);
    }

    public DevelopmentCodebaseRepairResponse applyRepair(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodebaseRepairRunRequest request
    ) {
        return buildRepair(workspaceId, projectCode, moduleId, request, true);
    }

    private DevelopmentCodebaseRepairResponse buildRepair(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodebaseRepairRunRequest request,
            boolean apply
    ) {
        DevelopmentCodebaseRepairRunRequest safeRequest = request == null
                ? new DevelopmentCodebaseRepairRunRequest(null, false, null, List.of(), Map.of(), null)
                : request;
        String targetStack = resolveTargetStack(safeRequest.targetStack());

        DevelopmentPlanResponse plan = developmentPlanService.generatePlan(
                workspaceId,
                projectCode,
                moduleId,
                new GenerateDevelopmentPlanRequest(
                        null,
                        targetStack,
                        safeText(safeRequest.remarks(), apply ? "ACCEPTANCE_REPAIR_APPLY" : "ACCEPTANCE_REPAIR_PROPOSE")
                )
        );
        DevelopmentHandoffContextResponse context = plan.handoffContext();
        DevelopmentCodebaseRegistryResponse registry = codebaseRegistryService.resolveCodebase(
                workspaceId,
                plan.projectId(),
                projectCode,
                targetStack
        );

        Path root = Path.of(registry.absoluteRootPath()).toAbsolutePath().normalize();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> nextActions = new ArrayList<>();

        if (!registry.registered()) {
            blockers.add("当前代码库尚未登记，不能执行自动修复。请先生成并写入代码。 ");
        }
        if (!Files.isDirectory(root)) {
            blockers.add("代码库物理目录不存在，不能执行自动修复：" + root);
        }

        List<DevelopmentCodebaseAcceptanceItemResponse> failedItems = failedItems(safeRequest.acceptance());
        List<String> logTail = collectLogTail(failedItems, safeRequest.extraLogLines());
        if (failedItems.isEmpty()) {
            blockers.add("没有收到验收失败项。请先运行验收流水线，失败后再生成修复提案。 ");
        }
        if (logTail.isEmpty()) {
            warnings.add("未收到失败日志，修复模型只能根据失败项摘要推断问题。 ");
        }

        List<String> pathHints = collectPathHints(safeRequest.context(), logTail, failedItems);
        List<DevelopmentCodeFileResponse> currentFiles = blockers.isEmpty()
                ? collectCurrentFiles(root, registry.platformRootPath(), warnings, pathHints)
                : List.of();

        if (currentFiles.isEmpty() && blockers.isEmpty()) {
            blockers.add("没有找到可提供给修复模型的当前代码文件。 ");
        }

        if (!blockers.isEmpty()) {
            nextActions.add("先处理阻断项，再重新生成修复提案。 ");
            return response(
                    plan,
                    projectCode,
                    targetStack,
                    registry,
                    "BLOCKED",
                    false,
                    false,
                    null,
                    null,
                    "REPAIR_NOT_READY",
                    failedItems,
                    null,
                    null,
                    null,
                    blockers,
                    warnings,
                    nextActions,
                    "自动修复尚未就绪。"
            );
        }

        String nodeType = safeUpper(plan.nodeType());
        String nodeName = safeText(plan.moduleName(), "当前节点");
        DevelopmentAiCodegenProposalService.AiCodegenResult result = aiCodegenProposalService.repairNodejsCodeContribution(
                workspaceId,
                projectCode,
                moduleId,
                plan,
                context,
                registry.platformRootPath(),
                nodeType,
                nodeName,
                failedItems,
                logTail,
                currentFiles,
                safeRequest.remarks()
        );

        warnings.addAll(result.warnings() == null ? List.of() : result.warnings());
        List<DevelopmentCodeFileResponse> repairFiles = result.files() == null ? List.of() : result.files();
        boolean aiGenerated = result.aiGenerated() && !repairFiles.isEmpty();

        if (!aiGenerated) {
            blockers.add("修复模型未生成可写入文件。 ");
            nextActions.add("查看验收日志，补充错误信息后重新生成修复提案。 ");
        } else {
            nextActions.add(apply ? "修复文件已进入安全写入流程。 " : "检查修复文件列表，确认后点击“应用修复并写入”。 ");
        }

        DevelopmentCodeProjectResponse repairProject = new DevelopmentCodeProjectResponse(
                plan.projectId(),
                plan.moduleId(),
                plan.moduleKey(),
                plan.moduleName(),
                plan.moduleType(),
                plan.nodeType(),
                plan.targetStack(),
                plan.targetStackLabel(),
                result.generatorMode(),
                aiGenerated ? "AI_REPAIR_GENERATED" : "REPAIR_EMPTY",
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                registry.platformRootPath(),
                result.summary(),
                List.of("npm install", "npm run build", "npm test"),
                plan.sourceArtifactTypes(),
                warnings,
                repairFiles,
                plan,
                context,
                codeProjectService.buildCodegenTrace(result, aiGenerated ? "AI_REPAIR_GENERATED" : "REPAIR_EMPTY", repairFiles.size())
        );

        DevelopmentCodeMaterializationResponse materialization = null;
        DevelopmentCodebaseAcceptanceResponse postRepairAcceptance = null;
        boolean applied = false;
        String status = aiGenerated ? "PROPOSED" : "EMPTY";

        if (apply && aiGenerated) {
            materialization = codeProjectService.materializePreparedCodeProject(
                    workspaceId,
                    projectCode,
                    moduleId,
                    repairProject,
                    Boolean.TRUE.equals(safeRequest.allowOverwrite()),
                    "AI_ACCEPTANCE_REPAIR_BEFORE_WRITE"
            );
            applied = true;
            status = materialization.conflictCount() > 0 && !Boolean.TRUE.equals(safeRequest.allowOverwrite())
                    ? "PARTIAL_APPLIED_WITH_CONFLICTS"
                    : "APPLIED";

            if ("APPLIED".equals(status)) {
                try {
                    postRepairAcceptance = acceptanceService.runAcceptance(
                            workspaceId,
                            projectCode,
                            moduleId,
                            new DevelopmentCodebaseAcceptanceRunRequest(
                                    targetStack,
                                    true,
                                    true,
                                    true,
                                    true
                            )
                    );
                    // A9.10.14.4.3.15.4.8.6_FIX_POST_REPAIR_ACCEPTANCE_CALL: use direct acceptanceService.runAcceptance call to avoid helper signature drift.

                    if (postRepairAcceptance != null && postRepairAcceptance.deliverable()) {
                        status = "APPLIED_AND_ACCEPTED";
                        addUnique(nextActions, "修复已写入并通过二次验收，可以进入交付导出或部署配置。 ");
                    } else {
                        status = "APPLIED_BUT_ACCEPTANCE_FAILED";
                        addUnique(nextActions, "修复已写入，但二次验收仍未通过。请基于 postRepairAcceptance.failedItems 再生成下一轮修复。 ");
                    }
                } catch (Exception e) {
                    status = "APPLIED_BUT_ACCEPTANCE_RERUN_FAILED";
                    warnings.add("修复已写入，但自动二次验收启动失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
                    addUnique(nextActions, "手动重新运行验收流水线，确认修复后的 build / database / runtime / test 状态。 ");
                }
            } else {
                addUnique(nextActions, "修复文件存在写入冲突，先处理冲突或允许覆盖后，再运行二次验收。 ");
            }
        }


        // A9.10.14.4.3.15.4.8.6_POST_REPAIR_ACCEPTANCE_CALIBRATION: 二次验收状态校准，必须重点复查原型真实化与 trace 门禁。
        if (apply && applied && postRepairAcceptance != null && !"APPLIED_BUT_ACCEPTANCE_RERUN_FAILED".equals(status)) {
            calibratePostRepairAcceptanceStatus(postRepairAcceptance, warnings, nextActions);
            status = postRepairFocusedAcceptancePassed(postRepairAcceptance)
                    ? "APPLIED_AND_ACCEPTED"
                    : "APPLIED_BUT_ACCEPTANCE_FAILED";
        }
        return response(
                plan,
                projectCode,
                targetStack,
                registry,
                status,
                aiGenerated,
                applied,
                result.provider(),
                result.model(),
                result.generatorMode(),
                failedItems,
                repairProject,
                materialization,
                postRepairAcceptance,
                blockers,
                warnings,
                nextActions,
                result.summary()
        );
    }


    private boolean postRepairFocusedAcceptancePassed(DevelopmentCodebaseAcceptanceResponse acceptance) {
        if (acceptance == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(acceptance.deliverable())) {
            return false;
        }
        if (acceptance.failedCount() > 0) {
            return false;
        }

        List<DevelopmentCodebaseAcceptanceItemResponse> items = acceptance.items() == null
                ? List.of()
                : acceptance.items();
        Set<String> requiredFocusIds = Set.of(
                "PROTOTYPE_FULLSTACK_BINDING_CONTRACT",
                "GENERATED_MANIFEST_TRACE_CONTRACT"
        );
        Set<String> seenFocusIds = new LinkedHashSet<>();

        for (DevelopmentCodebaseAcceptanceItemResponse item : items) {
            if (item == null) {
                continue;
            }
            String id = safeText(item.id(), "").toUpperCase(Locale.ROOT);
            if (requiredFocusIds.contains(id)) {
                seenFocusIds.add(id);
                if (!item.passed()) {
                    return false;
                }
            }
            if ((id.contains("RUNTIME_FIDELITY") || id.contains("FULLSTACK_BINDING") || id.contains("MANIFEST_TRACE")) && !item.passed()) {
                return false;
            }
        }

        return seenFocusIds.containsAll(requiredFocusIds);
    }

    private List<String> postRepairFocusFailures(DevelopmentCodebaseAcceptanceResponse acceptance) {
        List<String> failures = new ArrayList<>();
        Set<String> requiredFocusIds = Set.of(
                "PROTOTYPE_FULLSTACK_BINDING_CONTRACT",
                "GENERATED_MANIFEST_TRACE_CONTRACT"
        );
        Set<String> seenFocusIds = new LinkedHashSet<>();

        if (acceptance == null) {
            return List.of("POST_REPAIR_ACCEPTANCE_NOT_AVAILABLE");
        }

        List<DevelopmentCodebaseAcceptanceItemResponse> items = acceptance.items() == null
                ? List.of()
                : acceptance.items();

        for (DevelopmentCodebaseAcceptanceItemResponse item : items) {
            if (item == null) {
                continue;
            }
            String id = safeText(item.id(), "").toUpperCase(Locale.ROOT);
            if (requiredFocusIds.contains(id)) {
                seenFocusIds.add(id);
            }
            if ((requiredFocusIds.contains(id) || id.contains("RUNTIME_FIDELITY") || id.contains("FULLSTACK_BINDING") || id.contains("MANIFEST_TRACE")) && !item.passed()) {
                failures.add(id + ":" + safeText(item.status(), "FAILED"));
            }
        }

        for (String required : requiredFocusIds) {
            if (!seenFocusIds.contains(required)) {
                failures.add(required + ":MISSING");
            }
        }

        if (!Boolean.TRUE.equals(acceptance.deliverable())) {
            failures.add("DELIVERABLE:false");
        }
        if (acceptance.failedCount() > 0) {
            failures.add("FAILED_COUNT:" + acceptance.failedCount());
        }

        return failures.isEmpty() ? List.of("UNKNOWN_POST_REPAIR_ACCEPTANCE_FOCUS_FAILURE") : failures;
    }

    private void calibratePostRepairAcceptanceStatus(
            DevelopmentCodebaseAcceptanceResponse postRepairAcceptance,
            List<String> warnings,
            List<String> nextActions
    ) {
        if (postRepairFocusedAcceptancePassed(postRepairAcceptance)) {
            addUnique(warnings, "A9.10.14.4.3.15.4.8.6_POST_REPAIR_FOCUSED_ACCEPTANCE_PASSED");
            return;
        }

        List<String> failures = postRepairFocusFailures(postRepairAcceptance);
        addUnique(warnings, "A9.10.14.4.3.15.4.8.6_POST_REPAIR_FOCUSED_ACCEPTANCE_FAILED=" + String.join(",", failures));
        addUnique(nextActions, "优先修复 postRepairAcceptance 中的 PROTOTYPE_FULLSTACK_BINDING_CONTRACT 与 GENERATED_MANIFEST_TRACE_CONTRACT。");
    }

    private DevelopmentCodebaseRepairResponse response(
            DevelopmentPlanResponse plan,
            String projectCode,
            String targetStack,
            DevelopmentCodebaseRegistryResponse registry,
            String status,
            boolean aiGenerated,
            boolean applied,
            String provider,
            String model,
            String generatorMode,
            List<DevelopmentCodebaseAcceptanceItemResponse> failedItems,
            DevelopmentCodeProjectResponse repairProject,
            DevelopmentCodeMaterializationResponse materialization,
            DevelopmentCodebaseAcceptanceResponse postRepairAcceptance,
            List<String> blockers,
            List<String> warnings,
            List<String> nextActions,
            String summary
    ) {
        return new DevelopmentCodebaseRepairResponse(
                plan.projectId(),
                plan.moduleId(),
                plan.moduleKey(),
                plan.moduleName(),
                projectCode,
                targetStack,
                status,
                aiGenerated,
                applied,
                provider,
                model,
                generatorMode,
                registry.platformRootPath(),
                registry.absoluteRootPath(),
                failedItems == null ? 0 : failedItems.size(),
                repairProject == null || repairProject.files() == null ? 0 : repairProject.files().size(),
                LocalDateTime.now(),
                safeText(summary, "验收失败自动修复处理完成。"),
                dedupe(blockers),
                dedupe(warnings),
                dedupe(nextActions),
                failedItems == null ? List.of() : failedItems,
                repairProject,
                materialization,
                postRepairAcceptance
        );
    }

    private List<DevelopmentCodebaseAcceptanceItemResponse> failedItems(DevelopmentCodebaseAcceptanceResponse acceptance) {
        if (acceptance == null || acceptance.items() == null) {
            return List.of();
        }
        return acceptance.items().stream()
                .filter(item -> item != null && (!item.passed() || isFailureStatus(item.status())))
                .limit(12)
                .toList();
    }

    private boolean isFailureStatus(String status) {
        String value = safeUpper(status);
        return value.equals("FAILED") || value.equals("TIMEOUT") || value.equals("NOT_READY");
    }

    private List<String> collectLogTail(
            List<DevelopmentCodebaseAcceptanceItemResponse> failedItems,
            List<String> extraLines
    ) {
        List<String> lines = new ArrayList<>();
        if (failedItems != null) {
            for (DevelopmentCodebaseAcceptanceItemResponse item : failedItems) {
                lines.add("## " + item.title() + " [" + item.status() + "]");
                if (item.summary() != null) {
                    lines.add(item.summary());
                }
                lines.addAll(buildAcceptanceFailureDiagnosticLines(item));
                if (item.logTail() != null) {
                    lines.addAll(item.logTail());
                }
            }
        }
        if (extraLines != null) {
            lines.addAll(extraLines.stream().filter(item -> item != null && !item.isBlank()).toList());
        }
        int from = Math.max(0, lines.size() - 240);
        return new ArrayList<>(lines.subList(from, lines.size()));
    }

    private List<String> collectPathHints(
            Map<String, Object> context,
            List<String> logTail,
            List<DevelopmentCodebaseAcceptanceItemResponse> failedItems
    ) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        addDefaultFailurePathHints(hints, failedItems);
        addHintValue(hints, context == null ? null : context.get("selectedFilePath"));
        addHintValue(hints, context == null ? null : context.get("currentFilePath"));
        addHintValue(hints, context == null ? null : context.get("nodeFilePaths"));
        addHintValue(hints, context == null ? null : context.get("backendFilePaths"));
        addHintValue(hints, context == null ? null : context.get("dataFilePaths"));
        addHintValue(hints, context == null ? null : context.get("traceFilePaths"));
        addHintValue(hints, context == null ? null : context.get("logDetectedFilePaths"));

        if (logTail != null) {
            for (String line : logTail) {
                addPathLikeTokens(hints, line);
            }
        }

        // A9.10.14.4.3.15.4.8.3_MAPPING_AWARE_REPAIR_LOOP_V2: focus repair context on files that turn mock prototype contracts into real fullstack bindings.
        if (hasPrototypeFullstackBindingFailure(failedItems, logTail)) {
            hints.add("backend/src/modules/generated/generatedResources.js");
            hints.add("backend/src/modules/generated/generatedManifestTrace.js");
            hints.add("database/generated-trace.json");
            hints.add("frontend/src/modules/generated/generatedResources.js");
            hints.add("frontend/src/modules/generated/pages");
            hints.add("frontend/src/modules/generated/api");
            hints.add("backend/src/modules/generated/generated-resource.routes.js");
            hints.add("backend/src/modules/generated/generated-resource.service.js");
            hints.add("backend/src/modules/generated/generated-resource.repository.js");
            hints.add("backend/src/routes/index.js");
            hints.add("backend/src/db/localStore.js");
            hints.add("backend/src/db/seedData.js");
            hints.add("tests/smoke.spec.js");
            hints.add("database/schema.json");
            hints.add("database/seed.json");
        }

        return hints.stream().limit(CONTEXT_PATH_HINT_LIMIT).toList();
    }


    private List<String> buildAcceptanceFailureDiagnosticLines(
            DevelopmentCodebaseAcceptanceItemResponse item
    ) {
        if (item == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        String id = safeUpper(item.id());
        String phase = safeUpper(item.phase());
        String status = safeUpper(item.status());
        String failureType = classifyAcceptanceFailure(item);
        List<String> likelyFiles = defaultFailurePathHints(item);

        lines.add("[CODEMIND_ACCEPTANCE_DIAGNOSTIC] id=" + id
                + "; phase=" + phase
                + "; status=" + status
                + "; failureType=" + failureType);
        lines.add("[CODEMIND_ACCEPTANCE_REPAIR_FOCUS] " + repairFocusForFailureType(failureType));

        if (item.command() != null && !item.command().isBlank()) {
            lines.add("[CODEMIND_ACCEPTANCE_COMMAND] " + item.command());
        }

        if (item.exitCode() != null) {
            lines.add("[CODEMIND_ACCEPTANCE_EXIT_CODE] " + item.exitCode());
        }

        if (!likelyFiles.isEmpty()) {
            lines.add("[CODEMIND_ACCEPTANCE_LIKELY_FILES] " + String.join(", ", likelyFiles));
        }

        return lines;
    }

    private String classifyAcceptanceFailure(
            DevelopmentCodebaseAcceptanceItemResponse item
    ) {
        if (item == null) {
            return "UNKNOWN_ACCEPTANCE_FAILURE";
        }

        String id = safeUpper(item.id());
        String phase = safeUpper(item.phase());
        String summary = safeUpper(item.summary());
        String command = safeUpper(item.command());
        String logs = safeUpper(String.join("\n", item.logTail() == null ? List.of() : item.logTail()));

        if (id.contains("NODE_NPM") || id.contains("NPM_AVAILABLE") || command.contains("NODE -V") || command.contains("NPM -V")) {
            return "NODE_ENVIRONMENT_UNAVAILABLE";
        }
        if (id.contains("PACKAGE_JSON") || logs.contains("MISSING SCRIPT") || logs.contains("缺少") && logs.contains("PACKAGE.JSON")) {
            return "PROJECT_MANIFEST_OR_PACKAGE_MISSING";
        }
        if (id.contains("NPM_INSTALL") || "INSTALL".equals(phase)) {
            if (logs.contains("ERESOLVE")) {
                return "NPM_DEPENDENCY_RESOLUTION_FAILED";
            }
            if (logs.contains("ETIMEDOUT") || logs.contains("ECONNRESET") || logs.contains("NETWORK")) {
                return "NPM_NETWORK_OR_REGISTRY_FAILED";
            }
            return "NPM_INSTALL_FAILED";
        }
        if (id.contains("NPM_BUILD") || "BUILD".equals(phase)) {
            if (logs.contains("SYNTAXERROR")) {
                return "FRONTEND_BUILD_SYNTAX_ERROR";
            }
            if (logs.contains("MODULE NOT FOUND") || logs.contains("CANNOT FIND MODULE") || logs.contains("FAILED TO RESOLVE IMPORT")) {
                return "FRONTEND_IMPORT_OR_MODULE_RESOLUTION_FAILED";
            }
            if (logs.contains(".VUE") || logs.contains("VITE") || logs.contains("ROLLUP")) {
                return "FRONTEND_VITE_OR_VUE_BUILD_FAILED";
            }
            return "NPM_BUILD_FAILED";
        }
        if (id.contains("DATABASE_LOCAL_STORE")
                || id.contains("DATABASE_SEED")
                || id.contains("DATABASE_SCHEMA")
                || id.contains("DATABASE_READ_WRITE_PROBE")
                || "DATABASE".equals(phase)) {
            if (logs.contains("EACCES") || logs.contains("PERMISSION")) {
                return "DATABASE_STORE_PERMISSION_FAILED";
            }
            if (logs.contains("ENOENT") || logs.contains("NO SUCH FILE")) {
                return "DATABASE_STORE_FILE_MISSING";
            }
            if (logs.contains("READSTORE") || logs.contains("WRITESTORE") || logs.contains("DATABASE WRITE/READ")) {
                return "DATABASE_LOCAL_STORE_READ_WRITE_FAILED";
            }
            return "DATABASE_ACCEPTANCE_FAILED";
        }
        // A9.10.14.4.3.15.4.8.3_MAPPING_AWARE_REPAIR_LOOP: prototype contract -> real fullstack binding repair classification.
        if (id.contains("PROTOTYPE_FULLSTACK_BINDING_CONTRACT")
                || logs.contains("FULLSTACKBINDING")
                || logs.contains("GENERATEDRESOURCES")
                || logs.contains("ACTIONBINDINGS")
                || logs.contains("DATABINDINGS")
                || logs.contains("RUNUIACTION")
                || logs.contains("FETCHUIROWS")
                || logs.contains("GENERATED-RESOURCE")) {
            return "PROTOTYPE_FULLSTACK_BINDING_FAILED";
        }

        if (id.contains("RUNTIME_API_DATABASE_SMOKE")
                || id.contains("BACKEND_HEALTH")
                || id.contains("FRONTEND_PREVIEW")
                || "RUNTIME".equals(phase)
                || "HEALTH".equals(phase)) {
            if (logs.contains("EADDRINUSE")) {
                return "RUNTIME_PORT_IN_USE";
            }
            if (logs.contains("ECONNREFUSED") || summary.contains("健康检查未通过")) {
                return "RUNTIME_BACKEND_NOT_REACHABLE";
            }
            if (logs.contains("POST") || logs.contains("GET") || logs.contains("DELETE")) {
                return "RUNTIME_API_DATABASE_ROUND_TRIP_FAILED";
            }
            return "RUNTIME_OR_HEALTH_CHECK_FAILED";
        }
        if (id.contains("NPM_TEST") || "TEST".equals(phase)) {
            if (logs.contains("ASSERT") || logs.contains("EXPECTED") || logs.contains("RECEIVED")) {
                return "SMOKE_TEST_ASSERTION_FAILED";
            }
            return "NPM_TEST_FAILED";
        }

        if (command.contains("NPM INSTALL")) {
            return "NPM_INSTALL_FAILED";
        }
        if (command.contains("NPM RUN BUILD")) {
            return "NPM_BUILD_FAILED";
        }
        if (command.contains("NPM TEST")) {
            return "NPM_TEST_FAILED";
        }

        return "UNKNOWN_ACCEPTANCE_FAILURE";
    }

    private String repairFocusForFailureType(
            String failureType
    ) {
        return switch (safeUpper(failureType)) {
            case "NODE_ENVIRONMENT_UNAVAILABLE" -> "修复运行环境：确认 Node.js / npm 已安装并加入 PATH。";
            case "PROJECT_MANIFEST_OR_PACKAGE_MISSING" -> "修复项目主干：补齐 package.json、workspace scripts、codemind.generated.json。";
            case "NPM_DEPENDENCY_RESOLUTION_FAILED", "NPM_INSTALL_FAILED" -> "修复依赖声明：检查根 package.json、frontend/package.json、backend/package.json 的依赖和版本。";
            case "NPM_NETWORK_OR_REGISTRY_FAILED" -> "修复 npm 源、网络代理或离线依赖策略。";
            case "FRONTEND_BUILD_SYNTAX_ERROR", "FRONTEND_IMPORT_OR_MODULE_RESOLUTION_FAILED", "FRONTEND_VITE_OR_VUE_BUILD_FAILED", "NPM_BUILD_FAILED" -> "修复前端构建：优先处理日志首个 Vue/Vite/import/语法错误。";
            case "DATABASE_STORE_PERMISSION_FAILED", "DATABASE_STORE_FILE_MISSING", "DATABASE_LOCAL_STORE_READ_WRITE_FAILED", "DATABASE_ACCEPTANCE_FAILED" -> "修复数据层：检查 backend/src/db/localStore.js、seedData.js、database/schema.json 和 data/local-store.json 写入逻辑。";
            case "RUNTIME_PORT_IN_USE" -> "修复运行端口：释放 5178 / 3101 或调整生成项目端口。";
            case "PROTOTYPE_FULLSTACK_BINDING_FAILED" -> "修复原型真实化绑定：检查 backend/src/modules/generated/generatedResources.js、frontend generated pages/api、backend generated resource routes/service/repository、localStore 和 tests/smoke.spec.js，确保 fullstackBinding 中的 actionId / apiSegment / fieldKey 被真实实现。";
            case "RUNTIME_BACKEND_NOT_REACHABLE", "RUNTIME_API_DATABASE_ROUND_TRIP_FAILED", "RUNTIME_OR_HEALTH_CHECK_FAILED" -> "修复后端运行链路：检查 backend/server.js、app.js、routes/index.js、response.js、localStore.js。";
            case "SMOKE_TEST_ASSERTION_FAILED", "NPM_TEST_FAILED" -> "修复冒烟测试链路：检查 tests/smoke.spec.js 与前后端 API 合同是否一致。";
            default -> "根据失败项、命令、退出码和最近日志，从首个错误开始修复。";
        };
    }

    private void addDefaultFailurePathHints(
            LinkedHashSet<String> hints,
            List<DevelopmentCodebaseAcceptanceItemResponse> failedItems
    ) {
        if (hints == null || failedItems == null) {
            return;
        }

        for (DevelopmentCodebaseAcceptanceItemResponse item : failedItems) {
            for (String hint : defaultFailurePathHints(item)) {
                if (hint != null && !hint.isBlank()) {
                    hints.add(hint);
                }
            }
        }
    }

    private List<String> defaultFailurePathHints(
            DevelopmentCodebaseAcceptanceItemResponse item
    ) {
        if (item == null) {
            return List.of();
        }

        String failureType = classifyAcceptanceFailure(item);
        String id = safeUpper(item.id());
        String phase = safeUpper(item.phase());
        LinkedHashSet<String> hints = new LinkedHashSet<>();

        if ("PROJECT_MANIFEST_OR_PACKAGE_MISSING".equals(failureType)
                || id.contains("CODEBASE")
                || id.contains("MANIFEST")
                || id.contains("PACKAGE_JSON")) {
            hints.add("package.json");
            hints.add("codemind.generated.json");
        }

        if (failureType.startsWith("NPM_")
                || "INSTALL".equals(phase)
                || "BUILD".equals(phase)
                || "TEST".equals(phase)) {
            hints.add("package.json");
            hints.add("frontend/package.json");
            hints.add("backend/package.json");
        }

        if (failureType.contains("FRONTEND")
                || "BUILD".equals(phase)) {
            hints.add("frontend/vite.config.js");
            hints.add("frontend/src/main.js");
            hints.add("frontend/src/App.vue");
            hints.add("frontend/src/router/routes.js");
            hints.add("frontend/src/shared/styles/app.css");
        }

        if (failureType.contains("DATABASE")
                || "DATABASE".equals(phase)
                || id.contains("DATABASE")) {
            hints.add("backend/src/db/localStore.js");
            hints.add("backend/src/db/seedData.js");
            hints.add("database/schema.json");
            hints.add("database/seed.json");
        }

        if (failureType.contains("RUNTIME")
                || failureType.contains("BACKEND")
                || "HEALTH".equals(phase)
                || id.contains("BACKEND_HEALTH")
                || id.contains("RUNTIME_API_DATABASE_SMOKE")) {
            hints.add("backend/server.js");
            hints.add("backend/src/app.js");
            hints.add("backend/src/routes/index.js");
            hints.add("backend/src/shared/response.js");
            hints.add("backend/src/db/localStore.js");
        }

        if (failureType.contains("TEST")
                || "TEST".equals(phase)
                || id.contains("NPM_TEST")) {
            hints.add("tests/smoke.spec.js");
            hints.add("backend/server.js");
            hints.add("backend/src/routes/index.js");
            hints.add("frontend/src/App.vue");
        }

        return hints.stream().limit(18).toList();
    }

    // A9.10.14.4.3.15.4.8.3_MAPPING_AWARE_REPAIR_LOOP_V2: detect prototype contract -> real fullstack binding failures from acceptance items/logs.
    private boolean hasPrototypeFullstackBindingFailure(
            List<DevelopmentCodebaseAcceptanceItemResponse> failedItems,
            List<String> logTail
    ) {
        if (failedItems != null) {
            for (DevelopmentCodebaseAcceptanceItemResponse item : failedItems) {
                if (item == null) {
                    continue;
                }
                String details = item.details() == null ? "" : String.join(" ", item.details());
                String logs = item.logTail() == null ? "" : String.join(" ", item.logTail());
                String value = safeUpper(
                        item.id() + " "
                                + item.phase() + " "
                                + item.title() + " "
                                + item.summary() + " "
                                + details + " "
                                + logs
                );
                if (isPrototypeFullstackBindingSignal(value)) {
                    return true;
                }
            }
        }

        if (logTail != null) {
            for (String line : logTail) {
                if (isPrototypeFullstackBindingSignal(safeUpper(line))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPrototypeFullstackBindingSignal(String value) {
        String text = safeUpper(value);
        return text.contains("PROTOTYPE_FULLSTACK_BINDING_CONTRACT")
                || text.contains("GENERATED_MANIFEST_TRACE_CONTRACT")
                || text.contains("GENERATEDMANIFESTTRACE")
                || text.contains("GENERATEDTRACE")
                || text.contains("SMOKECASE")
                || text.contains("LOCALSTOREBUCKET")
                || text.contains("FULLSTACKBINDING")
                || text.contains("GENERATEDRESOURCES")
                || text.contains("ACTIONBINDINGS")
                || text.contains("DATABINDINGS")
                || text.contains("RUNUIACTION")
                || text.contains("FETCHUIROWS")
                || text.contains("GENERATED-RESOURCE")
                || text.contains("APISEGMENT")
                || text.contains("ACTIONID")
                || text.contains("FIELDKEY");
    }

    private void addHintValue(LinkedHashSet<String> hints, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addHintValue(hints, item);
            }
            return;
        }
        if (value instanceof Object[] array) {
            for (Object item : array) {
                addHintValue(hints, item);
            }
            return;
        }

        addPathLikeTokens(hints, String.valueOf(value));
    }

    private void addPathLikeTokens(LinkedHashSet<String> hints, String source) {
        if (source == null || source.isBlank()) {
            return;
        }

        String[] tokens = source.replace('\u0000', ' ').split("\\s+");
        for (String token : tokens) {
            String value = token == null ? "" : token.trim();
            if (value.isBlank()) {
                continue;
            }

            value = value
                    .replaceAll("^[`'\\\"(\\[]+", "")
                    .replaceAll("[`'\\\"),;\\]]+$", "")
                    .replace('\\', '/')
                    .replaceAll(":(\\d+)(:\\d+)?$", "");

            String lower = value.toLowerCase(Locale.ROOT);
            boolean sourceLike = REPAIR_SOURCE_SUFFIXES.stream().anyMatch(lower::endsWith);
            boolean projectPathLike = value.contains("/") && (
                    value.contains("frontend/") ||
                            value.contains("backend/") ||
                            value.contains("database/") ||
                            value.contains("tests/") ||
                            value.contains("package.json") ||
                            value.contains("codemind.generated.json")
            );

            if (sourceLike && projectPathLike) {
                hints.add(value);
            }
        }
    }

    private List<DevelopmentCodeFileResponse> collectCurrentFiles(
            Path root,
            String platformRoot,
            List<String> warnings,
            List<String> pathHints
    ) {
        List<Path> paths = new ArrayList<>();
        Set<Path> seenPaths = new LinkedHashSet<>();

        if (pathHints != null) {
            for (String hint : pathHints) {
                Path hintedPath = resolveHintPath(root, platformRoot, hint);
                if (
                        hintedPath != null &&
                                Files.isRegularFile(hintedPath) &&
                                hintedPath.startsWith(root) &&
                                !isExcluded(root, hintedPath) &&
                                isRepairSourceFile(hintedPath) &&
                                seenPaths.add(hintedPath)
                ) {
                    paths.add(hintedPath);
                }
            }
        }

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(root, path))
                    .filter(this::isRepairSourceFile)
                    .sorted(Comparator.comparing(this::priority).thenComparing(path -> root.relativize(path).toString()))
                    .forEach(path -> {
                        Path normalized = path.toAbsolutePath().normalize();
                        if (seenPaths.add(normalized)) {
                            paths.add(normalized);
                        }
                    });
        } catch (IOException e) {
            warnings.add("读取代码库当前文件失败：" + e.getMessage());
            return List.of();
        }

        List<DevelopmentCodeFileResponse> result = new ArrayList<>();
        int totalChars = 0;
        String normalizedRoot = normalizePath(platformRoot);
        for (Path path : paths) {
            if (result.size() >= MAX_CURRENT_FILES) {
                break;
            }

            String content = readLimited(path);
            totalChars += content.length();
            if (totalChars > MAX_TOTAL_CURRENT_CHARS) {
                warnings.add("修复上下文文件总量已达到上限，后续文件未发送给模型。 ");
                break;
            }
            String relative = normalizePath(root.relativize(path).toString());
            result.add(new DevelopmentCodeFileResponse(
                    normalizedRoot + "/" + relative,
                    fileType(relative),
                    "CREATE_OR_UPDATE",
                    "CODEBASE_REPAIR_CONTEXT",
                    "验收失败自动修复上下文文件。",
                    language(relative),
                    content,
                    priority(path)
            ));
        }
        return result;
    }

    private Path resolveHintPath(Path root, String platformRoot, String hint) {
        if (hint == null || hint.isBlank()) {
            return null;
        }

        try {
            Path direct = Path.of(hint).toAbsolutePath().normalize();
            if (direct.startsWith(root)) {
                return direct;
            }
        } catch (Exception ignored) {
            // Continue with platform / relative generated path normalization.
        }

        String normalized = hint.replace('\\', '/').trim()
                .replaceAll("^[`'\\\"(\\[]+", "")
                .replaceAll("[`'\\\"),;\\]]+$", "")
                .replaceAll(":(\\d+)(:\\d+)?$", "");

        String rootKey = normalizePath(platformRoot);
        int rootIndex = normalized.indexOf(rootKey);
        if (rootIndex >= 0) {
            normalized = normalized.substring(rootIndex + rootKey.length());
        }

        int generatedIndex = normalized.indexOf("generated/");
        if (generatedIndex >= 0 && rootIndex < 0) {
            String generatedPart = normalized.substring(generatedIndex);
            int nestedRootIndex = generatedPart.indexOf(rootKey);
            if (nestedRootIndex >= 0) {
                normalized = generatedPart.substring(nestedRootIndex + rootKey.length());
            }
        }

        normalized = normalized.replaceAll("^/+", "");

        for (String token : List.of("frontend/", "backend/", "database/", "tests/")) {
            int tokenIndex = normalized.indexOf(token);
            if (tokenIndex >= 0) {
                normalized = normalized.substring(tokenIndex);
                break;
            }
        }

        if (normalized.isBlank()) {
            return null;
        }

        try {
            Path candidate = root.resolve(normalized).toAbsolutePath().normalize();
            return candidate.startsWith(root) ? candidate : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isExcluded(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (EXCLUDED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRepairSourceFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return REPAIR_SOURCE_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private int priority(Path path) {
        String value = normalizePath(path.toString()).toLowerCase(Locale.ROOT);
        if (value.endsWith("package.json")) return 1;
        if (value.contains("/frontend/src/") || value.contains("\\frontend\\src\\")) return 10;
        if (value.contains("/backend/src/") || value.contains("\\backend\\src\\")) return 20;
        if (value.contains("/tests/")) return 30;
        return 100;
    }

    private String readLimited(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.length() <= MAX_CURRENT_FILE_CHARS
                    ? content
                    : content.substring(0, MAX_CURRENT_FILE_CHARS);
        } catch (Exception e) {
            return "/* READ_ERROR: " + e.getMessage() + " */";
        }
    }

    private String fileType(String relative) {
        String value = relative.toLowerCase(Locale.ROOT);
        if (value.startsWith("frontend/")) return "FRONTEND";
        if (value.startsWith("backend/")) return "BACKEND";
        if (value.startsWith("database/")) return "DATABASE";
        if (value.startsWith("tests/")) return "TEST";
        if (value.endsWith(".md")) return "DOC";
        return "CONFIG";
    }

    private String language(String relative) {
        String value = relative.toLowerCase(Locale.ROOT);
        if (value.endsWith(".vue")) return "vue";
        if (value.endsWith(".js") || value.endsWith(".mjs") || value.endsWith(".cjs")) return "javascript";
        if (value.endsWith(".css")) return "css";
        if (value.endsWith(".json")) return "json";
        if (value.endsWith(".html")) return "html";
        if (value.endsWith(".md")) return "markdown";
        return "text";
    }

    private String resolveTargetStack(String targetStack) {
        try {
            return DevelopmentTargetStack.resolve(targetStack).stackCode();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private String normalizePath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }


    private void addUnique(List<String> target, String value) {
        if (target == null || value == null || value.isBlank()) {
            return;
        }

        String trimmed = value.trim();
        if (!target.contains(trimmed)) {
            target.add(trimmed);
        }
    }

    private List<String> dedupe(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
        }
        return new ArrayList<>(result);
    }
}