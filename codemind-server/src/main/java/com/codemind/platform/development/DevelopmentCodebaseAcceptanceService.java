package com.codemind.platform.development;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class DevelopmentCodebaseAcceptanceService {

    private static final int DEFAULT_FRONTEND_PORT = 5178;
    private static final int DEFAULT_BACKEND_PORT = 3101;
    private static final int SHORT_COMMAND_TIMEOUT_SECONDS = 20;
    private static final int INSTALL_TIMEOUT_SECONDS = 180;
    private static final int BUILD_TIMEOUT_SECONDS = 150;
    private static final int TEST_TIMEOUT_SECONDS = 90;

    private final DevelopmentHandoffGateService developmentHandoffGateService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(900))
            .build();

    public DevelopmentCodebaseAcceptanceService(
            DevelopmentHandoffGateService developmentHandoffGateService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            ObjectMapper objectMapper
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.objectMapper = objectMapper;
    }

    public DevelopmentCodebaseAcceptanceResponse getStatus(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack
    ) {
        AcceptanceContext context = resolveContext(workspaceId, projectCode, moduleId, targetStack);
        return evaluate(context, false, new DevelopmentCodebaseAcceptanceRunRequest(
                context.targetStack(),
                false,
                false,
                false,
                true
        ));
    }

    public DevelopmentCodebaseAcceptanceResponse runAcceptance(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodebaseAcceptanceRunRequest request
    ) {
        String requestedStack = request == null ? null : request.targetStack();
        AcceptanceContext context = resolveContext(workspaceId, projectCode, moduleId, requestedStack);
        DevelopmentCodebaseAcceptanceRunRequest safeRequest = request == null
                ? new DevelopmentCodebaseAcceptanceRunRequest(context.targetStack(), true, true, true, true)
                : request;
        return evaluate(context, true, safeRequest);
    }

    private DevelopmentCodebaseAcceptanceResponse evaluate(
            AcceptanceContext context,
            boolean executeCommands,
            DevelopmentCodebaseAcceptanceRunRequest request
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        List<DevelopmentCodebaseAcceptanceItemResponse> items = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> nextActions = new ArrayList<>();

        Path root = context.absoluteCodebaseRoot();
        Path packageJson = root.resolve("package.json");
        Path manifest = root.resolve("codemind.generated.json");
        Path backendLocalStore = root.resolve("backend").resolve("src").resolve("db").resolve("localStore.js");
        Path backendSeedData = root.resolve("backend").resolve("src").resolve("db").resolve("seedData.js");
        Path databaseSchema = root.resolve("database").resolve("schema.json");
        Path databaseSeed = root.resolve("database").resolve("seed.json");
        boolean codebaseExists = Files.isDirectory(root);
        boolean packageJsonExists = Files.isRegularFile(packageJson);
        boolean manifestExists = Files.isRegularFile(manifest);
        boolean localStoreExists = Files.isRegularFile(backendLocalStore);
        boolean seedDataExists = Files.isRegularFile(backendSeedData);
        boolean databaseSchemaExists = Files.isRegularFile(databaseSchema);
        boolean databaseSeedExists = Files.isRegularFile(databaseSeed);
        boolean nodeModulesExists = Files.isDirectory(root.resolve("node_modules"));
        boolean buildScriptExists = packageJsonExists && hasScript(packageJson, "build");
        boolean testScriptExists = packageJsonExists && hasScript(packageJson, "test");

        items.add(staticItem(
                "CODEBASE_EXISTS",
                "PREFLIGHT",
                "代码库目录",
                true,
                codebaseExists,
                codebaseExists ? "已找到生成项目主干。" : "未找到生成项目主干。",
                List.of(context.codebaseRegistry().platformRootPath())
        ));
        if (!codebaseExists) {
            addUnique(blockers, "生成项目主干不存在，不能执行验收流水线。");
            addUnique(nextActions, "先生成当前节点代码，并写入 generated/nodejs 项目主干。 ");
        }

        items.add(staticItem(
                "MANIFEST_EXISTS",
                "PREFLIGHT",
                "代码映射 Manifest",
                true,
                manifestExists,
                manifestExists ? "已找到 codemind.generated.json。" : "缺少 codemind.generated.json。",
                List.of(context.codebaseRegistry().manifestPlatformPath())
        ));
        if (!manifestExists) {
            addUnique(blockers, "缺少 codemind.generated.json，模块树节点与代码文件无法可靠追踪。");
            addUnique(nextActions, "重新写入当前节点代码，让 Dev Agent 更新 manifest。 ");
        }

        DevelopmentCodebaseAcceptanceItemResponse generatedTrace = generatedManifestTraceItem(root);
        items.add(generatedTrace);
        if (!generatedTrace.passed()) {
            addUnique(blockers, "缺少生成项目原型真实化追踪 Manifest，无法稳定定位 pageKey/actionId/apiSegment/localStore/smokeCase。 ");
            addUnique(nextActions, "重新生成并写入 Node.js 项目，确保 generatedResources.js 带 generatedTrace，且生成 generatedManifestTrace.js / database/generated-trace.json。 ");
        }

        items.add(staticItem(
                "PACKAGE_JSON_EXISTS",
                "PREFLIGHT",
                "根 package.json",
                true,
                packageJsonExists,
                packageJsonExists ? "已找到根 package.json。" : "缺少根 package.json。",
                List.of("package.json")
        ));
        if (!packageJsonExists) {
            addUnique(blockers, "缺少根 package.json，不能安装依赖、构建或测试。 ");
            addUnique(nextActions, "重新生成项目主干，确保根目录包含 package.json。 ");
        }

        items.add(staticItem(
                "DEPENDENCIES_INSTALLED",
                "PREFLIGHT",
                "依赖目录",
                false,
                nodeModulesExists,
                nodeModulesExists ? "node_modules 已存在。" : "尚未发现 node_modules，可通过验收流水线自动执行 npm install。",
                List.of("node_modules")
        ));
        if (!nodeModulesExists) {
            addUnique(warnings, "依赖尚未安装，首次验收会耗时更久。 ");
        }

        items.add(staticItem(
                "BUILD_SCRIPT_EXISTS",
                "PREFLIGHT",
                "构建脚本",
                true,
                buildScriptExists,
                buildScriptExists ? "根 package.json 已声明 build 脚本。" : "根 package.json 缺少 build 脚本。",
                List.of("npm run build")
        ));
        if (packageJsonExists && !buildScriptExists) {
            addUnique(blockers, "根 package.json 缺少 build 脚本，无法执行正式构建验收。 ");
            addUnique(nextActions, "重新生成并安全写入项目主干，或手动补充根 build 脚本。 ");
        }

        items.add(staticItem(
                "TEST_SCRIPT_EXISTS",
                "PREFLIGHT",
                "测试脚本",
                true,
                testScriptExists,
                testScriptExists ? "根 package.json 已声明 test 脚本。" : "根 package.json 缺少 test 脚本。",
                List.of("npm test")
        ));
        if (packageJsonExists && !testScriptExists) {
            addUnique(blockers, "根 package.json 缺少 test 脚本，无法执行测试验收。 ");
            addUnique(nextActions, "重新生成并安全写入项目主干，或手动补充根 test 脚本。 ");
        }

        items.add(staticItem(
                "DATABASE_LOCAL_STORE_EXISTS",
                "PREFLIGHT",
                "数据库连接模块",
                true,
                localStoreExists,
                localStoreExists ? "已找到后端本地数据库连接模块 localStore.js。" : "缺少后端本地数据库连接模块 localStore.js。",
                List.of("backend/src/db/localStore.js")
        ));
        if (!localStoreExists) {
            addUnique(blockers, "缺少后端数据层连接模块 localStore.js，不能验收数据库连接和读写。 ");
            addUnique(nextActions, "重新生成项目主干，确保 backend/src/db/localStore.js 存在。 ");
        }

        items.add(staticItem(
                "DATABASE_SEED_DATA_EXISTS",
                "PREFLIGHT",
                "数据库初始化数据",
                true,
                seedDataExists,
                seedDataExists ? "已找到 seedData.js，可初始化本地数据库数据。" : "缺少 seedData.js。",
                List.of("backend/src/db/seedData.js")
        ));
        if (!seedDataExists) {
            addUnique(blockers, "缺少数据库 seedData.js，不能可靠初始化本地数据层。 ");
            addUnique(nextActions, "重新生成项目主干，确保 backend/src/db/seedData.js 存在。 ");
        }

        items.add(staticItem(
                "DATABASE_SCHEMA_EXISTS",
                "PREFLIGHT",
                "数据库结构描述",
                true,
                databaseSchemaExists,
                databaseSchemaExists ? "已找到 database/schema.json。" : "缺少 database/schema.json。",
                List.of("database/schema.json")
        ));
        if (!databaseSchemaExists) {
            addUnique(blockers, "缺少 database/schema.json，不能追踪生成数据库结构。 ");
            addUnique(nextActions, "重新生成项目主干，确保 database/schema.json 存在。 ");
        }

        items.add(staticItem(
                "DATABASE_SEED_FILE_EXISTS",
                "PREFLIGHT",
                "数据库种子文件",
                false,
                databaseSeedExists,
                databaseSeedExists ? "已找到 database/seed.json。" : "缺少 database/seed.json；可继续验收 localStore，但建议补齐。",
                List.of("database/seed.json")
        ));
        if (!databaseSeedExists) {
            addUnique(warnings, "缺少 database/seed.json；当前可通过 backend/src/db/seedData.js 初始化，但交付包数据库种子文件不完整。 ");
        }

        DevelopmentCodebaseAcceptanceItemResponse prototypeFullstackBindingContract = prototypeFullstackBindingContractItem(root);
        items.add(prototypeFullstackBindingContract);
        if (!prototypeFullstackBindingContract.passed()) {
            addUnique(blockers, "交互原型真实化绑定门禁未通过，不能把 build/test 通过当成完整交付。 ");
            addUnique(nextActions, "重新生成 Node.js 项目，确保 PAGE_UI_CONTRACT runtime、fullstackBinding、actionBindings、dataBindings、API client、Express router、localStore 和 smoke test 同源闭环。 ");
        }

        boolean readyToRun = blockers.isEmpty();

        if (executeCommands && readyToRun) {
            DevelopmentCodebaseAcceptanceItemResponse nodeCheck = runCommandItem(
                    root,
                    "NODE_NPM_AVAILABLE",
                    "ENVIRONMENT",
                    "Node.js / npm 可用性",
                    true,
                    List.of("node", "-v"),
                    SHORT_COMMAND_TIMEOUT_SECONDS
            );
            items.add(nodeCheck);
            if (!nodeCheck.passed()) {
                addUnique(blockers, "Node.js 命令不可用，不能继续执行 npm 验收。 ");
                addUnique(nextActions, "在服务器安装 Node.js，并确认 node / npm 已加入 PATH。 ");
            } else {
                DevelopmentCodebaseAcceptanceItemResponse npmCheck = runCommandItem(
                        root,
                        "NPM_AVAILABLE",
                        "ENVIRONMENT",
                        "npm 可用性",
                        true,
                        List.of("npm", "-v"),
                        SHORT_COMMAND_TIMEOUT_SECONDS
                );
                items.add(npmCheck);
                if (!npmCheck.passed()) {
                    addUnique(blockers, "npm 命令不可用，不能继续执行依赖安装、构建和测试。 ");
                    addUnique(nextActions, "修复 Node.js / npm 安装后重新运行验收。 ");
                }
            }

            if (blockers.isEmpty() && enabled(request.installDependencies(), true)) {
                DevelopmentCodebaseAcceptanceItemResponse install = runCommandItem(
                        root,
                        "NPM_INSTALL",
                        "INSTALL",
                        "安装依赖",
                        true,
                        List.of("npm", "install"),
                        INSTALL_TIMEOUT_SECONDS
                );
                items.add(install);
                if (!install.passed()) {
                    addUnique(blockers, "npm install 失败。 ");
                    addUnique(nextActions, "查看安装日志，处理 npm 源、网络代理或依赖版本问题。 ");
                }
            } else if (blockers.isEmpty()) {
                items.add(skippedItem("NPM_INSTALL", "INSTALL", "安装依赖", false, "本次请求跳过 npm install。", List.of("npm install")));
            }

            if (blockers.isEmpty() && enabled(request.buildProject(), true)) {
                DevelopmentCodebaseAcceptanceItemResponse build = runCommandItem(
                        root,
                        "NPM_BUILD",
                        "BUILD",
                        "前端生产构建",
                        true,
                        List.of("npm", "run", "build"),
                        BUILD_TIMEOUT_SECONDS
                );
                items.add(build);
                if (!build.passed()) {
                    addUnique(blockers, "npm run build 失败。 ");
                    addUnique(nextActions, "优先修复构建日志里第一处语法错误、导入路径错误或组件编译错误。 ");
                }
            } else if (blockers.isEmpty()) {
                items.add(skippedItem("NPM_BUILD", "BUILD", "前端生产构建", true, "本次请求跳过 npm run build。", List.of("npm run build")));
            }

            if (blockers.isEmpty()) {
                String dbProbeScript = writeDatabaseAcceptanceProbeScript(root);
                DevelopmentCodebaseAcceptanceItemResponse databaseProbe = runCommandItem(
                        root,
                        "DATABASE_READ_WRITE_PROBE",
                        "DATABASE",
                        "数据库连接与读写探针",
                        true,
                        List.of("node", dbProbeScript),
                        SHORT_COMMAND_TIMEOUT_SECONDS
                );
                items.add(databaseProbe);
                if (!databaseProbe.passed()) {
                    addUnique(blockers, "数据库连接或读写探针失败。 ");
                    addUnique(nextActions, "优先检查 backend/src/db/localStore.js、data/local-store.json 写入权限以及 seed data 结构。 ");
                }
            }

            if (blockers.isEmpty()) {
                DevelopmentCodebaseAcceptanceItemResponse runtimeApiDatabaseSmoke = runRuntimeApiDatabaseSmoke(root);
                items.add(runtimeApiDatabaseSmoke);
                if (!runtimeApiDatabaseSmoke.passed()) {
                    addUnique(blockers, "后端 HTTP API 到数据库读写链路失败。 ");
                    addUnique(nextActions, "优先检查 backend/server.js、backend/src/routes/index.js、localStore.js 以及 3101 端口占用。 ");
                }
            }

            if (blockers.isEmpty() && enabled(request.runTests(), true)) {
                DevelopmentCodebaseAcceptanceItemResponse test = runCommandItem(
                        root,
                        "NPM_TEST",
                        "TEST",
                        "冒烟测试",
                        true,
                        List.of("npm", "test"),
                        TEST_TIMEOUT_SECONDS
                );
                items.add(test);
                if (!test.passed()) {
                    addUnique(blockers, "npm test 失败。 ");
                    addUnique(nextActions, "查看测试日志；如果测试依赖后端健康检查，请先启动生成项目后重新验收。 ");
                }
            } else if (blockers.isEmpty()) {
                items.add(skippedItem("NPM_TEST", "TEST", "冒烟测试", true, "本次请求跳过 npm test。", List.of("npm test")));
            }
        } else if (executeCommands) {
            addUnique(nextActions, "先处理预检查阻断项，再运行验收流水线。 ");
        }

        if (enabled(request.checkRuntimeHealth(), true)) {
            Probe frontendProbe = probe(frontendUrl());
            Probe backendProbe = probe(healthUrl());
            items.add(probeItem(
                    "FRONTEND_PREVIEW",
                    "HEALTH",
                    "前端预览可访问",
                    false,
                    frontendProbe,
                    frontendUrl()
            ));
            items.add(probeItem(
                    "BACKEND_HEALTH",
                    "HEALTH",
                    "后端健康检查",
                    false,
                    backendProbe,
                    healthUrl()
            ));
            if (!frontendProbe.reachable() || !backendProbe.reachable()) {
                addUnique(warnings, "前端或后端健康检查未全部通过。项目未启动时这属于预期现象。 ");
                addUnique(nextActions, "点击“启动项目”，待状态变为 RUNNING 后重新运行验收。 ");
            }
        }

        int passedCount = 0;
        int warningCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        for (DevelopmentCodebaseAcceptanceItemResponse item : items) {
            String status = item.status();
            if ("PASS".equals(status) || "READY".equals(status)) {
                passedCount += 1;
            } else if ("WARNING".equals(status)) {
                warningCount += 1;
            } else if ("SKIPPED".equals(status)) {
                skippedCount += 1;
            } else if ("FAILED".equals(status) || "TIMEOUT".equals(status) || "NOT_READY".equals(status)) {
                failedCount += 1;
            }
        }

        boolean requiredFailed = items.stream().anyMatch(item -> item.required() && !item.passed());
        String acceptanceStatus;
        boolean deliverable;
        if (!blockers.isEmpty() || requiredFailed) {
            acceptanceStatus = "FAILED";
            deliverable = false;
        } else if (!executeCommands) {
            acceptanceStatus = "READY";
            deliverable = false;
        } else if (!warnings.isEmpty() || warningCount > 0 || skippedCount > 0) {
            acceptanceStatus = "WARNING";
            deliverable = true;
        } else {
            acceptanceStatus = "PASS";
            deliverable = true;
        }

        String summary = buildSummary(acceptanceStatus, executeCommands, blockers, warnings);
        if (nextActions.isEmpty()) {
            if (deliverable) {
                addUnique(nextActions, "可以下载交付 ZIP，或继续生成 Dockerfile / docker-compose 部署配置。 ");
            } else if ("READY".equals(acceptanceStatus)) {
                addUnique(nextActions, "点击“运行验收流水线”，执行 npm install / build / test。 ");
            }
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

        return new DevelopmentCodebaseAcceptanceResponse(
                context.readiness().projectId(),
                context.readiness().moduleId(),
                context.readiness().moduleKey(),
                context.readiness().moduleName(),
                context.projectCode(),
                context.targetStack(),
                context.codebaseRegistry(),
                context.codebaseRegistry().platformRootPath(),
                root.toString(),
                codebaseExists,
                readyToRun,
                executeCommands,
                deliverable,
                acceptanceStatus,
                passedCount,
                warningCount,
                failedCount,
                skippedCount,
                durationMillis,
                startedAt,
                finishedAt,
                frontendUrl(),
                backendUrl(),
                healthUrl(),
                items,
                blockers,
                warnings,
                nextActions,
                summary
        );
    }

    private AcceptanceContext resolveContext(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack
    ) {
        DevelopmentHandoffReadinessResponse readiness = developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );
        String resolvedStack = resolveTargetStack(targetStack);
        DevelopmentCodebaseRegistryResponse registry = codebaseRegistryService.resolveCodebase(
                workspaceId,
                readiness.projectId(),
                projectCode,
                resolvedStack
        );
        Path root = Path.of(registry.absoluteRootPath()).toAbsolutePath().normalize();
        return new AcceptanceContext(workspaceId, projectCode, moduleId, resolvedStack, readiness, registry, root);
    }


    // A9.10.14.4.3.15.4.8.5 GENERATED_MANIFEST_TRACE_CLOSURE
    private DevelopmentCodebaseAcceptanceItemResponse generatedManifestTraceItem(Path root) {
        Path resources = root.resolve("backend/src/modules/generated/generatedResources.js");
        Path traceJs = root.resolve("backend/src/modules/generated/generatedManifestTrace.js");
        Path traceJson = root.resolve("database/generated-trace.json");
        Path schemaJson = root.resolve("database/schema.json");

        boolean resourcesExists = Files.isRegularFile(resources);
        boolean traceExists = Files.isRegularFile(traceJs) || Files.isRegularFile(traceJson);
        String text = readAcceptanceText(resources) + "\n" + readAcceptanceText(traceJs) + "\n" + readAcceptanceText(traceJson) + "\n" + readAcceptanceText(schemaJson);
        String compact = text.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");

        boolean hasGeneratedTrace = compact.contains("generatedtrace") || compact.contains("generatedmanifesttrace");
        boolean hasPageKey = compact.contains("pagekey");
        boolean hasRoutePath = compact.contains("routepath");
        boolean hasComponentFile = compact.contains("componentfile");
        boolean hasActionId = compact.contains("actionid");
        boolean hasApiSegment = compact.contains("apisegment");
        boolean hasLocalStoreBucket = compact.contains("localstorebucket") || compact.contains("localstore");
        boolean hasSmokeCase = compact.contains("smokecase") || compact.contains("verifyresource") || compact.contains("verifyactionbindings");
        boolean hasFullstackBinding = compact.contains("fullstackbinding");

        boolean passed = resourcesExists
                && traceExists
                && hasGeneratedTrace
                && hasPageKey
                && hasRoutePath
                && hasComponentFile
                && hasActionId
                && hasApiSegment
                && hasLocalStoreBucket
                && hasSmokeCase
                && hasFullstackBinding;

        return staticItem(
                "GENERATED_MANIFEST_TRACE_CONTRACT",
                "STATIC",
                "原型真实化追踪 Manifest",
                true,
                passed,
                passed
                        ? "已生成 pageKey / route / component / actionId / apiSegment / localStore / smokeCase 的稳定追踪 Manifest。"
                        : "生成项目缺少完整的原型真实化追踪 Manifest，repair 和验收无法精确定位断链。",
                List.of(
                        "generatedResources.js=" + resourcesExists,
                        "generatedManifestTrace=" + traceExists,
                        "generatedTrace=" + hasGeneratedTrace,
                        "pageKey=" + hasPageKey,
                        "routePath=" + hasRoutePath,
                        "componentFile=" + hasComponentFile,
                        "actionId=" + hasActionId,
                        "apiSegment=" + hasApiSegment,
                        "localStoreBucket=" + hasLocalStoreBucket,
                        "smokeCase=" + hasSmokeCase,
                        "fullstackBinding=" + hasFullstackBinding,
                        "files=backend/src/modules/generated/generatedResources.js, backend/src/modules/generated/generatedManifestTrace.js, database/generated-trace.json"
                )
        );
    }

    private String readAcceptanceText(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private DevelopmentCodebaseAcceptanceItemResponse staticItem(
            String id,
            String phase,
            String title,
            boolean required,
            boolean passed,
            String summary,
            List<String> details
    ) {
        return new DevelopmentCodebaseAcceptanceItemResponse(
                id,
                phase,
                title,
                passed ? "PASS" : (required ? "NOT_READY" : "WARNING"),
                required,
                passed || !required,
                false,
                null,
                null,
                0L,
                summary,
                details == null ? List.of() : details,
                List.of()
        );
    }

    private DevelopmentCodebaseAcceptanceItemResponse skippedItem(
            String id,
            String phase,
            String title,
            boolean required,
            String summary,
            List<String> details
    ) {
        return new DevelopmentCodebaseAcceptanceItemResponse(
                id,
                phase,
                title,
                "SKIPPED",
                required,
                !required,
                false,
                null,
                null,
                0L,
                summary,
                details == null ? List.of() : details,
                List.of()
        );
    }

    private DevelopmentCodebaseAcceptanceItemResponse prototypeFullstackBindingContractItem(Path root) {
        long startedNanos = System.nanoTime();
        List<String> details = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        Path generatedResources = root.resolve("backend").resolve("src").resolve("modules").resolve("generated").resolve("generatedResources.js");
        Path generatedRoutes = root.resolve("backend").resolve("src").resolve("modules").resolve("generated").resolve("generated-resource.routes.js");
        Path generatedService = root.resolve("backend").resolve("src").resolve("modules").resolve("generated").resolve("generated-resource.service.js");
        Path generatedRepository = root.resolve("backend").resolve("src").resolve("modules").resolve("generated").resolve("generated-resource.repository.js");
        Path localStore = root.resolve("backend").resolve("src").resolve("db").resolve("localStore.js");
        Path seedData = root.resolve("backend").resolve("src").resolve("db").resolve("seedData.js");
        Path pagesDir = root.resolve("frontend").resolve("src").resolve("modules").resolve("generated").resolve("pages");
        Path apiDir = root.resolve("frontend").resolve("src").resolve("modules").resolve("generated").resolve("api");
        Path runtime = root.resolve("frontend").resolve("src").resolve("shared").resolve("ui-runtime").resolve("UiContractPreviewRuntime.vue");
        Path runtimeNode = root.resolve("frontend").resolve("src").resolve("shared").resolve("ui-runtime").resolve("UiContractRuntimeNode.vue");
        Path patternRenderer = root.resolve("frontend").resolve("src").resolve("shared").resolve("ui-runtime").resolve("ui-contract-runtime").resolve("UiContractPatternRenderer.vue");
        Path smoke = root.resolve("tests").resolve("smoke.spec.js");

        requireRegularFile(root, generatedResources, failures, details);
        requireRegularFile(root, generatedRoutes, failures, details);
        requireRegularFile(root, generatedService, failures, details);
        requireRegularFile(root, generatedRepository, failures, details);
        requireRegularFile(root, localStore, failures, details);
        requireRegularFile(root, seedData, failures, details);
        requireRegularFile(root, runtime, failures, details);
        requireRegularFile(root, runtimeNode, failures, details);
        requireRegularFile(root, patternRenderer, failures, details);
        requireRegularFile(root, smoke, failures, details);

        List<Path> pageFiles = regularFiles(pagesDir, ".vue");
        List<Path> apiFiles = regularFiles(apiDir, ".js");
        details.add("frontend generated page files: " + pageFiles.size());
        details.add("frontend generated api files: " + apiFiles.size());

        String generatedResourcesText = readUtf8(generatedResources);
        requireTextContains(failures, details, "backend/src/modules/generated/generatedResources.js", generatedResourcesText,
                "generatedResources", "fullstackBinding", "actionBindings", "dataBindings", "fidelityPolicy", "PAGE_UI_CONTRACT_RUNTIME");

        String routesText = readUtf8(generatedRoutes);
        requireTextContains(failures, details, "backend/src/modules/generated/generated-resource.routes.js", routesText,
                "createGeneratedResourceRouter", "router.get", "router.post", "router.put", "router.delete", "router.post('/:actionId'");

        String serviceText = readUtf8(generatedService);
        requireTextContains(failures, details, "backend/src/modules/generated/generated-resource.service.js", serviceText,
                "list", "create", "update", "remove", "runAction", "validatePayload");

        String repositoryText = readUtf8(generatedRepository);
        requireTextContains(failures, details, "backend/src/modules/generated/generated-resource.repository.js", repositoryText,
                "localStoreCollection", "../../db/localStore.js", "getCollection");

        String localStoreText = readUtf8(localStore);
        requireTextContains(failures, details, "backend/src/db/localStore.js", localStoreText,
                "seedData", "collection");

        if (pageFiles.isEmpty()) {
            failures.add("frontend/src/modules/generated/pages 下没有生成任何页面文件。 ");
        }

        int runtimePageCount = 0;
        int placeholderPageCount = 0;
        int genericTemplatePageCount = 0;
        for (Path pageFile : pageFiles) {
            String name = pageFile.getFileName().toString();
            if ("GeneratedHomePage.vue".equals(name)) {
                continue;
            }
            String pageText = readUtf8(pageFile);
            if (pageText.contains("MODULE_PROTOTYPE_PLACEHOLDER")) {
                placeholderPageCount += 1;
                details.add("prototype placeholder page: " + relativePath(root, pageFile));
                continue;
            }
            if (pageText.contains("NODE.JS FULLSTACK PAGE") || pageText.contains("submitForm()") && pageText.contains("ucg-page-header")) {
                genericTemplatePageCount += 1;
                failures.add("发现旧通用业务模板页面，未复用 PAGE_UI_CONTRACT runtime：" + relativePath(root, pageFile));
                continue;
            }
            if (pageText.contains("UiContractPreviewRuntime") || pageText.contains("@prototype-action")) {
                runtimePageCount += 1;
                requireTextContains(failures, details, relativePath(root, pageFile), pageText,
                        "UiContractPreviewRuntime", "@prototype-action", "runUiAction", "fetchUiRows", "fullstackBinding", "componentTree", "pageStates", "mockData");
            }
        }

        details.add("runtime fidelity pages: " + runtimePageCount);
        details.add("prototype placeholder pages: " + placeholderPageCount);
        details.add("legacy generic template pages: " + genericTemplatePageCount);

        if (runtimePageCount <= 0) {
            failures.add("没有发现复用 UiContractPreviewRuntime 的真实原型页面；不能把普通 Vue 模板当成交互原型真实化结果。 ");
        }

        int apiBindingFiles = 0;
        for (Path apiFile : apiFiles) {
            String apiText = readUtf8(apiFile);
            if (apiText.contains("fetchUiRows") && apiText.contains("runUiAction") && apiText.contains("actionBindings")) {
                apiBindingFiles += 1;
            }
        }
        details.add("runtime api binding files: " + apiBindingFiles);
        if (runtimePageCount > 0 && apiBindingFiles < runtimePageCount) {
            failures.add("真实原型页面数量为 " + runtimePageCount + "，但绑定 fetchUiRows/runUiAction/actionBindings 的 API 文件只有 " + apiBindingFiles + "。 ");
        }

        String smokeText = readUtf8(smoke);
        requireTextContains(failures, details, "tests/smoke.spec.js", smokeText,
                "generatedResources", "verifyResource", "verifyActionBindings", "actionBindings", "POST", "DELETE");

        String status = failures.isEmpty() ? "PASS" : "FAILED";
        boolean passed = failures.isEmpty();
        if (!failures.isEmpty()) {
            logs.addAll(failures);
        }

        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return new DevelopmentCodebaseAcceptanceItemResponse(
                "PROTOTYPE_FULLSTACK_BINDING_CONTRACT",
                "CONTRACT",
                "交互原型真实化绑定门禁",
                status,
                true,
                passed,
                false,
                null,
                null,
                durationMillis,
                passed
                        ? "PAGE_UI_CONTRACT runtime、fullstackBinding、API、Express router、localStore 与 smoke test 已形成同源真实化闭环。"
                        : "交互原型真实化绑定门禁未通过：" + failures.size() + " 个问题。",
                details,
                tail(logs, 120)
        );
    }

    private List<Path> regularFiles(Path dir, String suffix) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> suffix == null || suffix.isBlank() || path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void requireRegularFile(Path root, Path file, List<String> failures, List<String> details) {
        String relative = relativePath(root, file);
        if (Files.isRegularFile(file)) {
            details.add("OK file: " + relative);
        } else {
            failures.add("缺少文件：" + relative);
        }
    }

    private void requireTextContains(List<String> failures, List<String> details, String label, String text, String... tokens) {
        String safeText = text == null ? "" : text;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (!safeText.contains(token)) {
                failures.add(label + " 缺少关键 token：" + token);
            } else {
                details.add(label + " contains " + token);
            }
        }
    }

    private String readUtf8(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String relativePath(Path root, Path file) {
        if (file == null) {
            return "";
        }
        try {
            return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.toString().replace('\\', '/');
        }
    }

    private DevelopmentCodebaseAcceptanceItemResponse runCommandItem(
            Path root,
            String id,
            String phase,
            String title,
            boolean required,
            List<String> command,
            int timeoutSeconds
    ) {
        long startedNanos = System.nanoTime();
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        String commandText = String.join(" ", command);
        Integer exitCode = null;
        boolean timedOut = false;

        try {
            ProcessBuilder builder = new ProcessBuilder(platformCommand(command));
            builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            builder.environment().putIfAbsent("CODEMIND_ACCEPTANCE", "true");
            builder.environment().putIfAbsent("PORT", String.valueOf(DEFAULT_BACKEND_PORT));

            Process process = builder.start();
            Thread reader = new Thread(() -> readProcessLines(process, lines), "codemind-acceptance-log-reader");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                timedOut = true;
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } else {
                exitCode = process.exitValue();
            }
            reader.join(1200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lines.add("命令执行被中断：" + e.getMessage());
            timedOut = true;
        } catch (IOException e) {
            lines.add("命令启动失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
            exitCode = -1;
        }

        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        boolean passed = !timedOut && exitCode != null && exitCode == 0;
        String status = timedOut ? "TIMEOUT" : (passed ? "PASS" : "FAILED");
        String summary;
        if (passed) {
            summary = title + "通过。";
        } else if (timedOut) {
            summary = title + "超时，已终止命令。";
        } else {
            summary = title + "失败，退出码：" + (exitCode == null ? "-" : exitCode) + "。";
        }

        return new DevelopmentCodebaseAcceptanceItemResponse(
                id,
                phase,
                title,
                status,
                required,
                passed,
                true,
                commandText,
                exitCode,
                durationMillis,
                summary,
                List.of("工作目录：" + root, "超时限制：" + timeoutSeconds + " 秒"),
                tail(lines, 80)
        );
    }

    private void readProcessLines(Process process, List<String> lines) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                while (lines.size() > 600) {
                    lines.remove(0);
                }
            }
        } catch (IOException e) {
            lines.add("读取命令日志失败：" + e.getMessage());
        }
    }

    private DevelopmentCodebaseAcceptanceItemResponse probeItem(
            String id,
            String phase,
            String title,
            boolean required,
            Probe probe,
            String url
    ) {
        List<String> details = new ArrayList<>();
        details.add(url);
        if (probe.statusCode() != null) {
            details.add("HTTP " + probe.statusCode());
        }
        details.add(probe.message());

        return new DevelopmentCodebaseAcceptanceItemResponse(
                id,
                phase,
                title,
                probe.reachable() ? "PASS" : (required ? "FAILED" : "WARNING"),
                required,
                probe.reachable() || !required,
                false,
                null,
                null,
                0L,
                probe.reachable() ? title + "通过。" : title + "未通过：" + probe.message(),
                details,
                List.of()
        );
    }

    private Probe probe(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(1200))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            boolean reachable = status >= 200 && status < 400;
            return new Probe(reachable, status, reachable ? "可访问" : "HTTP " + status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Probe(false, null, "健康检查被中断");
        } catch (Exception e) {
            return new Probe(false, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean hasScript(Path packageJson, String scriptName) {
        try {
            Map<String, Object> json = objectMapper.readValue(packageJson.toFile(), new TypeReference<Map<String, Object>>() {
            });
            Object scripts = json.get("scripts");
            if (!(scripts instanceof Map<?, ?> scriptMap)) {
                return false;
            }
            Object value = scriptMap.get(scriptName);
            return value instanceof String script && !script.isBlank();
        } catch (Exception e) {
            return false;
        }
    }



    private DevelopmentCodebaseAcceptanceItemResponse runRuntimeApiDatabaseSmoke(Path root) {
        long startedNanos = System.nanoTime();
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        String commandText = "node backend/server.js + HTTP POST/GET/DELETE /api/codemindAcceptanceProbe";
        Process process = null;
        Thread reader = null;
        boolean startedBackend = false;
        boolean passed = false;
        String summary = "后端 HTTP API 到数据库读写链路尚未执行。";

        try {
            Probe existingBackend = probe(healthUrl());

            if (existingBackend.reachable()) {
                lines.add("检测到后端健康检查已可访问，直接执行 HTTP API + DB 端到端探针：" + healthUrl());
            } else {
                ProcessBuilder builder = new ProcessBuilder(platformCommand(List.of("node", "backend/server.js")));
                builder.directory(root.toFile());
                builder.redirectErrorStream(true);
                builder.environment().putIfAbsent("CODEMIND_ACCEPTANCE", "true");
                builder.environment().putIfAbsent("PORT", String.valueOf(DEFAULT_BACKEND_PORT));

                process = builder.start();
                startedBackend = true;
                lines.add("已启动临时后端进程：node backend/server.js");
                lines.add("等待后端健康检查：" + healthUrl());

                Process finalProcess = process;
                reader = new Thread(() -> readProcessLines(finalProcess, lines), "codemind-runtime-api-db-smoke-log-reader");
                reader.setDaemon(true);
                reader.start();

                Probe backendReady = waitForProbe(healthUrl(), 15_000L);

                if (!backendReady.reachable()) {
                    Integer exitCode = null;
                    if (!process.isAlive()) {
                        exitCode = process.exitValue();
                    }

                    throw new IllegalStateException(
                            "后端健康检查未通过：" + backendReady.message()
                                    + (exitCode == null ? "" : "；临时后端进程退出码：" + exitCode)
                    );
                }

                lines.add("后端健康检查通过：" + backendReady.message());
            }

            RuntimeApiDatabaseSmokeResult result = runRuntimeApiDatabaseRoundTrip();
            lines.addAll(result.logs());
            summary = result.summary();
            passed = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            summary = "后端 HTTP API 到数据库读写链路被中断。";
            lines.add(summary + " " + e.getMessage());
        } catch (Exception e) {
            summary = "后端 HTTP API 到数据库读写链路失败：" + e.getClass().getSimpleName() + ": " + e.getMessage();
            lines.add(summary);
        } finally {
            if (startedBackend && process != null) {
                stopAcceptanceProcess(process, lines);
            }

            if (reader != null) {
                try {
                    reader.join(1200L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    lines.add("等待后端日志读取线程结束时被中断。");
                }
            }
        }

        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        return new DevelopmentCodebaseAcceptanceItemResponse(
                "RUNTIME_API_DATABASE_SMOKE",
                "RUNTIME",
                "后端 API 与数据库端到端验收",
                passed ? "PASS" : "FAILED",
                true,
                passed,
                true,
                commandText,
                passed ? 0 : 1,
                durationMillis,
                summary,
                List.of(
                        "健康检查：" + healthUrl(),
                        "写入接口：POST " + backendUrl() + "/api/codemindAcceptanceProbe",
                        "读回接口：GET " + backendUrl() + "/api/codemindAcceptanceProbe",
                        "清理接口：DELETE " + backendUrl() + "/api/codemindAcceptanceProbe/{id}"
                ),
                tail(lines, 100)
        );
    }

    private Probe waitForProbe(String url, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Probe last = new Probe(false, null, "尚未检查");

        while (System.nanoTime() < deadline) {
            last = probe(url);

            if (last.reachable()) {
                return last;
            }

            Thread.sleep(500L);
        }

        return last;
    }

    private RuntimeApiDatabaseSmokeResult runRuntimeApiDatabaseRoundTrip() throws IOException, InterruptedException {
        List<String> logs = new ArrayList<>();
        String marker = "probe-" + UUID.randomUUID();
        String endpoint = backendUrl() + "/api/codemindAcceptanceProbe";
        String deleteEndpoint = endpoint + "/" + marker;

        Map<String, Object> payload = Map.of(
                "id", marker,
                "title", "CodeMind runtime API database smoke",
                "status", "ACTIVE"
        );

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> createResponse = httpClient.send(
                createRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ensureHttpOk(createResponse, "POST 创建记录");
        Object createdData = responseData(createResponse, "POST 创建记录");
        if (!containsRecordWithId(createdData, marker)) {
            throw new IllegalStateException("POST 响应中没有返回刚创建的记录 ID：" + marker);
        }
        logs.add("POST 创建记录通过，ID=" + marker + "，HTTP " + createResponse.statusCode());

        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> listResponse = httpClient.send(
                listRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ensureHttpOk(listResponse, "GET 读回记录");
        Object listData = responseData(listResponse, "GET 读回记录");
        if (!containsRecordWithId(listData, marker)) {
            throw new IllegalStateException("GET 读回数据中没有找到刚写入的记录 ID：" + marker);
        }
        logs.add("GET 读回记录通过，确认数据库已持久化探针记录。");

        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(deleteEndpoint))
                .timeout(Duration.ofSeconds(5))
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = httpClient.send(
                deleteRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ensureHttpOk(deleteResponse, "DELETE 清理记录");
        logs.add("DELETE 清理记录通过，探针记录已删除。");

        return new RuntimeApiDatabaseSmokeResult(
                "后端 HTTP API 与数据库读写端到端验收通过：health → POST 写入 → GET 读回 → DELETE 清理。",
                logs
        );
    }

    private void ensureHttpOk(HttpResponse<String> response, String label) {
        if (response == null) {
            throw new IllegalStateException(label + "没有返回响应。");
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(label + "失败：HTTP " + statusCode + "，响应：" + safeBody(response.body()));
        }
    }

    private Object responseData(HttpResponse<String> response, String label) {
        Map<String, Object> body = parseJsonObject(response.body(), label);

        Object success = body.get("success");
        if (Boolean.FALSE.equals(success)) {
            throw new IllegalStateException(label + "返回 success=false：" + safeBody(response.body()));
        }

        if (!body.containsKey("data")) {
            throw new IllegalStateException(label + "响应缺少 data 字段：" + safeBody(response.body()));
        }

        return body.get("data");
    }

    private Map<String, Object> parseJsonObject(String body, String label) {
        try {
            return objectMapper.readValue(
                    body == null || body.isBlank() ? "{}" : body,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (IOException e) {
            throw new IllegalStateException(label + "响应不是合法 JSON：" + safeBody(body), e);
        }
    }

    private boolean containsRecordWithId(Object value, String expectedId) {
        if (value instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (expectedId.equals(String.valueOf(id))) {
                return true;
            }

            Object data = map.get("data");
            return data != null && containsRecordWithId(data, expectedId);
        }

        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (containsRecordWithId(item, expectedId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String safeBody(String body) {
        if (body == null) {
            return "";
        }

        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private void stopAcceptanceProcess(Process process, List<String> lines) {
        if (process == null) {
            return;
        }

        try {
            ProcessHandle handle = process.toHandle();
            handle.descendants()
                    .forEach(child -> {
                        try {
                            child.destroy();
                        } catch (Exception ignored) {
                        }
                    });

            process.destroy();

            if (!process.waitFor(1800L, TimeUnit.MILLISECONDS)) {
                handle.descendants()
                        .forEach(child -> {
                            try {
                                child.destroyForcibly();
                            } catch (Exception ignored) {
                            }
                        });
                process.destroyForcibly();
            }

            lines.add("临时后端进程已停止。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lines.add("停止临时后端进程时被中断。");
        } catch (Exception e) {
            lines.add("停止临时后端进程失败：" + e.getMessage());
        }
    }

    private String writeDatabaseAcceptanceProbeScript(Path root) {
        Path script = root
                .resolve(".codemind")
                .resolve("acceptance")
                .resolve("database-read-write-probe.mjs");

        String content = String.join(System.lineSeparator(), List.of(
                "import { readStore, writeStore } from '../../backend/src/db/localStore.js'",
                "",
                "const bucketKey = '__codemindAcceptanceProbe'",
                "const marker = `probe-${Date.now()}-${Math.random().toString(16).slice(2)}`",
                "const store = await readStore()",
                "",
                "if (!store || typeof store !== 'object' || Array.isArray(store)) {",
                "  throw new Error('readStore() did not return an object store')",
                "}",
                "",
                "const bucket = Array.isArray(store[bucketKey]) ? [...store[bucketKey]] : []",
                "bucket.push({",
                "  id: marker,",
                "  title: 'CodeMind acceptance database probe',",
                "  status: 'ACTIVE',",
                "  createdAt: new Date().toISOString(),",
                "})",
                "",
                "await writeStore({",
                "  ...store,",
                "  [bucketKey]: bucket,",
                "})",
                "",
                "const afterWrite = await readStore()",
                "const exists = Array.isArray(afterWrite[bucketKey])",
                "  && afterWrite[bucketKey].some((item) => item && item.id === marker)",
                "",
                "if (!exists) {",
                "  throw new Error('Database write/read verification failed')",
                "}",
                "",
                "await writeStore({",
                "  ...afterWrite,",
                "  [bucketKey]: afterWrite[bucketKey].filter((item) => item && item.id !== marker),",
                "})",
                "",
                "console.log('Database probe passed: connect/read/write/read-back/cleanup OK')"
        ));

        try {
            if (script.getParent() != null) {
                Files.createDirectories(script.getParent());
            }
            Files.writeString(script, content + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "写入数据库验收探针脚本失败。",
                    e
            );
        }

        return root.relativize(script).toString().replace("\\", "/");
    }

    private List<String> platformCommand(List<String> command) {
        String joined = String.join(" ", command);
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return List.of("cmd", "/c", joined);
        }
        return List.of("bash", "-lc", joined);
    }

    private List<String> tail(List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, lines.size() - max);
        return new ArrayList<>(lines.subList(from, lines.size()));
    }

    private boolean enabled(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void addUnique(List<String> target, String value) {
        if (value == null || value.isBlank() || target.contains(value)) {
            return;
        }
        target.add(value);
    }

    private String resolveTargetStack(String targetStack) {
        try {
            return DevelopmentTargetStack.resolve(targetStack).stackCode();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private String buildSummary(String status, boolean executed, List<String> blockers, List<String> warnings) {
        if (!blockers.isEmpty()) {
            return "验收未通过：" + String.join("；", blockers);
        }
        if (!executed) {
            return "代码库预检查通过，可以运行 npm install / build / test 验收流水线。";
        }
        if ("PASS".equals(status)) {
            return "生成项目已通过安装、构建、数据库连接/读写、后端 HTTP API 端到端读写、测试和基础检查，可以作为交付候选代码包。";
        }
        if ("WARNING".equals(status)) {
            return warnings.isEmpty()
                    ? "核心验收通过，但存在非阻断提示。"
                    : "核心验收通过，但仍有提示：" + String.join("；", warnings);
        }
        return "验收状态：" + status;
    }

    private String frontendUrl() {
        return "http://127.0.0.1:" + DEFAULT_FRONTEND_PORT;
    }

    private String backendUrl() {
        return "http://127.0.0.1:" + DEFAULT_BACKEND_PORT;
    }

    private String healthUrl() {
        return backendUrl() + "/api/health";
    }


    private record RuntimeApiDatabaseSmokeResult(
            String summary,
            List<String> logs
    ) {
    }

    private record AcceptanceContext(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack,
            DevelopmentHandoffReadinessResponse readiness,
            DevelopmentCodebaseRegistryResponse codebaseRegistry,
            Path absoluteCodebaseRoot
    ) {
    }

    private record Probe(
            boolean reachable,
            Integer statusCode,
            String message
    ) {
    }
}
