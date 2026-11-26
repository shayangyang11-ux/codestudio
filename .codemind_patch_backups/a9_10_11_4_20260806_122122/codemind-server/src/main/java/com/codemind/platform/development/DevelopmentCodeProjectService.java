package com.codemind.platform.development;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DevelopmentCodeProjectService {

    private final DevelopmentPlanService developmentPlanService;
    private final DevelopmentGeneratedPathService generatedPathService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final DevelopmentCodebaseSnapshotService codebaseSnapshotService;
    private final DevelopmentAiCodegenProposalService aiCodegenProposalService;
    private final ObjectMapper objectMapper;

    public DevelopmentCodeProjectService(
            DevelopmentPlanService developmentPlanService,
            DevelopmentGeneratedPathService generatedPathService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            DevelopmentCodebaseSnapshotService codebaseSnapshotService,
            DevelopmentAiCodegenProposalService aiCodegenProposalService,
            ObjectMapper objectMapper
    ) {
        this.developmentPlanService = developmentPlanService;
        this.generatedPathService = generatedPathService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.codebaseSnapshotService = codebaseSnapshotService;
        this.aiCodegenProposalService = aiCodegenProposalService;
        this.objectMapper = objectMapper;
    }

    public DevelopmentCodeProjectResponse generateCodeProject(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateDevelopmentCodeProjectRequest request
    ) {
        String targetStack = request == null ? null : request.targetStack();

        DevelopmentPlanResponse plan = developmentPlanService.generatePlan(
                workspaceId,
                projectCode,
                moduleId,
                new GenerateDevelopmentPlanRequest(
                        request == null ? null : request.planScope(),
                        targetStack,
                        request == null ? null : request.remarks()
                )
        );

        if (!"NODEJS_FULLSTACK".equals(plan.targetStack())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前代码生成第一版只支持 NODEJS_FULLSTACK。"
            );
        }

        DevelopmentHandoffContextResponse context = plan.handoffContext();
        List<String> preflightWarnings = buildCodegenPreflightWarnings(context);

        if (context == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "缺少 Development Build Context，不能生成 Node.js 代码贡献提案。"
            );
        }

        String nodeType = safeUpper(plan.nodeType());

        if (!"MODULE".equals(nodeType) && !"PAGE".equals(nodeType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前节点作为开发上下文或父页面内联需求使用，不直接生成独立代码。请选择 MODULE 或 PAGE 生成 Node.js 项目代码贡献。"
            );
        }

        String nodeName = safeText(plan.moduleName(), "当前节点");
        String nodeKey = safeText(plan.moduleKey(), nodeName);
        String classBaseName = toClassName(nodeKey, nodeName);
        String kebabBaseName = toKebabCase(nodeKey, nodeName);
        String moduleSegment = toPathSegment(nodeKey, nodeName);
        String codebaseRoot = buildCodebaseRoot(projectCode);
        String pageTitle = buildPageTitle(context, nodeName);
        String domainSummary = buildDomainSummary(context);
        String previewRoute = buildPreviewRoute(nodeType, moduleSegment);

        List<DevelopmentCodeFileResponse> files = new ArrayList<>();

        addCodebaseSkeleton(files, codebaseRoot, projectCode, nodeName, pageTitle, domainSummary);
        addNodeContributionFiles(files, codebaseRoot, nodeType, moduleSegment, classBaseName, kebabBaseName, pageTitle, domainSummary);

        DevelopmentAiCodegenProposalService.AiCodegenResult aiCodegenResult = aiCodegenProposalService.refineNodejsCodeContribution(
                workspaceId,
                projectCode,
                moduleId,
                plan,
                context,
                codebaseRoot,
                nodeType,
                nodeName,
                previewRoute,
                files,
                request == null ? null : request.remarks()
        );

        files = new ArrayList<>(aiCodegenResult.files());
        addManifestFile(files, codebaseRoot, plan, moduleId, nodeType, nodeName, previewRoute);

        List<String> warnings = new ArrayList<>();
        warnings.addAll(preflightWarnings);
        warnings.add("本次输出为 Node.js 项目主干中的“当前节点代码贡献”提案，不再为每个节点生成独立项目。");
        warnings.add("代码统一写入 generated/nodejs/{projectCode}/app，并通过 codemind.generated.json 记录模块树节点与代码文件的映射关系。");
        warnings.add("DOMAIN、SUBMODULE、FEATURE 等结构或内联节点不会直接生成独立代码；正式代码生成入口仅保留 MODULE/PAGE，FEATURE 随父 PAGE 写入。");
        warnings.add("seed data 仅用于首次运行演示和验收测试，不作为前端 Mock 主业务链路。前端必须请求 Node.js 后端，后端必须访问本地数据层。");
        warnings.add("M2 已启用 Development Agent AI Codegen：Java 先生成受控模板，再由 Python Dev Codegen Agent 使用 development.codegen 模型路由进行代码提案增强；如果 Agent 不可用会明确失败，不再用模板冒充业务代码。");
        warnings.addAll(aiCodegenResult.warnings());

        String summary = aiCodegenResult.aiGenerated()
                ? "已由 Development Agent 模型生成“" + nodeName + "”的 Node.js 代码贡献提案。运行单位是整个 generated/nodejs/" + toPathSegment(projectCode, "project") + "/app 项目，当前 MODULE/PAGE 只贡献其负责的模块或页面代码。"
                : "仅生成工程脚手架，Development Codegen Agent 未完成“" + nodeName + "”的 Node.js 代码贡献提案。运行单位是整个 generated/nodejs/" + toPathSegment(projectCode, "project") + "/app 项目，当前 MODULE/PAGE 只贡献其负责的模块或页面代码。";

        if (aiCodegenResult.summary() != null && !aiCodegenResult.summary().isBlank()) {
            summary = summary + " " + aiCodegenResult.summary();
        }
        String checksum = calculateChecksum(plan, files, warnings, summary);

        return new DevelopmentCodeProjectResponse(
                plan.projectId(),
                plan.moduleId(),
                plan.moduleKey(),
                plan.moduleName(),
                plan.moduleType(),
                plan.nodeType(),
                plan.targetStack(),
                plan.targetStackLabel(),
                aiCodegenResult.generatorMode(),
                aiCodegenResult.aiGenerated() ? "AI_GENERATED" : "GENERATED",
                checksum,
                LocalDateTime.now(),
                codebaseRoot,
                summary,
                runtimeCommands(),
                plan.sourceArtifactTypes(),
                warnings,
                files,
                plan,
                context,
                buildCodegenTrace(aiCodegenResult, aiCodegenResult.aiGenerated() ? "AI_GENERATED" : "GENERATED", files.size())
        );
    }

    public DevelopmentCodeMaterializationResponse materializeCodeProject(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            MaterializeDevelopmentCodeProjectRequest request
    ) {
        MaterializeDevelopmentCodeProjectRequest safeRequest = request == null
                ? new MaterializeDevelopmentCodeProjectRequest(null, null, null, false)
                : request;

        DevelopmentCodeProjectResponse codeProject = generateCodeProject(
                workspaceId,
                projectCode,
                moduleId,
                new GenerateDevelopmentCodeProjectRequest(
                        safeRequest.planScope(),
                        safeRequest.targetStack(),
                        safeRequest.remarks()
                )
        );

        return materializePreparedCodeProject(
                workspaceId,
                projectCode,
                moduleId,
                codeProject,
                Boolean.TRUE.equals(safeRequest.allowOverwrite()),
                "CODE_PROJECT_MATERIALIZE"
        );
    }

    public DevelopmentCodeMaterializationResponse materializePreparedCodeProject(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodeProjectResponse codeProject,
            boolean allowOverwrite,
            String operationReason
    ) {
        if (codeProject == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少可写入的代码提案。");
        }

        Path workspaceRoot = generatedPathService.resolveGeneratedStorageRoot();
        Path generationRoot = resolveSafeGeneratedPath(workspaceRoot, codeProject.generationRoot());
        DevelopmentCodebaseSafetyCheckResponse safetyCheck = codebaseSnapshotService.analyzeCodeProject(
                generationRoot,
                codeProject,
                allowOverwrite
        );
        DevelopmentCodebaseSnapshotResponse snapshot = codebaseSnapshotService.createSnapshotIfNeeded(
                generationRoot,
                projectCode,
                codeProject.targetStack(),
                safeText(operationReason, allowOverwrite ? "OVERWRITE_BEFORE_MATERIALIZE" : "SAFE_MATERIALIZE_BEFORE_WRITE")
        );
        Map<String, DevelopmentCodebaseSafetyFileResponse> safetyByPath = new LinkedHashMap<>();

        for (DevelopmentCodebaseSafetyFileResponse safetyFile : safetyCheck.files()) {
            safetyByPath.put(safetyFile.filePath(), safetyFile);
        }

        List<DevelopmentCodeMaterializedFileResponse> materializedFiles = new ArrayList<>();
        int writtenCount = 0;
        int skippedCount = 0;

        for (DevelopmentCodeFileResponse file : codeProject.files()) {
            DevelopmentCodeMaterializedFileResponse result = file.filePath().endsWith("/codemind.generated.json")
                    ? materializeManifestFile(workspaceRoot, file, allowOverwrite)
                    : materializeOneFile(workspaceRoot, file, allowOverwrite, safetyByPath.get(file.filePath()));

            materializedFiles.add(result);

            if ("WRITTEN".equals(result.status()) || "OVERWRITTEN".equals(result.status()) || "MERGED".equals(result.status())) {
                writtenCount++;
            } else {
                skippedCount++;
            }
        }

        codebaseSnapshotService.refreshManifestGeneratedFiles(generationRoot, codeProject, materializedFiles);

        int conflictCount = safetyCheck.conflictCount();
        String status;

        if (conflictCount > 0 && !allowOverwrite) {
            status = writtenCount > 0 ? "PARTIAL_MATERIALIZED_WITH_CONFLICTS" : "BLOCKED_BY_CONFLICTS";
        } else if (skippedCount == 0) {
            status = "MATERIALIZED";
        } else {
            status = "PARTIAL_MATERIALIZED";
        }

        List<String> warnings = new ArrayList<>(codeProject.warnings() == null ? List.of() : codeProject.warnings());
        warnings.add("代码已写入 generated/nodejs/{projectCode}/app；该动作只写入生成目录，不覆盖平台主工程源码。");
        warnings.add("R7 已启用安全再生成：写入前会创建代码快照，并基于 SHA-256 判断文件是否可能被用户修改。");

        if (snapshot != null) {
            warnings.add("已创建写入前快照：" + snapshot.snapshotId() + "，可用于后续回滚或审计。 ");
        }

        if (conflictCount > 0 && !allowOverwrite) {
            warnings.add("检测到 " + conflictCount + " 个冲突文件，默认未覆盖。确认需要覆盖时再使用覆盖写入。 ");
        }

        if (skippedCount > 0 && !allowOverwrite) {
            warnings.add("部分文件因保留策略、内容未变化或冲突保护被跳过；codemind.generated.json 会继续合并当前节点映射。 ");
        }

        DevelopmentCodebaseRegistryResponse codebaseRegistry = codebaseRegistryService.registerMaterializedCodebase(
                workspaceId,
                codeProject.projectId(),
                projectCode,
                codeProject.targetStack(),
                status,
                codeProject.generationRoot(),
                generationRoot,
                codeProject,
                materializedFiles
        );

        return new DevelopmentCodeMaterializationResponse(
                codeProject.projectId(),
                codeProject.moduleId(),
                codeProject.moduleKey(),
                codeProject.moduleName(),
                codeProject.targetStack(),
                status,
                LocalDateTime.now(),
                workspaceRoot.toString(),
                codeProject.generationRoot(),
                generationRoot.toString(),
                allowOverwrite,
                writtenCount,
                skippedCount,
                safetyCheck.addedCount(),
                safetyCheck.modifiedCount(),
                safetyCheck.unchangedCount(),
                safetyCheck.conflictCount(),
                warnings,
                materializedFiles,
                codeProject,
                codebaseRegistry,
                snapshot,
                safetyCheck
        );
    }

    private void addCodebaseSkeleton(
            List<DevelopmentCodeFileResponse> files,
            String root,
            String projectCode,
            String nodeName,
            String pageTitle,
            String domainSummary
    ) {
        String appName = "codemind-" + toPathSegment(projectCode, "project") + "-nodejs-app";

        add(files, root + "/package.json", "CONFIG", "CREATE_OR_UPDATE", "DEV_HANDOFF_PACKAGE", "Node.js 全栈项目主干根 package.json，统一管理 frontend/backend 工作区和启动命令。", "json", 1,
                """
                {
                  "name": "%s",
                  "private": true,
                  "version": "0.1.0",
                  "type": "module",
                  "workspaces": [
                    "frontend",
                    "backend"
                  ],
                  "scripts": {
                    "dev": "concurrently \"npm run dev --workspace frontend\" \"npm run dev --workspace backend\"",
                    "dev:frontend": "npm run dev --workspace frontend",
                    "dev:backend": "npm run dev --workspace backend",
                    "build": "npm run build --workspace frontend",
                    "test": "node tests/smoke.spec.js",
                    "acceptance": "npm run build && npm test"
                  },
                  "devDependencies": {
                    "concurrently": "^8.2.2"
                  }
                }
                """.formatted(appName));

        add(files, root + "/README.md", "DOC", "CREATE_OR_UPDATE", "DEV_HANDOFF_PACKAGE", "生成项目说明、目录说明、运行方式和模块树到代码的映射说明。", "markdown", 2,
                """
                # CodeMind Node.js 全栈生成项目

                本目录是 Dev Agent 生成的 Node.js 全栈代码主干。所有模块树节点的代码贡献都会进入同一个 app，而不是为每个节点生成孤立项目。

                ## 运行方式

                ```powershell
                npm install
                npm run dev
                npm run build
                npm test
                ```

                默认地址：

                - 前端：http://127.0.0.1:5178
                - 后端：http://127.0.0.1:3101
                - 健康检查：http://127.0.0.1:3101/api/health

                ## 当前贡献

                当前节点：%s
                当前页面/场景：%s

                %s

                ## 代码映射

                `codemind.generated.json` 记录模块树节点与代码文件的映射关系。平台会基于它实现：

                - 按项目目录查看完整代码
                - 按模块树节点查看相关代码
                - 定位页面或功能场景预览入口
                - 判断代码是否需要重新生成
                """.formatted(nodeName, pageTitle, domainSummary));

        add(files, root + "/frontend/package.json", "CONFIG", "CREATE_OR_UPDATE", "DEV_HANDOFF_PACKAGE", "Vite + Vue 前端工程依赖与启动脚本。", "json", 3,
                """
                {
                  "name": "codemind-generated-frontend",
                  "private": true,
                  "version": "0.1.0",
                  "type": "module",
                  "scripts": {
                    "dev": "vite --host 0.0.0.0 --port 5178",
                    "build": "vite build",
                    "preview": "vite preview --host 0.0.0.0 --port 5178"
                  },
                  "dependencies": {
                    "@vitejs/plugin-vue": "^5.0.5",
                    "vite": "^5.4.10",
                    "vue": "^3.5.12"
                  },
                  "devDependencies": {}
                }
                """);

        add(files, root + "/frontend/index.html", "FRONTEND", "CREATE_OR_KEEP", "UI_CONTRACT", "前端页面入口。", "html", 4,
                """
                <!doctype html>
                <html lang="zh-CN">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>CodeMind Generated App</title>
                  </head>
                  <body>
                    <div id="app"></div>
                    <script type="module" src="/src/main.js"></script>
                  </body>
                </html>
                """);

        add(files, root + "/frontend/vite.config.js", "CONFIG", "CREATE_OR_KEEP", "DEV_HANDOFF_PACKAGE", "Vite 开发代理配置，前端统一通过 /api 请求后端。", "javascript", 5,
                """
                import { defineConfig } from 'vite'
                import vue from '@vitejs/plugin-vue'

                export default defineConfig({
                  plugins: [vue()],
                  server: {
                    proxy: {
                      '/api': 'http://127.0.0.1:3101',
                    },
                  },
                })
                """);

        add(files, root + "/frontend/src/main.js", "FRONTEND", "CREATE_OR_KEEP", "UI_CONTRACT", "挂载 Vue 应用，并接入路由与基础样式。", "javascript", 6,
                """
                import { createApp } from 'vue'
                import App from './App.vue'
                import './shared/styles/app.css'

                createApp(App).mount('#app')
                """);

        add(files, root + "/frontend/src/App.vue", "FRONTEND", "CREATE_OR_KEEP", "UI_CONTRACT + DEV_HANDOFF_PACKAGE", "应用壳层，承载当前生成系统和模块页面。", "vue", 7,
                """
                <template>
                  <main class="app-shell">
                    <aside class="app-sidebar">
                      <strong>CodeMind Generated App</strong>
                      <span>Node.js Fullstack</span>
                    </aside>
                    <section class="app-content">
                      <component :is="activePage" />
                    </section>
                  </main>
                </template>

                <script setup>
                import { computed } from 'vue'
                import { generatedRoutes } from './router/routes.js'

                const activePage = computed(() => {
                  const pathname = window.location.pathname || '/'
                  return generatedRoutes.find((route) => route.path === pathname)?.component || generatedRoutes[0]?.component
                })
                </script>
                """);

        add(files, root + "/frontend/src/router/routes.js", "FRONTEND", "CREATE_OR_UPDATE", "PAGE_SPEC + UI_CONTRACT", "生成页面路由聚合入口；PAGE 节点贡献具体页面路由。", "javascript", 8,
                """
                import GeneratedHomePage from '../modules/generated/pages/GeneratedHomePage.vue'

                export const generatedRoutes = [
                  {
                    path: '/',
                    name: 'GeneratedHome',
                    component: GeneratedHomePage,
                  },
                ]
                """);

        add(files, root + "/frontend/src/modules/generated/pages/GeneratedHomePage.vue", "FRONTEND", "CREATE_OR_KEEP", "DEV_HANDOFF_PACKAGE", "生成项目默认首页。", "vue", 9,
                """
                <template>
                  <section class="page-card">
                    <p class="eyebrow">Generated Project</p>
                    <h1>Node.js 全栈应用已初始化</h1>
                    <p>请继续在模块树中选择 PAGE 或 FEATURE 节点生成具体页面和功能代码。</p>
                  </section>
                </template>
                """);

        add(files, root + "/frontend/src/shared/styles/app.css", "FRONTEND", "CREATE_OR_UPDATE", "UI_CONTRACT + THEME_PROFILE_SNAPSHOT", "应用基础样式，后续 PAGE/FEATURE 只补充必要样式，不重复全局样式。", "css", 10,
                """
                * { box-sizing: border-box; }
                body { margin: 0; font-family: Inter, 'Microsoft YaHei', Arial, sans-serif; background: #f8fafc; color: #0f172a; }
                .app-shell { min-height: 100vh; display: grid; grid-template-columns: 240px minmax(0, 1fr); }
                .app-sidebar { padding: 24px; background: #0f172a; color: #fff; display: flex; flex-direction: column; gap: 8px; }
                .app-sidebar span { color: #cbd5e1; font-size: 13px; }
                .app-content { padding: 32px; }
                .page-card { max-width: 960px; margin: 0 auto; padding: 28px; border: 1px solid #e2e8f0; border-radius: 22px; background: #fff; box-shadow: 0 20px 50px rgba(15,23,42,.08); }
                .eyebrow { margin: 0 0 8px; color: #2563eb; font-weight: 900; letter-spacing: .06em; text-transform: uppercase; font-size: 12px; }
                .form-grid { display: grid; gap: 14px; margin-top: 18px; }
                .form-grid label { display: grid; gap: 6px; font-weight: 800; color: #334155; }
                .form-grid input, .form-grid textarea { border: 1px solid #cbd5e1; border-radius: 12px; padding: 12px; font: inherit; }
                .primary-action { border: 0; border-radius: 12px; padding: 12px 16px; background: #2563eb; color: #fff; font-weight: 900; cursor: pointer; }
                .status-message { margin-top: 14px; color: #475569; }
                """);

        add(files, root + "/backend/package.json", "CONFIG", "CREATE_OR_KEEP", "DEV_HANDOFF_PACKAGE", "Express 后端工程依赖与启动脚本。", "json", 11,
                """
                {
                  "name": "codemind-generated-backend",
                  "private": true,
                  "version": "0.1.0",
                  "type": "module",
                  "scripts": {
                    "dev": "node --watch server.js",
                    "start": "node server.js"
                  },
                  "dependencies": {
                    "cors": "^2.8.5",
                    "express": "^4.19.2"
                  }
                }
                """);

        add(files, root + "/backend/server.js", "BACKEND", "CREATE_OR_KEEP", "API_CONTRACT + DEV_HANDOFF_PACKAGE", "Express 服务入口。", "javascript", 12,
                """
                import { createApp } from './src/app.js'

                const port = Number(process.env.PORT || 3101)
                const app = createApp()

                app.listen(port, () => {
                  console.log(`Generated backend listening on http://127.0.0.1:${port}`)
                })
                """);

        add(files, root + "/backend/src/app.js", "BACKEND", "CREATE_OR_KEEP", "API_CONTRACT", "创建 Express 应用，统一接入健康检查、业务路由和错误处理。", "javascript", 13,
                """
                import express from 'express'
                import cors from 'cors'
                import { registerGeneratedRoutes } from './routes/index.js'
                import { sendError } from './shared/response.js'

                export function createApp() {
                  const app = express()
                  app.use(cors())
                  app.use(express.json())

                  app.get('/api/health', (_req, res) => {
                    res.json({ ok: true, service: 'codemind-generated-backend' })
                  })

                  registerGeneratedRoutes(app)

                  app.use((error, _req, res, _next) => {
                    sendError(res, error)
                  })

                  return app
                }
                """);

        add(files, root + "/backend/src/routes/index.js", "BACKEND", "CREATE_OR_UPDATE", "API_CONTRACT", "后端业务路由聚合入口。", "javascript", 14,
                """
                import { readStore, writeStore } from '../db/localStore.js'
                import { sendOk } from '../shared/response.js'

                export function registerGeneratedRoutes(app) {
                  app.get('/api/:moduleKey', async (req, res, next) => {
                    try {
                      const store = await readStore()
                      const bucket = ensureBucket(store, req.params.moduleKey)
                      sendOk(res, bucket)
                    } catch (error) {
                      next(error)
                    }
                  })

                  app.post('/api/:moduleKey', async (req, res, next) => {
                    try {
                      const store = await readStore()
                      const bucket = ensureBucket(store, req.params.moduleKey)
                      const nextRecord = {
                        id: `${normalizeKey(req.params.moduleKey)}-${Date.now()}`,
                        ...(req.body || {}),
                        status: req.body?.status || 'ACTIVE',
                        createdAt: new Date().toISOString(),
                      }

                      bucket.push(nextRecord)
                      await writeStore(store)
                      sendOk(res, nextRecord)
                    } catch (error) {
                      next(error)
                    }
                  })

                  app.post('/api/:moduleKey/actions/:actionKey', async (req, res, next) => {
                    try {
                      const store = await readStore()
                      const bucket = ensureBucket(store, req.params.moduleKey)
                      const actionRecord = {
                        id: `${normalizeKey(req.params.actionKey)}-${Date.now()}`,
                        action: req.params.actionKey,
                        ...(req.body || {}),
                        status: 'DONE',
                        createdAt: new Date().toISOString(),
                      }

                      bucket.push(actionRecord)
                      await writeStore(store)
                      sendOk(res, actionRecord, { action: req.params.actionKey })
                    } catch (error) {
                      next(error)
                    }
                  })
                }

                function ensureBucket(store, moduleKey) {
                  const bucketKey = normalizeKey(moduleKey)

                  if (!Array.isArray(store[bucketKey])) {
                    store[bucketKey] = []
                  }

                  return store[bucketKey]
                }

                function normalizeKey(value) {
                  return String(value || 'records').replace(/[^a-zA-Z0-9_]/g, '_') || 'records'
                }
                """);

        add(files, root + "/backend/src/shared/response.js", "BACKEND", "CREATE_OR_KEEP", "API_CONTRACT", "统一响应和错误输出。", "javascript", 15,
                """
                export function sendOk(res, data = {}, meta = {}) {
                  res.json({ success: true, data, meta })
                }

                export function sendError(res, error) {
                  const status = Number(error?.status || 500)
                  res.status(status).json({
                    success: false,
                    error: {
                      message: error?.message || 'Internal server error',
                    },
                  })
                }
                """);

        add(files, root + "/backend/src/db/localStore.js", "BACKEND", "CREATE_OR_KEEP", "DATA_MODEL", "纯 JavaScript 本地数据存储，避免原生 sqlite 依赖编译。后续可替换为 SQLite/PostgreSQL 适配器。", "javascript", 16,
                """
                import { promises as fs } from 'fs'
                import path from 'path'
                import { fileURLToPath } from 'url'
                import { seedData } from './seedData.js'

                const __filename = fileURLToPath(import.meta.url)
                const __dirname = path.dirname(__filename)
                const dataFile = path.resolve(__dirname, '../../data/local-store.json')

                export async function readStore() {
                  try {
                    const content = await fs.readFile(dataFile, 'utf-8')
                    return JSON.parse(content)
                  } catch (_error) {
                    await writeStore(seedData)
                    return structuredClone(seedData)
                  }
                }

                export async function writeStore(nextStore) {
                  await fs.mkdir(path.dirname(dataFile), { recursive: true })
                  await fs.writeFile(dataFile, JSON.stringify(nextStore, null, 2), 'utf-8')
                  return nextStore
                }
                """);

        add(files, root + "/backend/src/db/seedData.js", "BACKEND", "CREATE_OR_UPDATE", "DATA_MODEL + TEST_SPEC", "初始化演示数据。seed data 只用于首次运行和验收，不作为前端 Mock。", "javascript", 17,
                """
                export const seedData = {
                  records: [
                    {
                      id: 'demo-001',
                      title: '初始化演示记录',
                      status: 'ACTIVE',
                      createdAt: new Date().toISOString(),
                    },
                  ],
                }
                """);

        add(files, root + "/database/schema.json", "DATABASE", "CREATE_OR_UPDATE", "DATA_MODEL", "语言无关数据模型的本地结构化描述，后续可转换为 SQLite/PostgreSQL 迁移。", "json", 18,
                """
                {
                  "schemaVersion": "0.1.0",
                  "entities": [
                    {
                      "name": "GeneratedRecord",
                      "description": "由需求资产生成的演示实体。后续 Dev Agent 会按 DATA_MODEL 细化字段。",
                      "fields": [
                        { "name": "id", "type": "string", "required": true },
                        { "name": "title", "type": "string", "required": true },
                        { "name": "status", "type": "enum", "values": ["ACTIVE", "DISABLED"], "required": true }
                      ]
                    }
                  ]
                }
                """);

        add(files, root + "/database/seed.json", "DATABASE", "CREATE_OR_UPDATE", "DATA_MODEL + TEST_SPEC", "本地首次运行 seed data。", "json", 19,
                """
                {
                  "records": [
                    {
                      "id": "demo-001",
                      "title": "初始化演示记录",
                      "status": "ACTIVE"
                    }
                  ]
                }
                """);

        add(files, root + "/tests/smoke.spec.js", "TEST", "CREATE_OR_UPDATE", "TEST_SPEC", "生成项目冒烟测试，验证前后端真实链路。", "javascript", 20,
                """
                async function main() {
                  const health = await fetch('http://127.0.0.1:3101/api/health')
                  assert(health.ok, 'health endpoint should be ok')
                  console.log('Smoke test passed.')
                }

                function assert(condition, message) {
                  if (!condition) throw new Error(message)
                }

                main().catch((error) => {
                  console.error(error)
                  process.exit(1)
                })
                """);
    }

    private void addNodeContributionFiles(
            List<DevelopmentCodeFileResponse> files,
            String root,
            String nodeType,
            String moduleSegment,
            String classBaseName,
            String kebabBaseName,
            String pageTitle,
            String domainSummary
    ) {
        String moduleRoot = root + "/frontend/src/modules/" + moduleSegment;
        String backendRoot = root + "/backend/src/modules/" + moduleSegment;

        if ("MODULE".equals(nodeType) || "SUBMODULE".equals(nodeType)) {
            add(files, moduleRoot + "/index.js", "FRONTEND", "CREATE_OR_UPDATE", "MODULE_PRD + BUSINESS_FLOW", "模块前端索引。只建立必要骨架，不把页面和功能逻辑提前塞入模块层。", "javascript", 30,
                    """
                    export const moduleMeta = {
                      key: '%s',
                      title: '%s',
                      description: '%s',
                    }
                    """.formatted(moduleSegment, escapeJs(pageTitle), escapeJs(domainSummary)));

            add(files, moduleRoot + "/routes.js", "FRONTEND", "CREATE_OR_UPDATE", "MODULE_PRD", "模块路由聚合入口；具体页面由 PAGE 节点贡献。", "javascript", 31,
                    """
                    export const moduleRoutes = []
                    """);

            add(files, backendRoot + "/" + moduleSegment + ".routes.js", "BACKEND", "CREATE_OR_UPDATE", "API_CONTRACT", "模块后端路由聚合入口；具体接口由 PAGE/FEATURE 节点贡献。", "javascript", 32,
                    """
                    import { Router } from 'express'

                    export const router = Router()
                    """);

            return;
        }

        if ("PAGE".equals(nodeType)) {
            add(files, moduleRoot + "/pages/" + classBaseName + "Page.vue", "FRONTEND", "CREATE_OR_UPDATE", "UI_CONTRACT + PAGE_SPEC", "当前页面容器、字段展示、状态呈现和页面级交互编排。具体功能动作可由 FEATURE 节点继续细化。", "vue", 40,
                    """
                    <template>
                      <section class="page-card">
                        <p class="eyebrow">Generated Page</p>
                        <h1>%s</h1>
                        <p>%s</p>

                        <form class="form-grid" @submit.prevent="submitPrimaryAction">
                          <label>
                            标题
                            <input v-model="form.title" placeholder="请输入标题" />
                          </label>

                          <label>
                            说明
                            <textarea v-model="form.description" placeholder="请输入说明"></textarea>
                          </label>

                          <button class="primary-action" type="submit" :disabled="loading">
                            {{ loading ? '处理中...' : '提交' }}
                          </button>
                        </form>

                        <p v-if="message" class="status-message">{{ message }}</p>
                      </section>
                    </template>

                    <script setup>
                    import { reactive, ref } from 'vue'
                    import { create%sRecord } from '../api/%sApi.js'

                    const loading = ref(false)
                    const message = ref('')
                    const form = reactive({ title: '', description: '' })

                    async function submitPrimaryAction() {
                      loading.value = true
                      message.value = ''

                      try {
                        await create%sRecord({ ...form })
                        message.value = '提交成功，已通过 Node.js 后端写入本地数据层。'
                      } catch (error) {
                        message.value = error?.message || '提交失败'
                      } finally {
                        loading.value = false
                      }
                    }
                    </script>
                    """.formatted(escapeHtml(pageTitle), escapeHtml(domainSummary), classBaseName, moduleSegment, classBaseName));

            add(files, moduleRoot + "/api/" + moduleSegment + "Api.js", "FRONTEND", "CREATE_OR_UPDATE", "API_CONTRACT", "页面 API client。前端不写死假数据，统一请求 Node.js 后端。", "javascript", 41,
                    """
                    export async function fetch%sRecords() {
                      return request('/api/%s')
                    }

                    export async function create%sRecord(payload) {
                      return request('/api/%s', {
                        method: 'POST',
                        body: JSON.stringify(payload),
                      })
                    }

                    async function request(url, options = {}) {
                      const response = await fetch(url, {
                        headers: { 'Content-Type': 'application/json' },
                        ...options,
                      })

                      const body = await response.json().catch(() => ({}))

                      if (!response.ok || body.success === false) {
                        throw new Error(body?.error?.message || 'Request failed')
                      }

                      return body.data
                    }
                    """.formatted(classBaseName, moduleSegment, classBaseName, moduleSegment));

            addPageBackend(files, backendRoot, moduleSegment, classBaseName, pageTitle);
            addPageTest(files, root, moduleSegment, kebabBaseName, pageTitle);
            return;
        }

        if ("FEATURE".equals(nodeType)) {
            add(files, moduleRoot + "/features/" + moduleSegment + "/use" + classBaseName + ".js", "FRONTEND", "CREATE_OR_UPDATE", "PAGE_SPEC + API_CONTRACT + TEST_SPEC", "功能点前端交互 hook，供所属 PAGE 容器接入。", "javascript", 50,
                    """
                    import { run%sAction } from '../../api/%sApi.js'
                    import { validate%sInput } from './%sValidation.js'

                    export function use%s() {
                      async function execute(payload) {
                        const validation = validate%sInput(payload)

                        if (!validation.valid) {
                          throw new Error(validation.message)
                        }

                        return run%sAction(payload)
                      }

                      return { execute }
                    }
                    """.formatted(classBaseName, moduleSegment, classBaseName, moduleSegment, classBaseName, classBaseName, classBaseName));

            add(files, moduleRoot + "/features/" + moduleSegment + "/" + moduleSegment + "Validation.js", "FRONTEND", "CREATE_OR_UPDATE", "TEST_SPEC + PAGE_SPEC", "功能点输入校验，避免校验逻辑散落在页面组件中。", "javascript", 51,
                    """
                    export function validate%sInput(payload = {}) {
                      if (!payload || Object.keys(payload).length === 0) {
                        return { valid: false, message: '请输入必要信息。' }
                      }

                      return { valid: true, message: '' }
                    }
                    """.formatted(classBaseName));

            add(files, moduleRoot + "/api/" + moduleSegment + "Api.js", "FRONTEND", "CREATE_OR_UPDATE", "API_CONTRACT", "功能点 API client，真实请求 Node.js 后端动作接口。", "javascript", 52,
                    """
                    export async function run%sAction(payload) {
                      const response = await fetch('/api/%s/actions/%s', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(payload || {}),
                      })

                      const body = await response.json().catch(() => ({}))

                      if (!response.ok || body.success === false) {
                        throw new Error(body?.error?.message || 'Action failed')
                      }

                      return body.data
                    }
                    """.formatted(classBaseName, moduleSegment, moduleSegment));

            addPageBackend(files, backendRoot, moduleSegment, classBaseName, pageTitle);
            add(files, root + "/tests/modules/" + moduleSegment + "/" + moduleSegment + ".spec.js", "TEST", "CREATE_OR_UPDATE", "TEST_SPEC", "功能点验收测试骨架。", "javascript", 53,
                    """
                    async function main() {
                      const response = await fetch('http://127.0.0.1:3101/api/%s/actions/%s', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ title: '%s 验收动作' }),
                      })

                      if (!response.ok) {
                        throw new Error('feature action should be ok')
                      }

                      console.log('Feature smoke test passed.')
                    }

                    main().catch((error) => {
                      console.error(error)
                      process.exit(1)
                    })
                    """.formatted(moduleSegment, moduleSegment, escapeJs(pageTitle)));
        }
    }

    private void addPageBackend(
            List<DevelopmentCodeFileResponse> files,
            String backendRoot,
            String moduleSegment,
            String classBaseName,
            String pageTitle
    ) {
        add(files, backendRoot + "/" + moduleSegment + ".routes.js", "BACKEND", "CREATE_OR_UPDATE", "API_CONTRACT", "模块后端路由，挂载页面查询和功能动作接口。", "javascript", 60,
                """
                import { Router } from 'express'
                import { create%sRecord, list%sRecords, run%sAction } from './%s.controller.js'

                export const router = Router()

                router.get('/%s', list%sRecords)
                router.post('/%s', create%sRecord)
                router.post('/%s/actions/%s', run%sAction)
                """.formatted(classBaseName, classBaseName, classBaseName, moduleSegment, moduleSegment, classBaseName, moduleSegment, classBaseName, moduleSegment, moduleSegment, classBaseName));

        add(files, backendRoot + "/" + moduleSegment + ".controller.js", "BACKEND", "CREATE_OR_UPDATE", "API_CONTRACT + PAGE_SPEC", "Controller 只负责请求解析和响应装配，业务规则交给 Service。", "javascript", 61,
                """
                import { sendOk } from '../../shared/response.js'
                import { create%s, list%s, run%s } from './%s.service.js'

                export async function list%sRecords(_req, res, next) {
                  try {
                    sendOk(res, await list%s())
                  } catch (error) {
                    next(error)
                  }
                }

                export async function create%sRecord(req, res, next) {
                  try {
                    sendOk(res, await create%s(req.body || {}))
                  } catch (error) {
                    next(error)
                  }
                }

                export async function run%sAction(req, res, next) {
                  try {
                    sendOk(res, await run%s(req.body || {}))
                  } catch (error) {
                    next(error)
                  }
                }
                """.formatted(classBaseName, classBaseName, classBaseName, moduleSegment, classBaseName, classBaseName, classBaseName, classBaseName, classBaseName, classBaseName));

        add(files, backendRoot + "/" + moduleSegment + ".service.js", "BACKEND", "CREATE_OR_UPDATE", "MODULE_PRD + BUSINESS_FLOW + TEST_SPEC", "业务服务层，承载状态变更、异常处理和验收规则。", "javascript", 62,
                """
                import { insert%s, list%s } from './%s.repository.js'

                export async function list%s() {
                  return list%s()
                }

                export async function create%s(payload) {
                  if (!payload?.title) {
                    const error = new Error('标题不能为空。')
                    error.status = 400
                    throw error
                  }

                  return insert%s({
                    title: payload.title,
                    description: payload.description || '',
                    status: 'ACTIVE',
                  })
                }

                export async function run%s(payload) {
                  return create%s({
                    title: payload.title || '%s',
                    description: payload.description || '功能动作已通过真实后端服务处理。',
                  })
                }
                """.formatted(classBaseName, classBaseName, moduleSegment, classBaseName, classBaseName, classBaseName, classBaseName, classBaseName, classBaseName, escapeJs(pageTitle)));

        add(files, backendRoot + "/" + moduleSegment + ".repository.js", "BACKEND", "CREATE_OR_UPDATE", "DATA_MODEL", "数据访问层，统一读写本地数据存储，后续可替换数据库适配器。", "javascript", 63,
                """
                import { readStore, writeStore } from '../../db/localStore.js'

                export async function list%s() {
                  const store = await readStore()
                  return store.records || []
                }

                export async function insert%s(payload) {
                  const store = await readStore()
                  const records = store.records || []
                  const nextRecord = {
                    id: `%s-${Date.now()}`,
                    ...payload,
                    createdAt: new Date().toISOString(),
                  }

                  records.push(nextRecord)
                  await writeStore({ ...store, records })
                  return nextRecord
                }
                """.formatted(classBaseName, classBaseName, moduleSegment));
    }

    private void addPageTest(
            List<DevelopmentCodeFileResponse> files,
            String root,
            String moduleSegment,
            String kebabBaseName,
            String pageTitle
    ) {
        add(files, root + "/tests/modules/" + moduleSegment + "/" + kebabBaseName + ".spec.js", "TEST", "CREATE_OR_UPDATE", "TEST_SPEC", "页面级验收测试骨架。", "javascript", 70,
                """
                async function main() {
                  const list = await fetch('http://127.0.0.1:3101/api/%s')
                  assert(list.ok, 'list endpoint should be ok')

                  const created = await fetch('http://127.0.0.1:3101/api/%s', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ title: '%s 自动化验收记录' }),
                  })
                  assert(created.ok, 'create endpoint should be ok')

                  console.log('Page smoke test passed.')
                }

                function assert(condition, message) {
                  if (!condition) throw new Error(message)
                }

                main().catch((error) => {
                  console.error(error)
                  process.exit(1)
                })
                """.formatted(moduleSegment, moduleSegment, escapeJs(pageTitle)));
    }

    private void addManifestFile(
            List<DevelopmentCodeFileResponse> files,
            String root,
            DevelopmentPlanResponse plan,
            UUID moduleId,
            String nodeType,
            String nodeName,
            String previewRoute
    ) {
        List<String> ownedFiles = new ArrayList<>();
        List<String> contributedFiles = new ArrayList<>();
        List<String> sharedFiles = new ArrayList<>();

        for (DevelopmentCodeFileResponse file : files) {
            if (file.filePath().endsWith("/codemind.generated.json")) {
                continue;
            }

            String relativePath = file.filePath();

            if (isSharedCodeFile(file)) {
                sharedFiles.add(relativePath);
            } else if ("PAGE".equals(nodeType) && file.filePath().contains("/pages/")) {
                ownedFiles.add(relativePath);
            } else if (("MODULE".equals(nodeType) || "SUBMODULE".equals(nodeType)) && file.filePath().contains("/modules/")) {
                ownedFiles.add(relativePath);
            } else {
                contributedFiles.add(relativePath);
            }
        }

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeId", String.valueOf(moduleId));
        node.put("nodeType", nodeType);
        node.put("nodeName", nodeName);
        node.put("nodeKey", plan.moduleKey());
        node.put("moduleType", plan.moduleType());
        node.put("previewRoute", previewRoute);
        node.put("sourceArtifactTypes", plan.sourceArtifactTypes());
        node.put("ownedFiles", ownedFiles);
        node.put("contributedFiles", contributedFiles);
        node.put("sharedFiles", sharedFiles);
        node.put("updatedAt", LocalDateTime.now().toString());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", "2.0");
        manifest.put("projectId", String.valueOf(plan.projectId()));
        manifest.put("projectCode", extractProjectCodeFromRoot(root));
        manifest.put("targetStack", plan.targetStack());
        manifest.put("root", root);
        manifest.put("runtimeUnit", "PROJECT_CODEBASE");
        manifest.put("generationUnit", "MODULE_TREE_NODE_CONTRIBUTION");
        manifest.put("previewUnit", "PAGE_OR_FEATURE_SCENARIO");
        manifest.put("codeTraceGraphEnabled", true);
        manifest.put("updatedAt", LocalDateTime.now().toString());
        manifest.put("nodes", List.of(node));

        add(files, root + "/codemind.generated.json", "CONFIG", "MERGE", "DEV_HANDOFF_PACKAGE", "模块树节点与代码文件的映射清单。平台通过它实现按模块树查看代码。", "json", 0, toJson(manifest));
    }


    private boolean isSharedCodeFile(DevelopmentCodeFileResponse file) {
        String path = file.filePath() == null ? "" : file.filePath().replace("\\", "/");
        String type = file.fileType() == null ? "" : file.fileType();

        return path.contains("/shared/") ||
                path.contains("/db/") ||
                path.contains("/database/") ||
                path.endsWith("/package.json") ||
                path.endsWith("/README.md") ||
                path.endsWith("/vite.config.js") ||
                "CONFIG".equals(type) ||
                "DATABASE".equals(type) ||
                "DOC".equals(type);
    }

    private DevelopmentCodeMaterializedFileResponse materializeOneFile(
            Path workspaceRoot,
            DevelopmentCodeFileResponse file,
            boolean allowOverwrite,
            DevelopmentCodebaseSafetyFileResponse safetyFile
    ) {
        Path targetPath = resolveSafeGeneratedPath(workspaceRoot, file.filePath());
        boolean exists = Files.exists(targetPath);

        if (safetyFile != null && safetyFile.conflict() && !allowOverwrite) {
            return new DevelopmentCodeMaterializedFileResponse(
                    file.filePath(),
                    targetPath.toString(),
                    file.fileType(),
                    file.action(),
                    "SKIPPED_CONFLICT",
                    safetyFile.message(),
                    sizeOf(targetPath)
            );
        }

        if (safetyFile != null && "UNCHANGED".equals(safetyFile.changeType())) {
            return new DevelopmentCodeMaterializedFileResponse(
                    file.filePath(),
                    targetPath.toString(),
                    file.fileType(),
                    file.action(),
                    "UNCHANGED",
                    "文件内容未变化，无需写入。",
                    sizeOf(targetPath)
            );
        }

        if (exists && !allowOverwrite && "CREATE_OR_KEEP".equals(file.action())) {
            return new DevelopmentCodeMaterializedFileResponse(
                    file.filePath(),
                    targetPath.toString(),
                    file.fileType(),
                    file.action(),
                    "SKIPPED_EXISTS",
                    "项目主干文件已存在，保持用户已有内容。",
                    sizeOf(targetPath)
            );
        }

        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }

            String content = file.content() == null ? "" : file.content();
            Files.writeString(targetPath, content.strip() + System.lineSeparator(), StandardCharsets.UTF_8);

            return new DevelopmentCodeMaterializedFileResponse(
                    file.filePath(),
                    targetPath.toString(),
                    file.fileType(),
                    file.action(),
                    exists ? "OVERWRITTEN" : "WRITTEN",
                    exists ? "已根据安全检查结果写入。" : "已写入。",
                    sizeOf(targetPath)
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "写入生成代码文件失败：" + file.filePath(),
                    e
            );
        }
    }

    private DevelopmentCodeMaterializedFileResponse materializeManifestFile(
            Path workspaceRoot,
            DevelopmentCodeFileResponse file,
            boolean allowOverwrite
    ) {
        Path targetPath = resolveSafeGeneratedPath(workspaceRoot, file.filePath());
        boolean exists = Files.exists(targetPath);

        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }

            Map<String, Object> incoming = objectMapper.readValue(
                    file.content(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            Map<String, Object> merged = incoming;

            if (exists) {
                Map<String, Object> current = objectMapper.readValue(
                        targetPath.toFile(),
                        new TypeReference<Map<String, Object>>() {
                        }
                );
                merged = mergeManifest(current, incoming, allowOverwrite);
            }

            Files.writeString(
                    targetPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged) + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            return new DevelopmentCodeMaterializedFileResponse(
                    file.filePath(),
                    targetPath.toString(),
                    file.fileType(),
                    file.action(),
                    exists ? "MERGED" : "WRITTEN",
                    exists ? "已合并当前节点代码映射。" : "已写入代码映射清单。",
                    sizeOf(targetPath)
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "写入代码映射清单失败。",
                    e
            );
        }
    }

    private Map<String, Object> mergeManifest(
            Map<String, Object> current,
            Map<String, Object> incoming,
            boolean allowOverwrite
    ) {
        Map<String, Object> result = new LinkedHashMap<>(current == null ? Map.of() : current);
        result.put("schemaVersion", incoming.getOrDefault("schemaVersion", "1.0"));
        result.put("targetStack", incoming.getOrDefault("targetStack", result.get("targetStack")));
        result.put("root", incoming.getOrDefault("root", result.get("root")));
        result.put("runtimeUnit", "PROJECT_CODEBASE");
        result.put("generationUnit", "MODULE_TREE_NODE_CONTRIBUTION");
        result.put("previewUnit", "PAGE_OR_FEATURE_SCENARIO");
        result.put("updatedAt", LocalDateTime.now().toString());

        List<Map<String, Object>> nodes = new ArrayList<>();
        Object currentNodes = result.get("nodes");

        if (currentNodes instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    nodes.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }

        Object incomingNodes = incoming.get("nodes");

        if (incomingNodes instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }

                Map<String, Object> nextNode = new LinkedHashMap<>((Map<String, Object>) map);
                String nodeId = String.valueOf(nextNode.get("nodeId"));
                boolean replaced = false;

                for (int i = 0; i < nodes.size(); i++) {
                    if (nodeId.equals(String.valueOf(nodes.get(i).get("nodeId")))) {
                        nodes.set(i, allowOverwrite ? nextNode : mergeNode(nodes.get(i), nextNode));
                        replaced = true;
                        break;
                    }
                }

                if (!replaced) {
                    nodes.add(nextNode);
                }
            }
        }

        result.put("nodes", nodes);
        return result;
    }

    private Map<String, Object> mergeNode(
            Map<String, Object> current,
            Map<String, Object> incoming
    ) {
        Map<String, Object> merged = new LinkedHashMap<>(current);
        merged.putAll(incoming);
        merged.put("ownedFiles", mergeList(current.get("ownedFiles"), incoming.get("ownedFiles")));
        merged.put("contributedFiles", mergeList(current.get("contributedFiles"), incoming.get("contributedFiles")));
        merged.put("sharedFiles", mergeList(current.get("sharedFiles"), incoming.get("sharedFiles")));
        merged.put("updatedAt", LocalDateTime.now().toString());
        return merged;
    }

    private List<String> mergeList(Object left, Object right) {
        List<String> values = new ArrayList<>();
        appendList(values, left);
        appendList(values, right);
        return values.stream().distinct().toList();
    }

    private void appendList(List<String> values, Object source) {
        if (!(source instanceof List<?> list)) {
            return;
        }

        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
    }

    private void add(
            List<DevelopmentCodeFileResponse> files,
            String filePath,
            String fileType,
            String action,
            String source,
            String description,
            String language,
            int priority,
            String content
    ) {
        files.add(new DevelopmentCodeFileResponse(
                filePath,
                fileType,
                action,
                source,
                description,
                language,
                content == null ? "" : content.strip(),
                priority
        ));
    }

    private String buildCodebaseRoot(String projectCode) {
        return generatedPathService.buildPlatformCodebaseRoot(projectCode, "NODEJS_FULLSTACK");
    }

    private String buildPreviewRoute(String nodeType, String segment) {
        if ("FEATURE".equals(nodeType)) {
            return "/" + segment + "?scenario=" + segment;
        }

        if ("MODULE".equals(nodeType) || "SUBMODULE".equals(nodeType) || "PAGE".equals(nodeType)) {
            return "/" + segment;
        }

        return "/";
    }

    private List<String> runtimeCommands() {
        return List.of(
                "cd generated/nodejs/{projectCode}/app",
                "npm install",
                "npm run dev",
                "npm test"
        );
    }


    private List<String> buildCodegenPreflightWarnings(
            DevelopmentHandoffContextResponse context
    ) {
        List<String> warnings = new ArrayList<>();

        if (context == null) {
            warnings.add("A9.10.11：未获得 Development Build Context，已阻止生成。");
            return warnings;
        }

        DevelopmentHandoffReadinessResponse readiness = context.readiness();

        if (readiness == null) {
            warnings.add("A9.10.11：未获得旧开发交接诊断结果，继续按 Development Build Context 生成。");
            return warnings;
        }

        if (readiness.ready()) {
            return warnings;
        }

        warnings.add("A9.10.11：旧开发交接门禁已降级为高级诊断；本次按 Development Build Context / confirmed UI_CONTRACT / 模块树上下文继续生成 Node.js 系统代码。");

        if (context.developmentBuildContext() == null || context.developmentBuildContext().isEmpty()) {
            warnings.add("A9.10.11：Development Build Context 为空或不可用，本次将使用已确认 UI_CONTRACT、模块树信息和受控模板继续生成。");
        }

        appendReadinessIssues(warnings, "旧门禁阻断项", readiness.blockers(), 8);
        appendReadinessIssues(warnings, "旧门禁警告项", readiness.warnings(), 6);

        return warnings;
    }

    private void appendReadinessIssues(
            List<String> warnings,
            String label,
            List<?> issues,
            int limit
    ) {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        int appended = 0;

        for (Object issue : issues) {
            if (issue == null) {
                continue;
            }

            if (appended >= limit) {
                warnings.add(label + "：还有 " + (issues.size() - appended) + " 项已收起，可在高级诊断中查看。");
                return;
            }

            warnings.add(label + "：" + String.valueOf(issue));
            appended++;
        }
    }

    private String buildPageTitle(
            DevelopmentHandoffContextResponse context,
            String fallback
    ) {
        if (context != null && context.confirmedUiContract() != null) {
            Object page = context.confirmedUiContract().get("page");

            if (page instanceof Map<?, ?> pageMap) {
                Object name = pageMap.get("name");

                if (name != null && !String.valueOf(name).isBlank()) {
                    return String.valueOf(name);
                }
            }
        }

        return safeText(fallback, "Generated Page");
    }

    private String buildDomainSummary(DevelopmentHandoffContextResponse context) {
        if (context == null || context.artifacts() == null) {
            return "该节点由需求资产生成，前端请求 Node.js 后端接口，后端访问本地数据层。";
        }

        for (DevelopmentHandoffArtifactPayload artifact : context.artifacts()) {
            if (artifact == null || artifact.contentMarkdown() == null) {
                continue;
            }

            String normalized = artifact.contentMarkdown()
                    .replaceAll("[#>*`\\-]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            if (normalized.length() > 36) {
                return normalized.substring(0, Math.min(normalized.length(), 150));
            }
        }

        return "该节点由需求资产生成，前端请求 Node.js 后端接口，后端访问本地数据层。";
    }

    private String extractProjectCodeFromRoot(String root) {
        String normalized = root == null ? "" : root.replace("\\", "/");
        String prefix = "generated/nodejs/";

        if (normalized.startsWith(prefix)) {
            String rest = normalized.substring(prefix.length());
            int index = rest.indexOf('/');
            return index > 0 ? rest.substring(0, index) : rest;
        }

        return "project";
    }

    private Path resolveMaterializationWorkspaceRoot() {
        String configured = System.getProperty("codemind.development.workspace-root");

        if (configured == null || configured.isBlank()) {
            configured = System.getenv("CODEMIND_WORKSPACE_ROOT");
        }

        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        if (
                current.getFileName() != null &&
                        "codemind-server".equalsIgnoreCase(current.getFileName().toString()) &&
                        current.getParent() != null
        ) {
            return current.getParent().toAbsolutePath().normalize();
        }

        return current;
    }

    private Path resolveSafeGeneratedPath(Path workspaceRoot, String filePath) {
        return generatedPathService.resolvePlatformGeneratedPath(filePath);
    }

    private long sizeOf(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }


    public DevelopmentCodegenTraceResponse buildCodegenTrace(
            DevelopmentAiCodegenProposalService.AiCodegenResult result,
            String status,
            int fileCount
    ) {
        if (result == null) {
            return new DevelopmentCodegenTraceResponse(
                    UUID.randomUUID().toString(),
                    "development",
                    "codegen",
                    null,
                    null,
                    "UNKNOWN",
                    safeText(status, "UNKNOWN"),
                    false,
                    true,
                    fileCount,
                    List.of("AI codegen trace unavailable."),
                    Map.of(),
                    LocalDateTime.now()
            );
        }

        boolean aiGenerated = result.aiGenerated();
        return new DevelopmentCodegenTraceResponse(
                UUID.randomUUID().toString(),
                "development",
                "codegen",
                result.provider(),
                result.model(),
                result.generatorMode(),
                safeText(status, aiGenerated ? "AI_GENERATED" : "GENERATED"),
                aiGenerated,
                !aiGenerated,
                fileCount,
                result.warnings() == null ? List.of() : result.warnings(),
                result.usage() == null ? Map.of() : result.usage(),
                LocalDateTime.now()
        );
    }

    private String calculateChecksum(
            DevelopmentPlanResponse plan,
            List<DevelopmentCodeFileResponse> files,
            List<String> warnings,
            String summary
    ) {
        StringBuilder source = new StringBuilder();
        source.append(plan.planChecksum()).append('|')
                .append(plan.targetStack()).append('|')
                .append(summary).append('|')
                .append(warnings);

        for (DevelopmentCodeFileResponse file : files) {
            source.append('|').append(file.filePath()).append(':').append(file.content());
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(source.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();

            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }

            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate Development Code Project checksum.", e);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize codemind.generated.json", e);
        }
    }

    private String toClassName(String key, String fallback) {
        String source = safeText(key, fallback)
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim();

        if (source.isBlank()) {
            source = "Generated";
        }

        StringBuilder builder = new StringBuilder();

        for (String part : source.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }

            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));

            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.isEmpty() ? "Generated" : builder.toString();
    }

    private String toKebabCase(String key, String fallback) {
        return toPathSegment(key, fallback);
    }

    private String toPathSegment(String value, String fallback) {
        String source = safeText(value, fallback)
                .trim()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(Locale.ROOT);

        return source.isBlank() ? fallback : source;
    }

    private String safeUpper(String value) {
        return safeText(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String escapeJs(String value) {
        return safeText(value, "")
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private String escapeHtml(String value) {
        return safeText(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}