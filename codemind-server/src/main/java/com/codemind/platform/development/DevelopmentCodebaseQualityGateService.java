package com.codemind.platform.development;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class DevelopmentCodebaseQualityGateService {

    private static final int MAX_SCAN_BYTES = 180_000;

    private final DevelopmentCodebaseWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public DevelopmentCodebaseQualityGateService(
            DevelopmentCodebaseWorkspaceService workspaceService,
            ObjectMapper objectMapper
    ) {
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    public DevelopmentCodebaseQualityGateResponse inspect(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack
    ) {
        DevelopmentCodebaseWorkspaceResponse workspace = workspaceService.getWorkspace(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );

        Path root = Path.of(workspace.absoluteCodebaseRoot()).toAbsolutePath().normalize();
        boolean codebaseExists = Files.isDirectory(root);
        List<DevelopmentCodebaseQualityItemResponse> items = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> nextActions = new ArrayList<>();

        Path manifestPath = root.resolve("codemind.generated.json");
        Map<String, Object> manifest = readJsonObject(manifestPath);
        DevelopmentCodeTraceGraphResponse traceGraph = workspace.codeTraceGraph();

        items.add(item(
                "CODEBASE_EXISTS",
                "STRUCTURE",
                "生成代码库存在",
                true,
                codebaseExists ? "PASS" : "NOT_READY",
                codebaseExists ? "已找到 generated Node.js 项目主干。" : "尚未找到 generated Node.js 项目主干。",
                List.of(workspace.codebaseRoot(), workspace.absoluteCodebaseRoot()),
                List.of(workspace.codebaseRoot()),
                codebaseExists ? "继续执行质量自检。" : "先生成并写入当前节点的 Node.js 系统代码。"
        ));

        if (!codebaseExists) {
            addUnique(blockers, "尚未生成 Node.js 代码库。");
            addUnique(nextActions, "点击“生成 Node.js 系统代码”，生成后再运行系统自检。");
        }

        boolean manifestReadable = !manifest.isEmpty();
        int manifestNodeCount = collectionSize(manifest.get("nodes"));
        int manifestFileCount = collectionSize(manifest.get("generatedFiles"));
        items.add(item(
                "MANIFEST_TRACE",
                "TRACE",
                "代码映射 Manifest",
                true,
                manifestReadable && manifestNodeCount > 0 && manifestFileCount > 0 ? "PASS" : "FAILED",
                manifestReadable
                        ? "Manifest 可读取，包含 " + manifestNodeCount + " 个节点、" + manifestFileCount + " 个生成文件。"
                        : "Manifest 缺失或不可读取，无法判断模块树与代码文件映射。",
                List.of("nodes=" + manifestNodeCount, "generatedFiles=" + manifestFileCount),
                List.of("codemind.generated.json"),
                "重新生成或更新当前节点代码，确保 codemind.generated.json 与实际代码同步。"
        ));

        if (!manifestReadable || manifestNodeCount == 0 || manifestFileCount == 0) {
            addUnique(blockers, "代码映射 Manifest 不完整。");
        }

        int selectedTraceFileCount = traceGraph == null ? 0 : traceGraph.selectedNodeFileCount();
        String selectedTraceStatus;
        boolean selectedTraceRequired = workspace.selectedNodeCanGenerate();
        if (selectedTraceFileCount > 0 || workspace.selectedNodeHasCode()) {
            selectedTraceStatus = "PASS";
        } else {
            selectedTraceStatus = selectedTraceRequired ? "FAILED" : "WARNING";
        }
        items.add(item(
                "SELECTED_NODE_TRACE",
                "TRACE",
                "当前业务节点代码映射",
                selectedTraceRequired,
                selectedTraceStatus,
                selectedTraceFileCount > 0
                        ? "当前节点已关联 " + selectedTraceFileCount + " 个代码文件。"
                        : "当前节点尚未关联代码文件；普通组织模块可以通过下级最终模块生成。",
                traceGraph == null
                        ? List.of("Code Trace Graph 暂为空。")
                        : List.of(traceGraph.summary(), "nodeFiles=" + workspace.nodeFiles().size()),
                workspace.nodeFiles().stream().map(DevelopmentCodebaseNodeFileResponse::filePath).limit(12).toList(),
                "生成当前最终模块或页面代码后，系统会自动刷新 Code Trace Graph。"
        ));
        if (selectedTraceRequired && "FAILED".equals(selectedTraceStatus)) {
            addUnique(blockers, "当前可开发节点没有代码映射。");
        } else if ("WARNING".equals(selectedTraceStatus)) {
            addUnique(warnings, "当前节点暂无专属代码映射；如果是组织模块可忽略。");
        }

        items.add(frontendShellItem(root));
        items.add(routeAlignmentItem(root));
        items.add(frontendApiIntegrationItem(root));
        items.add(backendShellItem(root));
        items.add(backendModuleItem(root));
        items.add(localDataItem(root));
        items.add(testCoverageItem(root));
        items.add(businessSmokeItem(root));
        items.add(traceFileExistenceItem(root, workspace));
        items.add(mockLeakageItem(root));

        for (DevelopmentCodebaseQualityItemResponse qualityItem : items) {
            if (qualityItem.required() && !qualityItem.passed()) {
                addUnique(blockers, qualityItem.title() + "未通过。");
            } else if ("WARNING".equals(qualityItem.status())) {
                addUnique(warnings, qualityItem.title() + "存在提示。");
            }
        }

        int passedCount = 0;
        int warningCount = 0;
        int failedCount = 0;
        int requiredFailedCount = 0;

        for (DevelopmentCodebaseQualityItemResponse qualityItem : items) {
            String status = safeUpper(qualityItem.status());
            if ("PASS".equals(status)) {
                passedCount += 1;
            } else if ("WARNING".equals(status)) {
                warningCount += 1;
            } else if ("FAILED".equals(status) || "NOT_READY".equals(status)) {
                failedCount += 1;
                if (qualityItem.required()) {
                    requiredFailedCount += 1;
                }
            }
        }

        int score = Math.max(0, Math.min(100, 100 - requiredFailedCount * 18 - Math.max(0, failedCount - requiredFailedCount) * 10 - warningCount * 5));
        String qualityStatus;
        boolean deliverableCandidate;
        if (!codebaseExists) {
            qualityStatus = "NOT_READY";
            deliverableCandidate = false;
        } else if (requiredFailedCount > 0 || !blockers.isEmpty()) {
            qualityStatus = "FAILED";
            deliverableCandidate = false;
        } else if (warningCount > 0) {
            qualityStatus = "WARNING";
            deliverableCandidate = true;
        } else {
            qualityStatus = "PASS";
            deliverableCandidate = true;
        }

        if (nextActions.isEmpty()) {
            if ("PASS".equals(qualityStatus)) {
                addUnique(nextActions, "可以继续运行验收、人工预览真实业务页面，或导出交付代码包。");
            } else if ("WARNING".equals(qualityStatus)) {
                addUnique(nextActions, "优先处理提示项；核心链路可继续运行验收和 AI 修复。");
            } else {
                addUnique(nextActions, "先处理失败项，再运行 npm build / test / acceptance。");
            }
        }

        return new DevelopmentCodebaseQualityGateResponse(
                workspace.projectId(),
                workspace.moduleId(),
                workspace.moduleKey(),
                workspace.moduleName(),
                projectCode,
                workspace.targetStack(),
                workspace.codebaseRegistry(),
                workspace.codebaseRoot(),
                workspace.absoluteCodebaseRoot(),
                codebaseExists,
                qualityStatus,
                score,
                deliverableCandidate,
                passedCount,
                warningCount,
                failedCount,
                requiredFailedCount,
                items,
                blockers,
                warnings,
                nextActions,
                buildSummary(qualityStatus, score, blockers, warnings),
                LocalDateTime.now()
        );
    }

    private DevelopmentCodebaseQualityItemResponse frontendShellItem(Path root) {
        List<String> files = List.of(
                "frontend/package.json",
                "frontend/index.html",
                "frontend/src/main.js",
                "frontend/src/App.vue",
                "frontend/src/router/routes.js"
        );
        List<String> missing = missingFiles(root, files);
        return item(
                "FRONTEND_SHELL",
                "FRONTEND",
                "前端应用主干",
                true,
                missing.isEmpty() ? "PASS" : "FAILED",
                missing.isEmpty()
                        ? "前端 Vite/Vue 主干完整。"
                        : "前端主干缺失：" + String.join("，", missing),
                missing.isEmpty() ? files : missing,
                files,
                "重新生成前端项目主干，确保 App、router、入口文件和 Vite 配置完整。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse routeAlignmentItem(Path root) {
        Path routes = root.resolve("frontend/src/router/routes.js");
        if (!Files.isRegularFile(routes)) {
            return item(
                    "ROUTE_ALIGNMENT",
                    "FRONTEND",
                    "页面路由与文件对齐",
                    true,
                    "FAILED",
                    "缺少 frontend/src/router/routes.js，无法打开真实页面入口。",
                    List.of("frontend/src/router/routes.js"),
                    List.of("frontend/src/router/routes.js"),
                    "重新生成前端路由文件。"
            );
        }

        String content = readText(routes);
        List<String> imports = importedVuePaths(content);
        List<String> missing = new ArrayList<>();
        Path routerDir = routes.getParent();

        for (String imported : imports) {
            Path resolved = routerDir.resolve(imported).normalize();
            if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
                missing.add(root.relativize(resolved).toString().replace("\\", "/"));
            }
        }

        boolean hasBusinessRoute = content.contains("path:") && !content.contains("GeneratedHomePage.vue");
        String status = missing.isEmpty() && !imports.isEmpty() && hasBusinessRoute ? "PASS" : (missing.isEmpty() ? "WARNING" : "FAILED");
        return item(
                "ROUTE_ALIGNMENT",
                "FRONTEND",
                "页面路由与文件对齐",
                true,
                status,
                "PASS".equals(status)
                        ? "路由导入的页面文件均存在，并且存在真实业务页面入口。"
                        : "WARNING".equals(status)
                        ? "路由可读取，但业务页面入口可能仍偏模板化。"
                        : "路由导入了不存在的页面文件：" + String.join("，", missing),
                List.of("routes=" + imports.size(), "missing=" + missing.size()),
                mergeFilePaths(List.of("frontend/src/router/routes.js"), missing),
                "保持 UI_CONTRACT 路由、Vue 页面文件和 App shell 一致。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse frontendApiIntegrationItem(Path root) {
        Path frontend = root.resolve("frontend/src");
        List<Path> scanned = scan(frontend, ".js", ".vue");
        int apiClientFiles = countFiles(root.resolve("frontend/src/api"), ".js") + countFiles(root.resolve("frontend/src/api"), ".ts");
        int apiCalls = 0;
        int mockHits = 0;
        for (Path path : scanned) {
            String content = readText(path);
            if (content.contains("fetch(") && content.contains("/api/")) {
                apiCalls += occurrences(content, "/api/");
            }
            if (content.toLowerCase(Locale.ROOT).contains("mock")) {
                mockHits += 1;
            }
        }

        String status = apiCalls > 0 && apiClientFiles > 0 ? "PASS" : "FAILED";
        return item(
                "FRONTEND_BACKEND_INTEGRATION",
                "INTEGRATION",
                "前端真实调用后端 API",
                true,
                status,
                "PASS".equals(status)
                        ? "前端存在 API client，并检测到 " + apiCalls + " 处 /api/ 调用。"
                        : "未检测到前端通过 /api/ 调用 Node.js 后端，可能仍停留在纯前端 mock。",
                List.of("apiClientFiles=" + apiClientFiles, "apiCalls=" + apiCalls, "mockFileHints=" + mockHits),
                List.of("frontend/src/api"),
                "生成页面逻辑时必须让前端调用 backend /api 接口，业务主链路不能只靠 mock 数据。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse backendShellItem(Path root) {
        List<String> files = List.of(
                "backend/package.json",
                "backend/server.js",
                "backend/src/app.js",
                "backend/src/routes/index.js",
                "backend/src/shared/response.js"
        );
        List<String> missing = missingFiles(root, files);
        String routesIndex = readText(root.resolve("backend/src/routes/index.js"));
        boolean apiMounted = routesIndex.contains("/api/") || routesIndex.contains("app.use('/api") || routesIndex.contains("app.use(\"/api");
        String status = missing.isEmpty() && apiMounted ? "PASS" : "FAILED";
        return item(
                "BACKEND_SHELL",
                "BACKEND",
                "Node.js 后端主干",
                true,
                status,
                "PASS".equals(status)
                        ? "Express 后端主干完整，并已挂载 /api 路由。"
                        : "后端主干缺失或未挂载 /api 路由。",
                mergeFilePaths(files, missing),
                files,
                "重新生成后端 app/server/routes/shared response 主干。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse backendModuleItem(Path root) {
        Path modules = root.resolve("backend/src/modules");
        int routeFiles = countFiles(modules, ".routes.js");
        int serviceFiles = countFiles(modules, ".service.js");
        String status = routeFiles > 0 && serviceFiles > 0 ? "PASS" : "FAILED";
        return item(
                "BACKEND_BUSINESS_MODULES",
                "BACKEND",
                "后端业务模块逻辑",
                true,
                status,
                "PASS".equals(status)
                        ? "检测到 " + routeFiles + " 个 routes 文件和 " + serviceFiles + " 个 service 文件。"
                        : "未检测到完整的后端 routes/service 业务模块。",
                List.of("routes=" + routeFiles, "services=" + serviceFiles),
                List.of("backend/src/modules"),
                "每个可开发模块至少应生成 routes + service；复杂业务后续再补 repository/validation。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse localDataItem(Path root) {
        Path localStore = root.resolve("backend/src/db/localStore.js");
        Path seedData = root.resolve("backend/src/db/seedData.js");
        String storeText = readText(localStore);
        boolean ok = Files.isRegularFile(localStore)
                && Files.isRegularFile(seedData)
                && storeText.contains("readStore")
                && storeText.contains("writeStore");
        return item(
                "LOCAL_DATA_BEHAVIOR",
                "DATA",
                "本地数据读写逻辑",
                true,
                ok ? "PASS" : "FAILED",
                ok
                        ? "后端存在 localStore 和 seedData，并暴露读写逻辑。"
                        : "缺少本地数据层或读写函数，后端可能无法保持业务状态。",
                List.of("readStore=" + storeText.contains("readStore"), "writeStore=" + storeText.contains("writeStore")),
                List.of("backend/src/db/localStore.js", "backend/src/db/seedData.js", "database/schema.json", "database/seed.json"),
                "生成 service 时应通过 localStore / seedData 实现最小真实状态，不要只返回静态响应。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse testCoverageItem(Path root) {
        Path packageJson = root.resolve("package.json");
        String pkg = readText(packageJson);
        boolean hasBuild = pkg.contains("\"build\"");
        boolean hasTest = pkg.contains("\"test\"");
        boolean hasAcceptance = pkg.contains("\"acceptance\"");
        boolean smoke = Files.isRegularFile(root.resolve("tests/smoke.spec.js"));
        String status = hasBuild && hasTest && hasAcceptance && smoke ? "PASS" : "FAILED";
        return item(
                "TEST_AND_ACCEPTANCE_SCRIPTS",
                "TEST",
                "构建与验收脚本",
                true,
                status,
                "PASS".equals(status)
                        ? "根 package.json 包含 build/test/acceptance，且存在 smoke 测试。"
                        : "构建、测试或 acceptance 脚本不完整。",
                List.of("build=" + hasBuild, "test=" + hasTest, "acceptance=" + hasAcceptance, "smoke=" + smoke),
                List.of("package.json", "tests/smoke.spec.js"),
                "生成项目必须具备可自动运行的 build、test 和 acceptance 检查入口。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse businessSmokeItem(Path root) {
        Path smoke = root.resolve("tests/smoke.spec.js");
        String content = readText(smoke);
        int apiHits = occurrences(content, "/api/");
        boolean health = content.contains("/api/health");
        boolean business = apiHits > 1;
        String status = health && business ? "PASS" : "WARNING";
        return item(
                "BUSINESS_SMOKE_FLOW",
                "BUSINESS",
                "业务主链路 smoke 测试",
                false,
                status,
                "PASS".equals(status)
                        ? "smoke 测试覆盖健康检查和至少一个业务 API。"
                        : "smoke 测试只覆盖了基础检查，业务主链路覆盖不足。",
                List.of("apiHits=" + apiHits, "health=" + health),
                List.of("tests/smoke.spec.js"),
                "为当前模块补充创建、查询、详情、提交等业务主链路 smoke 测试。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse traceFileExistenceItem(Path root, DevelopmentCodebaseWorkspaceResponse workspace) {
        DevelopmentCodeTraceGraphResponse graph = workspace.codeTraceGraph();
        if (graph == null || graph.files() == null || graph.files().isEmpty()) {
            return item(
                    "TRACE_FILE_EXISTENCE",
                    "TRACE",
                    "Code Trace Graph 文件存在性",
                    false,
                    "WARNING",
                    "Code Trace Graph 暂无文件映射。",
                    List.of(),
                    List.of("codemind.generated.json"),
                    "生成代码或手动保存文件后，系统会自动建立节点与文件的多对多映射。"
            );
        }

        int total = graph.files().size();
        long existing = graph.files().stream().filter(DevelopmentCodeTraceFileResponse::exists).count();
        String status = existing == total ? "PASS" : (existing == 0 ? "FAILED" : "WARNING");
        return item(
                "TRACE_FILE_EXISTENCE",
                "TRACE",
                "Code Trace Graph 文件存在性",
                true,
                status,
                "Code Trace Graph 记录 " + total + " 个文件，其中 " + existing + " 个实际存在。",
                List.of("total=" + total, "existing=" + existing),
                graph.files().stream().map(DevelopmentCodeTraceFileResponse::filePath).limit(16).toList(),
                "如果映射文件丢失，请重新生成代码或重新绑定代码库。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse mockLeakageItem(Path root) {
        List<Path> sourceFiles = new ArrayList<>();
        sourceFiles.addAll(scan(root.resolve("frontend/src"), ".vue", ".js", ".ts"));
        sourceFiles.addAll(scan(root.resolve("backend/src"), ".js", ".ts"));
        int mockFiles = 0;
        int todoFiles = 0;
        List<String> hits = new ArrayList<>();

        for (Path path : sourceFiles) {
            String content = readText(path).toLowerCase(Locale.ROOT);
            boolean hasMock = content.contains("mock");
            boolean hasTodo = content.contains("todo");
            if (hasMock) mockFiles += 1;
            if (hasTodo) todoFiles += 1;
            if ((hasMock || hasTodo) && hits.size() < 12) {
                hits.add(root.relativize(path).toString().replace("\\", "/"));
            }
        }

        String status = mockFiles == 0 && todoFiles == 0 ? "PASS" : "WARNING";
        return item(
                "MOCK_TODO_LEAKAGE",
                "BUSINESS",
                "Mock / TODO 残留检查",
                false,
                status,
                "PASS".equals(status)
                        ? "未发现明显 mock 或 TODO 残留。"
                        : "检测到 " + mockFiles + " 个 mock 文件提示、" + todoFiles + " 个 TODO 文件提示。",
                List.of("mockFiles=" + mockFiles, "todoFiles=" + todoFiles),
                hits,
                "真实业务主链路完成后，应把 mock/TODO 收敛为真实 API、service、validation 和测试。"
        );
    }

    private DevelopmentCodebaseQualityItemResponse item(
            String id,
            String category,
            String title,
            boolean required,
            String status,
            String summary,
            List<String> details,
            List<String> filePaths,
            String recommendation
    ) {
        String safeStatus = safeUpper(status);
        boolean passed = "PASS".equals(safeStatus) || ("WARNING".equals(safeStatus) && !required);
        int scoreImpact = "PASS".equals(safeStatus) ? 0 : (required ? 18 : 5);
        return new DevelopmentCodebaseQualityItemResponse(
                id,
                category,
                title,
                safeStatus,
                required,
                passed,
                scoreImpact,
                summary,
                details == null ? List.of() : details,
                filePaths == null ? List.of() : filePaths,
                recommendation
        );
    }

    private Map<String, Object> readJsonObject(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<String> importedVuePaths(String content) {
        List<String> imports = new ArrayList<>();
        Pattern pattern = Pattern.compile("from\\s+['\\\"]([^'\\\"]+\\.vue)['\\\"]");
        Matcher matcher = pattern.matcher(content == null ? "" : content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    private List<Path> scan(Path root, String... suffixes) {
        if (root == null || !Files.exists(root)) {
            return List.of();
        }
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !shouldSkip(path))
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        for (String suffix : suffixes) {
                            if (name.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .limit(420)
                    .forEach(paths::add);
        } catch (IOException ignored) {
            return List.of();
        }
        return paths;
    }

    private int countFiles(Path root, String suffix) {
        return scan(root, suffix).size();
    }

    private boolean shouldSkip(Path path) {
        String normalized = path.toString().replace("\\", "/");
        return normalized.contains("/node_modules/")
                || normalized.contains("/dist/")
                || normalized.contains("/build/")
                || normalized.contains("/.git/");
    }

    private String readText(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        try {
            long size = Files.size(path);
            if (size > MAX_SCAN_BYTES) {
                byte[] bytes = Files.readAllBytes(path);
                return new String(bytes, 0, Math.min(bytes.length, MAX_SCAN_BYTES), StandardCharsets.UTF_8);
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private List<String> missingFiles(Path root, List<String> relativeFiles) {
        List<String> missing = new ArrayList<>();
        for (String relative : relativeFiles) {
            if (!Files.isRegularFile(root.resolve(relative))) {
                missing.add(relative);
            }
        }
        return missing;
    }

    private List<String> mergeFilePaths(List<String> primary, List<String> extra) {
        Set<String> merged = new LinkedHashSet<>();
        if (primary != null) merged.addAll(primary);
        if (extra != null) merged.addAll(extra);
        return new ArrayList<>(merged);
    }

    @SuppressWarnings("unchecked")
    private int collectionSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        return 0;
    }

    private int occurrences(String text, String needle) {
        if (text == null || text.isEmpty() || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count += 1;
            index += needle.length();
        }
        return count;
    }

    private String safeUpper(String value) {
        return String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
    }

    private void addUnique(List<String> target, String value) {
        if (value == null || value.isBlank() || target.contains(value)) {
            return;
        }
        target.add(value);
    }

    private String buildSummary(String status, int score, List<String> blockers, List<String> warnings) {
        if ("PASS".equals(status)) {
            return "生成系统质量自检通过，评分 " + score + " 分。";
        }
        if ("WARNING".equals(status)) {
            return "生成系统核心结构可用，但存在提示项，评分 " + score + " 分：" + String.join("；", warnings);
        }
        if ("NOT_READY".equals(status)) {
            return "尚未生成代码库，暂不能执行生成系统质量自检。";
        }
        return "生成系统质量自检未通过，评分 " + score + " 分：" + String.join("；", blockers);
    }
}

