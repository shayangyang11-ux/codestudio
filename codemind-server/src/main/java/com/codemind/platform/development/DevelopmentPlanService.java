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

    public DevelopmentPlanResponse generatePlan(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            GenerateDevelopmentPlanRequest request
    ) {
        DevelopmentHandoffContextResponse context =
                developmentHandoffGateService.getContext(
                        workspaceId,
                        projectCode,
                        moduleId
                );

        DevelopmentHandoffReadinessResponse readiness = context.readiness();

        if (!readiness.ready()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前节点尚未通过开发交接门禁，不能生成开发实施计划。"
            );
        }

        String nodeType = safeText(context.nodeType(), "MODULE");
        String moduleName = safeText(context.moduleName(), "CurrentModule");
        String moduleKey = safeText(context.moduleKey(), moduleName);
        String classBaseName = toClassName(moduleKey, moduleName);
        String kebabBaseName = toKebabCase(moduleKey, moduleName);

        List<DevelopmentPlanFileItem> frontendFiles = buildFrontendFiles(
                nodeType,
                classBaseName,
                kebabBaseName
        );

        List<DevelopmentPlanFileItem> backendFiles = buildBackendFiles(
                nodeType,
                classBaseName
        );

        List<DevelopmentPlanApiItem> apiItems = buildApiItems(
                nodeType,
                kebabBaseName,
                classBaseName,
                context
        );

        List<DevelopmentPlanDatabaseItem> databaseItems = buildDatabaseItems(
                nodeType,
                classBaseName,
                context
        );

        List<DevelopmentPlanStepItem> implementationSteps =
                buildImplementationSteps(nodeType);

        List<DevelopmentPlanSourceMapping> sourceMappings =
                buildSourceMappings(context);

        List<String> sourceArtifactTypes = collectSourceArtifactTypes(context);

        String summary = buildSummary(
                nodeType,
                moduleName,
                request == null ? null : request.targetStack()
        );

        List<String> principles = List.of(
                "以已确认需求资产为业务真相，不重新解释业务规则",
                "PAGE 节点以用户确认的 UI_CONTRACT 快照为页面蓝图",
                "真实代码只替换 Mock 数据与 Mock 动作为真实 API / Store / Service",
                "保留 UI_CONTRACT 中的 componentId、actionId、字段顺序和页面状态语义",
                "先生成可审查的实施计划，再进入真实代码生成"
        );

        String planChecksum = calculatePlanChecksum(
                context,
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

    private List<DevelopmentPlanFileItem> buildFrontendFiles(
            String nodeType,
            String classBaseName,
            String kebabBaseName
    ) {
        List<DevelopmentPlanFileItem> files = new ArrayList<>();

        if ("PAGE".equals(nodeType)) {
            files.add(frontend(
                    "codemind-web/src/pages/generated/" + classBaseName + "Page.vue",
                    "CREATE",
                    "UI_CONTRACT + PAGE_SPEC",
                    "按已确认 UI_CONTRACT 生成真实页面组件，保留布局、字段、动作和状态。",
                    1
            ));
            files.add(frontend(
                    "codemind-web/src/api/generated/" + kebabBaseName + "Api.ts",
                    "CREATE",
                    "API_CONTRACT",
                    "根据页面接口契约生成前端 API Client，替换 UI_CONTRACT 中的 Mock Action。",
                    2
            ));
            files.add(frontend(
                    "codemind-web/src/types/generated/" + kebabBaseName + ".ts",
                    "CREATE",
                    "DATA_MODEL + API_CONTRACT",
                    "生成页面 ViewModel、查询参数、表单模型和响应类型。",
                    3
            ));
            files.add(frontend(
                    "codemind-web/src/composables/generated/use" + classBaseName + ".ts",
                    "CREATE",
                    "UI_CONTRACT + API_CONTRACT",
                    "封装页面加载、提交、错误处理、空状态和成功状态逻辑。",
                    4
            ));
            return files;
        }

        if ("FEATURE".equals(nodeType)) {
            files.add(frontend(
                    "codemind-web/src/features/generated/" + kebabBaseName + "/" + classBaseName + "Action.ts",
                    "CREATE",
                    "MODULE_PRD + API_CONTRACT + TEST_SPEC",
                    "生成功能点动作处理逻辑，供所属页面接入。",
                    1
            ));
            files.add(frontend(
                    "codemind-web/src/features/generated/" + kebabBaseName + "/" + classBaseName + "Action.test.ts",
                    "CREATE",
                    "TEST_SPEC",
                    "生成功能点前端单元测试或交互测试骨架。",
                    2
            ));
            return files;
        }

        files.add(frontend(
                "codemind-web/src/modules/generated/" + kebabBaseName + "/" + classBaseName + "Module.ts",
                "CREATE",
                "MODULE_PRD + BUSINESS_FLOW",
                "生成模块级前端路由、菜单、页面清单和开发索引，不直接生成具体页面细节。",
                1
        ));
        files.add(frontend(
                "codemind-web/src/modules/generated/" + kebabBaseName + "/" + classBaseName + "README.md",
                "CREATE",
                "DEV_HANDOFF_PACKAGE",
                "生成模块级开发说明，记录子页面、接口、数据模型和交付边界。",
                2
        ));

        return files;
    }

    private List<DevelopmentPlanFileItem> buildBackendFiles(
            String nodeType,
            String classBaseName
    ) {
        List<DevelopmentPlanFileItem> files = new ArrayList<>();

        if ("PAGE".equals(nodeType) || "FEATURE".equals(nodeType)) {
            files.add(backend(
                    "codemind-server/src/main/java/com/codemind/generated/" + classBaseName + "Controller.java",
                    "CREATE",
                    "API_CONTRACT",
                    "生成页面或功能点对应的 REST Controller。",
                    1
            ));
            files.add(backend(
                    "codemind-server/src/main/java/com/codemind/generated/" + classBaseName + "Service.java",
                    "CREATE",
                    "MODULE_PRD + BUSINESS_FLOW",
                    "生成业务服务骨架，承载正式业务规则、状态变更和异常处理。",
                    2
            ));
            files.add(backend(
                    "codemind-server/src/main/java/com/codemind/generated/" + classBaseName + "Dto.java",
                    "CREATE",
                    "DATA_MODEL + API_CONTRACT",
                    "生成请求、响应和页面视图模型 DTO。",
                    3
            ));
        } else {
            files.add(backend(
                    "codemind-server/src/main/java/com/codemind/generated/" + classBaseName + "ModuleDesign.md",
                    "CREATE",
                    "MODULE_PRD + BUSINESS_FLOW + PAGE_SPEC",
                    "生成模块级后端开发拆分说明，不直接落具体接口实现。",
                    1
            ));
        }

        files.add(backend(
                "codemind-server/src/test/java/com/codemind/generated/" + classBaseName + "Test.java",
                "CREATE",
                "TEST_SPEC",
                "生成后端测试骨架，覆盖主路径、异常路径、权限与边界条件。",
                9
        ));

        return files;
    }

    private List<DevelopmentPlanApiItem> buildApiItems(
            String nodeType,
            String kebabBaseName,
            String classBaseName,
            DevelopmentHandoffContextResponse context
    ) {
        List<DevelopmentPlanApiItem> apis = new ArrayList<>();

        if ("PAGE".equals(nodeType)) {
            apis.add(new DevelopmentPlanApiItem(
                    "GET",
                    "/api/generated/" + kebabBaseName,
                    "页面初始化查询接口，用于替换 Mock 页面数据。",
                    List.of("API_CONTRACT", "DATA_MODEL", "UI_CONTRACT"),
                    "绑定 UI_CONTRACT 中的加载态、空状态、错误态和成功态。"
            ));
            apis.add(new DevelopmentPlanApiItem(
                    "POST",
                    "/api/generated/" + kebabBaseName + "/actions/{actionId}",
                    "页面动作提交接口，用于替换 UI_CONTRACT 中的按钮动作。",
                    List.of("API_CONTRACT", "BUSINESS_FLOW", "UI_CONTRACT"),
                    "actionId 必须来源于已确认 UI_CONTRACT，不允许开发阶段重命名。"
            ));
        } else if ("FEATURE".equals(nodeType)) {
            apis.add(new DevelopmentPlanApiItem(
                    "POST",
                    "/api/generated/actions/" + kebabBaseName,
                    classBaseName + " 功能点动作接口。",
                    List.of("MODULE_PRD", "API_CONTRACT", "TEST_SPEC"),
                    "只实现当前功能点动作，不扩展页面级交互。"
            ));
        } else {
            apis.add(new DevelopmentPlanApiItem(
                    "REVIEW",
                    "/api/generated/" + kebabBaseName + "/**",
                    "模块级接口规划审查项。",
                    List.of("API_CONTRACT", "PAGE_SPEC"),
                    "模块级节点先生成接口规划，不直接落全部接口代码。"
            ));
        }

        return apis;
    }

    private List<DevelopmentPlanDatabaseItem> buildDatabaseItems(
            String nodeType,
            String classBaseName,
            DevelopmentHandoffContextResponse context
    ) {
        if ("FEATURE".equals(nodeType)) {
            return List.of(
                    new DevelopmentPlanDatabaseItem(
                            classBaseName + "AuditLog",
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
                        List.of("DATA_MODEL"),
                        "根据数据模型决定是否创建实体、索引、状态字段和约束。"
                ),
                new DevelopmentPlanDatabaseItem(
                        classBaseName + "Migration",
                        "CREATE",
                        List.of("DATA_MODEL", "API_CONTRACT"),
                        "生成数据库迁移脚本草案，真实执行前必须二次审查。"
                )
        );
    }

    private List<DevelopmentPlanStepItem> buildImplementationSteps(
            String nodeType
    ) {
        return List.of(
                new DevelopmentPlanStepItem(
                        1,
                        "锁定开发输入",
                        "读取当前节点正式资产、开发交接门禁结果、确认后的 UI_CONTRACT 快照和主题快照。",
                        true
                ),
                new DevelopmentPlanStepItem(
                        2,
                        "生成代码文件清单",
                        "按前端、后端、接口、数据库、测试五类生成待创建/待修改文件清单。",
                        true
                ),
                new DevelopmentPlanStepItem(
                        3,
                        "生成最小可运行骨架",
                        "先落类型、API Client、Controller、Service、页面骨架和测试骨架，避免一次性生成不可控大文件。",
                        false
                ),
                new DevelopmentPlanStepItem(
                        4,
                        "替换 Mock 数据与 Mock 动作",
                        "PAGE 节点将 UI_CONTRACT 的 mockData / mockAction 替换为真实 API、Store 和 Service。",
                        "PAGE".equals(nodeType)
                ),
                new DevelopmentPlanStepItem(
                        5,
                        "执行构建与质量回读",
                        "执行前端 type-check/build 与后端 compile/test，把错误回写为下一轮开发修复任务。",
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
                    "用于保持真实页面与需求阶段 Mock Preview 的视觉风格一致。",
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
            case "DATA_MODEL" -> "实体、字段、ViewModel、表单模型和数据库设计来源。";
            case "API_CONTRACT" -> "接口路径、请求响应、错误码、权限和前后端绑定来源。";
            case "TEST_SPEC" -> "测试用例、验收路径、边界条件和自动化测试来源。";
            case "UI_CONTRACT" -> "页面原型、组件树、动作 ID、状态和 Mock 替换关系来源。";
            case "DEV_HANDOFF_PACKAGE" -> "开发注意事项、风险、交付边界和实施顺序来源。";
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

    private String buildSummary(
            String nodeType,
            String moduleName,
            String targetStack
    ) {
        String stack = safeText(targetStack, "当前项目默认技术栈");

        return "针对“" + moduleName + "”生成 " + nodeType
                + " 级开发实施计划。目标技术栈：" + stack
                + "。本计划只定义待生成文件、接口、数据库变更和实施顺序，不直接写入真实代码。";
    }

    private String calculatePlanChecksum(
            DevelopmentHandoffContextResponse context,
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
