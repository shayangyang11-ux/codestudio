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

    public DevelopmentCodebaseRepairService(
            DevelopmentPlanService developmentPlanService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            DevelopmentAiCodegenProposalService aiCodegenProposalService,
            DevelopmentCodeProjectService codeProjectService
    ) {
        this.developmentPlanService = developmentPlanService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.aiCodegenProposalService = aiCodegenProposalService;
        this.codeProjectService = codeProjectService;
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

        List<DevelopmentCodeFileResponse> currentFiles = blockers.isEmpty()
                ? collectCurrentFiles(root, registry.platformRootPath(), warnings)
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
                blockers,
                warnings,
                nextActions,
                result.summary()
        );
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
                materialization
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

    private List<DevelopmentCodeFileResponse> collectCurrentFiles(
            Path root,
            String platformRoot,
            List<String> warnings
    ) {
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(root, path))
                    .filter(this::isRepairSourceFile)
                    .sorted(Comparator.comparing(this::priority).thenComparing(path -> root.relativize(path).toString()))
                    .limit(MAX_CURRENT_FILES)
                    .forEach(paths::add);
        } catch (IOException e) {
            warnings.add("读取代码库当前文件失败：" + e.getMessage());
            return List.of();
        }

        List<DevelopmentCodeFileResponse> result = new ArrayList<>();
        int totalChars = 0;
        String normalizedRoot = normalizePath(platformRoot);
        for (Path path : paths) {
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
