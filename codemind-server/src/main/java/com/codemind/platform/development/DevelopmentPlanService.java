package com.codemind.platform.development;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DevelopmentPlanService {

    private final DevelopmentHandoffGateService developmentHandoffGateService;

    public DevelopmentPlanService(
            DevelopmentHandoffGateService developmentHandoffGateService
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
    }

    public List<DevelopmentTargetStackResponse> listTargetStacks(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );

        return DevelopmentTargetStack.responses();
    }

    public DevelopmentPlanResponse generatePlan(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateDevelopmentPlanRequest request
    ) {
        DevelopmentTargetStack targetStack = resolveTargetStack(request);

        if (!targetStack.available()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "目标技术栈“" + targetStack.displayName() + "”当前尚未开放生成。"
            );
        }

        DevelopmentHandoffContextResponse context =
                developmentHandoffGateService.getContext(
                        workspaceId,
                        projectCode,
                        moduleId
                );

        DevelopmentHandoffReadinessResponse readiness = context.readiness();

        if (!readiness.directDevelopmentAllowed()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前节点不是直接开发单元。请选择 MODULE 或 PAGE 生成开发实施计划。"
            );
        }

        // A9.10.11: Legacy handoff readiness is diagnostic for Development Agent codegen.
        // Missing old fixed assets such as MODULE_PRD / API_CONTRACT / TEST_SPEC must not block
        // Development Build Context based Node.js generation. Keep the readiness result in context
        // so the workspace can still show it under advanced diagnostics.
        String nodeType = safeText(context.nodeType(), "MODULE");
        String moduleName = safeText(context.moduleName(), "CurrentModule");
        String moduleKey = safeText(context.moduleKey(), moduleName);
        String classBaseName = toClassName(moduleKey, moduleName);
        String kebabBaseName = toKebabCase(moduleKey, moduleName);
        String generationRoot = buildGenerationRoot(projectCode);

        List<DevelopmentPlanFileItem> frontendFiles = buildFrontendFiles(
                targetStack,
                nodeType,
                classBaseName,
                kebabBaseName,
                generationRoot
        );

        List<DevelopmentPlanFileItem> backendFiles = buildBackendFiles(
                targetStack,
                nodeType,
                classBaseName,
                kebabBaseName,
                generationRoot
        );

        List<DevelopmentPlanApiItem> apiItems = buildApiItems(
                targetStack,
                nodeType,
                kebabBaseName,
                classBaseName,
                context
        );

        List<DevelopmentPlanDatabaseItem> databaseItems = buildDatabaseItems(
                targetStack,
                nodeType,
                classBaseName,
                context
        );

        List<DevelopmentPlanStepItem> implementationSteps =
                buildImplementationSteps(targetStack, nodeType);

        List<DevelopmentPlanSourceMapping> sourceMappings =
                buildSourceMappings(context);

        List<String> sourceArtifactTypes = collectSourceArtifactTypes(context);

        String summary = buildSummary(
                nodeType,
                moduleName,
                targetStack,
                generationRoot
        );

        List<String> principles = buildPrinciples(targetStack);

        String planChecksum = calculatePlanChecksum(
                context,
                targetStack,
                summary,
                principles,
                sourceArtifactTypes,
                frontendFiles,
                backendFiles,
                apiItems,
                databaseItems,
                implementationSteps,
                sourceMappings
        );

        return new DevelopmentPlanResponse(
                context.projectId(),
                context.moduleId(),
                context.moduleKey(),
                context.moduleName(),
                context.moduleType(),
                nodeType,
                true,
                "PLANNED",
                planChecksum,
                LocalDateTime.now(),
                targetStack.stackCode(),
                targetStack.displayName(),
                "PROPOSAL_ONLY",
                targetStack.runtimeCommands(),
                targetStack.notes(),
                summary,
                principles,
                sourceArtifactTypes,
                frontendFiles,
                backendFiles,
                apiItems,
                databaseItems,
                implementationSteps,
                sourceMappings,
                context
        );
    }

    private DevelopmentTargetStack resolveTargetStack(
            GenerateDevelopmentPlanRequest request
    ) {
        try {
            return DevelopmentTargetStack.resolve(
                    request == null ? null : request.targetStack()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage(),
                    ex
            );
        }
    }

    private List<DevelopmentPlanFileItem> buildFrontendFiles(
            DevelopmentTargetStack targetStack,
            String nodeType,
            String classBaseName,
            String kebabBaseName,
            String generationRoot
    ) {
        if (targetStack == DevelopmentTargetStack.NODEJS_FULLSTACK) {
            return buildNodeJsFrontendFiles(
                    nodeType,
                    classBaseName,
                    kebabBaseName,
                    generationRoot
            );
        }

        return List.of();
    }

    private List<DevelopmentPlanFileItem> buildNodeJsFrontendFiles(
            String nodeType,
            String classBaseName,
            String kebabBaseName,
            String generationRoot
    ) {
        List<DevelopmentPlanFileItem> files = new ArrayList<>();
        String frontendRoot = generationRoot + "/frontend";

        files.add(frontend(
                generationRoot + "/package.json",
                "CREATE",
                "DEV_HANDOFF_PACKAGE",
                "生成 Node.js 全栈项目根 package.json，统一管理前端、后端安装与 dev 启动脚本。",
                1
        ));
        files.add(frontend(
                frontendRoot + "/package.json",
                "CREATE",
                "UI_CONTRACT + PAGE_SPEC",
                "生成 Vite + Vue 前端工程依赖与启动脚本。",
                2
        ));
        files.add(frontend(
                frontendRoot + "/index.html",
                "CREATE",
                "UI_CONTRACT",
                "生成前端页面入口。",
                3
        ));
        files.add(frontend(
                frontendRoot + "/src/main.js",
                "CREATE",
                "UI_CONTRACT",
                "挂载 Vue 应用，并接入基础样式。",
                4
        ));
        files.add(frontend(
                frontendRoot + "/src/App.vue",
                "CREATE",
                "UI_CONTRACT + PAGE_SPEC",
                "生成应用壳层，承载当前节点页面和全局状态。",
                5
        ));

        if ("PAGE".equals(nodeType)) {
            files.add(frontend(
                    frontendRoot + "/src/pages/" + classBaseName + "Page.vue",
                    "CREATE",
                    "UI_CONTRACT + PAGE_SPEC",
                    "按已确认 UI_CONTRACT 生成真实页面组件，保留布局、字段、动作和状态。",
                    6
            ));
            files.add(frontend(
                    frontendRoot + "/src/api/" + kebabBaseName + "Api.js",
                    "CREATE",
                    "API_CONTRACT",
                    "生成前端 API Client，页面业务操作必须请求 Node.js 后端接口。",
                    7
            ));
            files.add(frontend(
                    frontendRoot + "/src/state/use" + classBaseName + "State.js",
                    "CREATE",
                    "PAGE_SPEC + API_CONTRACT",
                    "封装页面加载、提交、错误处理、空状态和成功状态逻辑。",
                    8
            ));
        } else if ("FEATURE".equals(nodeType)) {
            files.add(frontend(
                    frontendRoot + "/src/features/" + kebabBaseName + "/" + classBaseName + "Action.js",
                    "CREATE",
                    "MODULE_PRD + API_CONTRACT + TEST_SPEC",
                    "生成功能点动作处理逻辑，供所属页面接入。",
                    6
            ));
        } else {
            files.add(frontend(
                    frontendRoot + "/src/modules/" + kebabBaseName + "/index.js",
                    "CREATE",
                    "MODULE_PRD + BUSINESS_FLOW + DEV_HANDOFF_PACKAGE",
                    "生成模块级前端路由、菜单、页面清单和开发索引，不直接扩展未确认页面细节。",
                    6
            ));
        }

        files.add(frontend(
                frontendRoot + "/src/styles/app.css",
                "CREATE",
                "UI_CONTRACT + THEME_PROFILE_SNAPSHOT",
                "生成页面基础样式，保持与需求阶段确认的视觉契约一致。",
                20
        ));

        return files;
    }

    private List<DevelopmentPlanFileItem> buildBackendFiles(
            DevelopmentTargetStack targetStack,
            String nodeType,
            String classBaseName,
            String kebabBaseName,
            String generationRoot
    ) {
        if (targetStack == DevelopmentTargetStack.NODEJS_FULLSTACK) {
            return buildNodeJsBackendFiles(
                    nodeType,
                    classBaseName,
                    kebabBaseName,
                    generationRoot
            );
        }

        return List.of();
    }

    private List<DevelopmentPlanFileItem> buildNodeJsBackendFiles(
            String nodeType,
            String classBaseName,
            String kebabBaseName,
            String generationRoot
    ) {
        List<DevelopmentPlanFileItem> files = new ArrayList<>();
        String backendRoot = generationRoot + "/backend";
        String databaseRoot = generationRoot + "/database";

        files.add(backend(
                backendRoot + "/package.json",
                "CREATE",
                "DEV_HANDOFF_PACKAGE",
                "生成 Node.js 后端依赖与 dev 启动脚本。",
                1
        ));
        files.add(backend(
                backendRoot + "/server.js",
                "CREATE",
                "API_CONTRACT + DEV_HANDOFF_PACKAGE",
                "生成 Express 服务入口，接入 JSON、CORS、错误处理中间件和业务路由。",
                2
        ));
        files.add(backend(
                backendRoot + "/src/routes/" + kebabBaseName + "Routes.js",
                "CREATE",
                "API_CONTRACT",
                "根据接口契约生成真实 REST 路由。",
                3
        ));
        files.add(backend(
                backendRoot + "/src/controllers/" + classBaseName + "Controller.js",
                "CREATE",
                "API_CONTRACT + PAGE_SPEC",
                "接收前端请求，执行参数校验与响应结构装配。",
                4
        ));
        files.add(backend(
                backendRoot + "/src/services/" + classBaseName + "Service.js",
                "CREATE",
                "MODULE_PRD + BUSINESS_FLOW + TEST_SPEC",
                "承载正式业务规则、状态变更、异常处理和验收路径。",
                5
        ));
        files.add(backend(
                backendRoot + "/src/repositories/" + classBaseName + "Repository.js",
                "CREATE",
                "DATA_MODEL",
                "封装 SQLite 数据访问，不在 Controller 中直接写 SQL。",
                6
        ));
        files.add(backend(
                backendRoot + "/src/db/index.js",
                "CREATE",
                "DATA_MODEL",
                "初始化本地数据库连接，加载 schema 与 seed 数据。",
                7
        ));
        files.add(backend(
                databaseRoot + "/schema.sql",
                "CREATE",
                "DATA_MODEL",
                "根据语言无关数据模型生成 SQLite 表结构、约束和索引草案。",
                8
        ));
        files.add(backend(
                databaseRoot + "/seed.sql",
                "CREATE",
                "DATA_MODEL + TEST_SPEC",
                "写入首次运行演示数据。seed data 只用于初始化，不替代真实接口和数据访问层。",
                9
        ));
        files.add(backend(
                generationRoot + "/tests/" + kebabBaseName + ".spec.js",
                "CREATE",
                "TEST_SPEC",
                "生成 Node.js 接口与页面关键路径验收测试骨架。",
                30
        ));
        files.add(backend(
                generationRoot + "/README.md",
                "CREATE",
                "DEV_HANDOFF_PACKAGE",
                "生成运行说明、目录说明、验收步骤、未确认项和后续接入建议。",
                40
        ));

        return files;
    }

    private List<DevelopmentPlanApiItem> buildApiItems(
            DevelopmentTargetStack targetStack,
            String nodeType,
            String kebabBaseName,
            String classBaseName,
            DevelopmentHandoffContextResponse context
    ) {
        if (targetStack == DevelopmentTargetStack.NODEJS_FULLSTACK) {
            return buildNodeJsApiItems(
                    nodeType,
                    kebabBaseName,
                    classBaseName
            );
        }

        return List.of();
    }

    private List<DevelopmentPlanApiItem> buildNodeJsApiItems(
            String nodeType,
            String kebabBaseName,
            String classBaseName
    ) {
        List<DevelopmentPlanApiItem> apis = new ArrayList<>();

        if ("PAGE".equals(nodeType)) {
            apis.add(new DevelopmentPlanApiItem(
                    "GET",
                    "/api/" + kebabBaseName,
                    "页面初始化查询接口，用于加载页面真实展示数据。",
                    List.of("API_CONTRACT", "DATA_MODEL", "UI_CONTRACT"),
                    "绑定 UI_CONTRACT 中的加载态、空状态、错误态和成功态。"
            ));
            apis.add(new DevelopmentPlanApiItem(
                    "POST",
                    "/api/" + kebabBaseName + "/actions/:actionId",
                    "页面动作提交接口，用于承载按钮、表单提交和状态变更。",
                    List.of("API_CONTRACT", "BUSINESS_FLOW", "UI_CONTRACT"),
                    "actionId 必须来源于已确认 UI_CONTRACT，不允许开发阶段重命名。"
            ));
        } else if ("FEATURE".equals(nodeType)) {
            apis.add(new DevelopmentPlanApiItem(
                    "POST",
                    "/api/actions/" + kebabBaseName,
                    classBaseName + " 功能点动作接口。",
                    List.of("MODULE_PRD", "API_CONTRACT", "TEST_SPEC"),
                    "只实现当前功能点动作，不扩展页面级交互。"
            ));
        } else {
            apis.add(new DevelopmentPlanApiItem(
                    "REVIEW",
                    "/api/" + kebabBaseName + "/**",
                    "模块级接口规划审查项。",
                    List.of("DEV_HANDOFF_PACKAGE", "API_CONTRACT", "PAGE_SPEC"),
                    "模块级节点先生成接口规划与目录骨架，具体页面接口由 PAGE 节点继续细化。"
            ));
        }

        return apis;
    }

    private List<DevelopmentPlanDatabaseItem> buildDatabaseItems(
            DevelopmentTargetStack targetStack,
            String nodeType,
            String classBaseName,
            DevelopmentHandoffContextResponse context
    ) {
        if (targetStack == DevelopmentTargetStack.NODEJS_FULLSTACK) {
            return buildNodeJsDatabaseItems(nodeType, classBaseName);
        }

        return List.of();
    }

    private List<DevelopmentPlanDatabaseItem> buildNodeJsDatabaseItems(
            String nodeType,
            String classBaseName
    ) {
        if ("FEATURE".equals(nodeType)) {
            return List.of(
                    new DevelopmentPlanDatabaseItem(
                            classBaseName + "AuditTrail",
                            "REVIEW",
                            List.of("DATA_MODEL", "TEST_SPEC"),
                            "功能点通常不直接建新表；仅在测试规格或数据模型明确要求时补充审计记录。"
                    )
            );
        }

        return List.of(
                new DevelopmentPlanDatabaseItem(
                        classBaseName,
                        "CREATE_OR_UPDATE",
                        List.of("DATA_MODEL", "DEV_HANDOFF_PACKAGE"),
                        "根据数据模型决定 SQLite 表、字段、唯一性、枚举、状态字段和业务约束。"
                ),
                new DevelopmentPlanDatabaseItem(
                        classBaseName + "Schema",
                        "CREATE",
                        List.of("DATA_MODEL"),
                        "生成 database/schema.sql，真实运行前仍需人工确认字段命名和迁移风险。"
                ),
                new DevelopmentPlanDatabaseItem(
                        classBaseName + "SeedData",
                        "CREATE",
                        List.of("TEST_SPEC"),
                        "生成 database/seed.sql 作为本地首次运行演示数据，不作为业务 Mock 主链路。"
                )
        );
    }

    private List<DevelopmentPlanStepItem> buildImplementationSteps(
            DevelopmentTargetStack targetStack,
            String nodeType
    ) {
        return List.of(
                new DevelopmentPlanStepItem(
                        1,
                        "锁定语言无关开发输入",
                        "读取当前节点正式资产、DEV_HANDOFF_PACKAGE v2、开发交接门禁结果、确认后的 UI_CONTRACT 快照和主题快照。",
                        true
                ),
                new DevelopmentPlanStepItem(
                        2,
                        "选择目标技术栈适配器",
                        "本计划使用 " + targetStack.stackCode() + " 生成器，后续 Java / Python 等技术栈通过同一 Dev Blueprint 输入扩展。",
                        true
                ),
                new DevelopmentPlanStepItem(
                        3,
                        "生成 Node.js 全栈文件清单",
                        "按 frontend、backend、database、tests、README 五类生成待创建文件清单。",
                        true
                ),
                new DevelopmentPlanStepItem(
                        4,
                        "生成最小可运行系统骨架",
                        "先生成 Vite + Vue 页面、Express API、SQLite 数据层、schema/seed 和运行脚本，保证 npm run dev 能打开系统。",
                        false
                ),
                new DevelopmentPlanStepItem(
                        5,
                        "接通真实前后端链路",
                        "页面动作必须调用 Node.js 后端接口，后端必须访问本地数据库；seed data 仅用于首次运行演示。",
                        "PAGE".equals(nodeType)
                ),
                new DevelopmentPlanStepItem(
                        6,
                        "执行运行验证与质量回读",
                        "执行 npm install、npm run dev 和关键验收测试，把错误回写为下一轮开发修复任务。",
                        false
                )
        );
    }

    private List<DevelopmentPlanSourceMapping> buildSourceMappings(
            DevelopmentHandoffContextResponse context
    ) {
        List<DevelopmentPlanSourceMapping> mappings = new ArrayList<>();

        for (DevelopmentHandoffArtifactPayload artifact : context.artifacts()) {
            mappings.add(new DevelopmentPlanSourceMapping(
                    artifact.artifactType(),
                    artifact.title(),
                    usedFor(artifact.artifactType()),
                    artifact.required() ? "HIGH" : "MEDIUM"
            ));
        }

        if (context.confirmedUiContract() != null) {
            mappings.add(new DevelopmentPlanSourceMapping(
                    "UI_CONTRACT_SNAPSHOT",
                    "已确认交互原型快照",
                    "用于生成真实页面布局、字段、组件、动作、页面状态和视觉一致性约束。",
                    "HIGH"
            ));
        }

        if (context.themeProfileSnapshot() != null) {
            mappings.add(new DevelopmentPlanSourceMapping(
                    "THEME_PROFILE_SNAPSHOT",
                    "项目风格快照",
                    "用于保持生成页面与需求阶段确认的视觉风格一致。",
                    "MEDIUM"
            ));
        }

        return mappings;
    }

    private List<String> collectSourceArtifactTypes(
            DevelopmentHandoffContextResponse context
    ) {
        Set<String> artifactTypes = new LinkedHashSet<>();

        for (DevelopmentHandoffArtifactPayload artifact : context.artifacts()) {
            artifactTypes.add(artifact.artifactType());
        }

        if (context.confirmedUiContract() != null) {
            artifactTypes.add("UI_CONTRACT_SNAPSHOT");
        }

        if (context.themeProfileSnapshot() != null) {
            artifactTypes.add("THEME_PROFILE_SNAPSHOT");
        }

        return List.copyOf(artifactTypes);
    }

    private String usedFor(
            String artifactType
    ) {
        return switch (artifactType) {
            case "MODULE_PRD" -> "业务边界、角色、验收标准和核心能力来源。";
            case "BUSINESS_FLOW" -> "主流程、异常流程、状态流转和动作顺序来源。";
            case "PAGE_SPEC" -> "页面结构、字段、组件和操作说明来源。";
            case "DATA_MODEL" -> "实体、字段、关系、状态、约束和本地数据库映射来源。";
            case "API_CONTRACT" -> "接口路径、请求响应、错误码、权限和前后端绑定来源。";
            case "TEST_SPEC" -> "验收用例、边界条件、权限场景和自动化测试来源。";
            case "UI_CONTRACT" -> "页面布局、组件树、动作 ID、状态和交互契约来源。";
            case "DEV_HANDOFF_PACKAGE" -> "语言无关 Dev Blueprint、开发顺序、来源引用矩阵和风险边界来源。";
            default -> "开发上下文补充来源。";
        };
    }

    private DevelopmentPlanFileItem frontend(
            String filePath,
            String action,
            String source,
            String description,
            int priority
    ) {
        return new DevelopmentPlanFileItem(
                filePath,
                "FRONTEND",
                action,
                source,
                description,
                priority
        );
    }

    private DevelopmentPlanFileItem backend(
            String filePath,
            String action,
            String source,
            String description,
            int priority
    ) {
        return new DevelopmentPlanFileItem(
                filePath,
                "BACKEND",
                action,
                source,
                description,
                priority
        );
    }

    private List<String> buildPrinciples(
            DevelopmentTargetStack targetStack
    ) {
        if (targetStack == DevelopmentTargetStack.NODEJS_FULLSTACK) {
            return List.of(
                    "以已确认需求资产和 DEV_HANDOFF_PACKAGE v2 为业务真相，不重新解释业务规则。",
                    "PAGE 节点以用户确认的 UI_CONTRACT 快照为页面蓝图，不在开发阶段重新设计页面。",
                    "Node.js 分支必须生成真实前端、真实 Express API、真实本地数据访问层和初始化种子数据。",
                    "不以 Mock 作为主业务实现；seed data 仅用于首次运行、演示和验收测试。",
                    "保留 UI_CONTRACT 中的 componentId、actionId、字段顺序和页面状态语义。",
                    "先生成可审查的实施计划，再进入真实代码生成提案，AI 不直接覆盖现有项目。"
            );
        }

        return List.of(
                "以已确认需求资产为业务真相，不重新解释业务规则。",
                "先生成可审查的实施计划，再进入真实代码生成。"
        );
    }

    private String buildSummary(
            String nodeType,
            String moduleName,
            DevelopmentTargetStack targetStack,
            String generationRoot
    ) {
        return "针对“" + moduleName + "”生成 " + nodeType
                + " 级开发实施计划。目标技术栈：" + targetStack.displayName()
                + "（" + targetStack.stackCode() + "）。项目级代码库根目录：" + generationRoot
                + "。本计划定义待生成文件、接口、数据库对象、运行命令和实施顺序；MODULE/PAGE 只向同一个项目代码库贡献代码，不再生成独立模块项目。";
    }

    private String calculatePlanChecksum(
            DevelopmentHandoffContextResponse context,
            DevelopmentTargetStack targetStack,
            String summary,
            List<String> principles,
            List<String> sourceArtifactTypes,
            List<DevelopmentPlanFileItem> frontendFiles,
            List<DevelopmentPlanFileItem> backendFiles,
            List<DevelopmentPlanApiItem> apiItems,
            List<DevelopmentPlanDatabaseItem> databaseItems,
            List<DevelopmentPlanStepItem> implementationSteps,
            List<DevelopmentPlanSourceMapping> sourceMappings
    ) {
        String checksumSource = String.join(
                "|",
                String.valueOf(context.projectId()),
                String.valueOf(context.moduleId()),
                safeText(context.nodeType(), ""),
                safeText(context.moduleKey(), ""),
                safeText(context.moduleName(), ""),
                targetStack.stackCode(),
                String.valueOf(targetStack.runtimeCommands()),
                summary,
                String.valueOf(principles),
                String.valueOf(sourceArtifactTypes),
                String.valueOf(frontendFiles),
                String.valueOf(backendFiles),
                String.valueOf(apiItems),
                String.valueOf(databaseItems),
                String.valueOf(implementationSteps),
                String.valueOf(sourceMappings)
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(
                    checksumSource.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder builder = new StringBuilder();

            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }

            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to calculate Development Plan checksum.",
                    e
            );
        }
    }

    private String buildGenerationRoot(
            String projectCode
    ) {
        String projectSegment = toPathSegment(projectCode, "project");

        return "generated/nodejs/" + projectSegment + "/app";
    }

    private String toPathSegment(
            String value,
            String fallback
    ) {
        String segment = safeText(value, fallback)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        if (segment.isBlank()) {
            return fallback;
        }

        return segment;
    }

    private String toClassName(
            String moduleKey,
            String moduleName
    ) {
        String source = safeText(moduleKey, moduleName);
        StringBuilder builder = new StringBuilder();

        for (String part : source.split("[^A-Za-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }

            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));

            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        if (builder.length() == 0) {
            builder.append("GeneratedModule");
        }

        if (Character.isDigit(builder.charAt(0))) {
            builder.insert(0, "M");
        }

        return builder.toString();
    }

    private String toKebabCase(
            String moduleKey,
            String moduleName
    ) {
        String source = safeText(moduleKey, moduleName)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        if (source.isBlank()) {
            return "generated-module";
        }

        return source;
    }

    private String safeText(
            String first,
            String fallback
    ) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }

        return "";
    }
}
