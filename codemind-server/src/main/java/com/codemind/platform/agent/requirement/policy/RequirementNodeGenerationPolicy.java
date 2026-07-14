package com.codemind.platform.agent.requirement.policy;

import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class RequirementNodeGenerationPolicy {

    public enum RequirementNodeType {
        DOMAIN,
        MODULE,
        SUBMODULE,
        PAGE,
        FEATURE
    }

    public enum RequirementArtifactImportance {
        CORE,
        ADVANCED
    }

    public record RequirementArtifactPlan(
            int order,
            TaskType taskType,
            String artifactType,
            String title,
            int priority
    ) {
    }

    private record RequirementArtifactPlanSpec(
            RequirementArtifactPlan plan,
            RequirementArtifactImportance importance
    ) {
    }

    public RequirementNodeType resolveNodeType(ProjectModuleEntity module) {
        if (module == null) {
            return RequirementNodeType.MODULE;
        }

        String raw = firstNonBlank(
                module.getModuleType(),
                readExtraAsString(module, "moduleType"),
                readExtraAsString(module, "type"),
                readExtraAsString(module, "nodeType")
        );

        if (raw == null || raw.isBlank()) {
            return RequirementNodeType.MODULE;
        }

        String value = raw.trim().toUpperCase(Locale.ROOT);

        return switch (value) {
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> RequirementNodeType.DOMAIN;
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> RequirementNodeType.SUBMODULE;
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> RequirementNodeType.PAGE;
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION" -> RequirementNodeType.FEATURE;
            case "MODULE", "BUSINESS_MODULE", "FEATURE_MODULE", "INTEGRATION_MODULE", "INFRA_MODULE" -> RequirementNodeType.MODULE;
            default -> RequirementNodeType.MODULE;
        };
    }

    public boolean isPage(ProjectModuleEntity module) {
        return RequirementNodeType.PAGE.equals(resolveNodeType(module));
    }

    public boolean isFeature(ProjectModuleEntity module) {
        return RequirementNodeType.FEATURE.equals(resolveNodeType(module));
    }

    public boolean canGenerate(ProjectModuleEntity module, TaskType taskType) {
        if (taskType == null) {
            return false;
        }

        RequirementNodeType nodeType = resolveNodeType(module);

        return allPlanSpecsFor(nodeType).stream()
                .anyMatch(spec -> spec.plan().taskType().equals(taskType));
    }

    public void assertCanGenerate(ProjectModuleEntity module, TaskType taskType) {
        if (canGenerate(module, taskType)) {
            return;
        }

        RequirementNodeType nodeType = resolveNodeType(module);

        if (TaskType.GENERATE_UI_CONTRACT.equals(taskType)) {
            if (RequirementNodeType.FEATURE.equals(nodeType)) {
                throw new IllegalArgumentException("当前选中节点是页面功能点，请在其所属 PAGE 页面节点生成交互原型。");
            }

            throw new IllegalArgumentException("交互原型只能基于 PAGE 页面节点生成。请在模块树中选择具体页面节点。");
        }

        if (RequirementNodeType.FEATURE.equals(nodeType)
                && TaskType.GENERATE_PAGE_SPEC.equals(taskType)) {
            throw new IllegalArgumentException("功能点不生成完整页面规范，请在所属 PAGE 页面节点生成页面规范。");
        }

        if (RequirementNodeType.FEATURE.equals(nodeType)
                && TaskType.GENERATE_DATA_MODEL.equals(taskType)) {
            throw new IllegalArgumentException("功能点不单独生成页面数据模型，请在所属 PAGE 页面节点生成页面级数据模型。");
        }

        throw new IllegalArgumentException("当前节点类型不支持该资产生成任务。");
    }

    /**
     * Full-chain generation now means “current node core assets”, not every possible asset.
     * Advanced assets are still allowed, but should be generated explicitly when the user needs
     * delivery review, deep design, or development-readiness completion.
     */
    public List<RequirementArtifactPlan> fullChainDefinitionsFor(ProjectModuleEntity module) {
        RequirementNodeType nodeType = resolveNodeType(module);

        return allPlanSpecsFor(nodeType).stream()
                .filter(spec -> RequirementArtifactImportance.CORE.equals(spec.importance()))
                .map(RequirementArtifactPlanSpec::plan)
                .toList();
    }

    public List<RequirementArtifactPlan> allDefinitionsFor(ProjectModuleEntity module) {
        RequirementNodeType nodeType = resolveNodeType(module);

        return allPlanSpecsFor(nodeType).stream()
                .map(RequirementArtifactPlanSpec::plan)
                .toList();
    }

    public List<RequirementArtifactPlan> advancedDefinitionsFor(ProjectModuleEntity module) {
        RequirementNodeType nodeType = resolveNodeType(module);

        return allPlanSpecsFor(nodeType).stream()
                .filter(spec -> RequirementArtifactImportance.ADVANCED.equals(spec.importance()))
                .map(RequirementArtifactPlanSpec::plan)
                .toList();
    }

    public RequirementArtifactImportance resolveArtifactImportance(ProjectModuleEntity module, TaskType taskType) {
        RequirementNodeType nodeType = resolveNodeType(module);

        return allPlanSpecsFor(nodeType).stream()
                .filter(spec -> spec.plan().taskType().equals(taskType))
                .map(RequirementArtifactPlanSpec::importance)
                .findFirst()
                .orElse(RequirementArtifactImportance.ADVANCED);
    }

    public String buildRequirementInstruction(
            ProjectModuleEntity module,
            TaskType taskType,
            String baseRequirementText
    ) {
        String moduleName = module == null ? "当前节点" : module.getModuleName();
        RequirementNodeType nodeType = resolveNodeType(module);
        RequirementArtifactImportance importance = resolveArtifactImportance(module, taskType);

        String base = baseRequirementText == null || baseRequirementText.isBlank()
                ? "请基于当前节点生成企业级需求工程资产。"
                : baseRequirementText.trim();

        String scopePrefix = switch (nodeType) {
            case DOMAIN -> "当前节点是业务域节点。产物必须偏汇总和边界说明，只说明业务域目标、范围、模块组成、跨模块流程和高层约束。不要展开下层模块、页面、功能点的详细字段、按钮、接口参数或局部交互。";
            case MODULE -> "当前节点是模块节点。产物必须偏模块级规划，只说明模块边界、角色、核心能力、页面地图、接口规划和开发顺序。下层 PAGE 的页面字段、布局、按钮、状态和 FEATURE 的动作逻辑留给下层节点。";
            case SUBMODULE -> "当前节点是子模块/业务场景节点。产物必须偏场景组织，只说明场景目标、边界、参与页面、页面协作、关键规则和上下游衔接。具体页面规范和功能点细节留给 PAGE / FEATURE 节点。";
            case PAGE -> "当前节点是 PAGE 页面节点。产物应成为前端开发 Agent 的页面级输入，重点说明页面目标、入口、布局、字段、按钮、校验、状态、接口绑定、UI 契约和验收标准。页面内单个按钮动作的详细规则可由 FEATURE 节点补充。";
            case FEATURE -> "当前节点是页面功能点。产物必须偏局部动作和可执行逻辑，只说明该功能点的触发条件、输入输出、校验、成功失败逻辑、权限点、接口动作、测试点和开发风险。不要生成完整页面规范或完整交互原型。";
        };

        String taskInstruction = switch (taskType) {
            case GENERATE_MODULE_PRD -> buildPrdInstruction(nodeType);
            case GENERATE_BUSINESS_FLOW -> buildFlowInstruction(nodeType);
            case GENERATE_PAGE_SPEC -> buildPageSpecInstruction(nodeType);
            case GENERATE_DATA_MODEL -> buildDataModelInstruction(nodeType);
            case GENERATE_API_CONTRACT -> buildApiContractInstruction(nodeType);
            case GENERATE_TEST_SPEC -> buildTestSpecInstruction(nodeType);
            case GENERATE_UI_CONTRACT -> "请生成 PAGE 级 UI_CONTRACT。它必须能被 Mock Preview Runtime 渲染成完整页面，并作为后续开发 Agent 生成真实页面的唯一页面蓝图。输出必须包含布局、区块、字段、按钮、表格、弹窗、状态、接口绑定和 Mock 数据结构。";
            case GENERATE_DEV_HANDOFF_PACKAGE -> buildDevHandoffInstruction(nodeType);
            default -> "请生成当前任务对应的企业级需求工程资产。";
        };

        String importanceInstruction = switch (importance) {
            case CORE -> "本资产是当前层级核心资产。请优先保证甲方能看懂、开发 Agent 能使用，不要堆砌无关章节。";
            case ADVANCED -> "本资产是高级资产。仅补充正式交付、深度审查或开发准备度所需内容，不要重复核心资产已经完整表达的内容。";
        };

        return base
                + "\n【节点作用域】\n"
                + scopePrefix
                + "\n【资产重要性】\n"
                + importanceInstruction
                + "\n【本次任务要求】\n"
                + taskInstruction
                + "\n【当前节点】\n"
                + "名称：" + moduleName
                + "\n类型：" + nodeType.name()
                + "\n路径：" + (module == null ? "" : Objects.toString(module.getModulePath(), ""));

    }

    private String buildPrdInstruction(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "请生成业务域概览，聚焦业务域目标、边界、模块组成、关键参与方、跨模块约束和高层验收标准。不要详细描述下层页面字段、按钮和接口参数。";
            case MODULE -> "请生成模块 PRD，聚焦模块边界、角色、核心能力、页面组成、业务规则、异常场景和验收标准。页面详细布局、字段、交互和功能点逻辑留给 PAGE / FEATURE 节点。";
            case SUBMODULE -> "请生成场景需求说明，聚焦该业务场景的目标、页面组成、核心规则、上下游关系和验收标准。不要写成完整页面规范。";
            case PAGE -> "请生成页面需求说明，而不是模块级大而全 PRD。聚焦页面目标、入口、用户任务、关键字段、关键操作、状态和验收标准。";
            case FEATURE -> "请生成功能点需求说明。只描述该功能点的触发条件、输入输出、前置校验、规则边界、成功失败路径、权限点和验收标准，不要生成完整页面。";
        };
    }

    private String buildFlowInstruction(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "请生成业务域流程总览，覆盖跨模块主流程、关键分支、异常流和模块协作关系。不要展开页面内按钮点击细节。";
            case MODULE -> "请生成模块业务流程，覆盖主流程、分支流程、异常流程、状态流转和关键规则。具体页面内操作留给 PAGE 层。";
            case SUBMODULE -> "请生成场景业务流程，覆盖该场景内页面流转、规则判断、异常分支和上下游衔接。";
            case PAGE -> "请生成页面内交互流程，覆盖按钮、弹窗、表单提交、状态变化、异常提示和接口触发。";
            case FEATURE -> "请生成该功能点的执行流程，覆盖前置条件、处理步骤、成功路径、失败路径、权限拦截和边界条件。";
        };
    }

    private String buildPageSpecInstruction(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "请生成模块/页面地图，说明业务域下模块、页面、入口、跳转关系和信息架构，不要展开单页字段细节。";
            case MODULE -> "请生成页面地图，说明模块下有哪些页面、页面入口、页面关系、跳转路径和页面职责，不要展开每个页面的字段、按钮、表格列和弹窗。";
            case SUBMODULE -> "请生成场景页面清单，说明该场景涉及哪些页面、页面职责、入口出口和页面间协作关系。";
            case PAGE -> "请生成页面详细规范，包括布局、字段、表格列、按钮、弹窗、校验、空状态、加载状态、错误状态和权限状态。";
            case FEATURE -> "功能点不应生成完整页面规范。若必须补充，请只描述该功能点对所属页面局部交互的影响。";
        };
    }

    private String buildDataModelInstruction(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "请生成数据主题总览，说明业务域内核心数据主题、主数据、关键关系和跨模块数据边界，不展开实体级所有字段。";
            case MODULE -> "请生成模块数据模型，覆盖实体、字段、关系、索引、状态模型、约束和数据生命周期，但不要重复每个页面的 ViewModel。";
            case SUBMODULE -> "请生成场景数据约束，说明该场景涉及的数据对象、关键字段、状态约束和上下游数据关系。";
            case PAGE -> "请生成页面 ViewModel、查询模型、表单模型、展示模型、状态模型和接口数据绑定。";
            case FEATURE -> "功能点不单独生成页面数据模型。请在所属 PAGE 页面节点生成页面级数据模型。";
        };
    }

    private String buildApiContractInstruction(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "请生成接口域总览，说明业务域内接口边界、模块间接口关系、权限策略和联调约束，不要展开所有接口字段细节。";
            case MODULE -> "请生成模块接口规划，覆盖接口分组、接口清单、核心对象、权限边界、上下游系统关系和联调约束。不要把每个页面按钮接口都展开成详细契约。";
            case SUBMODULE -> "请生成场景接口清单，覆盖该业务场景涉及的查询、提交、校验、审批、状态变更等接口。";
            case PAGE -> "请生成当前页面绑定的查询、保存、提交、删除、审批等接口契约，明确请求响应、错误码、权限和前端绑定关系。";
            case FEATURE -> "请生成该功能点对应的动作接口契约，明确触发动作、请求参数、响应结构、错误码、权限点、幂等性和边界条件。";
        };
    }

    private String buildTestSpecInstruction(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "请生成业务域测试范围说明，覆盖跨模块端到端场景、关键风险、回归范围和验收边界。";
            case MODULE -> "请生成模块测试规格，覆盖端到端业务流程、模块边界、权限、异常、数据一致性和验收标准。";
            case SUBMODULE -> "请生成场景测试规格，覆盖该场景主路径、分支路径、异常路径、权限和上下游衔接。";
            case PAGE -> "请生成页面测试规格，覆盖字段校验、页面交互、权限、接口、状态、弹窗、空状态和异常状态。";
            case FEATURE -> "请生成该功能点的测试规格，覆盖正常路径、异常路径、权限、边界条件、接口错误和验收标准。";
        };
    }

    private String buildDevHandoffInstruction(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> "请生成业务域交付说明，汇总业务边界、模块组成、关键流程、跨模块约束和开发拆分建议。";
            case MODULE -> "请生成模块开发交接包，汇总模块约束、页面清单、接口规划、数据模型、测试重点和开发顺序。";
            case SUBMODULE -> "请生成场景开发说明，汇总场景边界、页面协作、规则约束、接口依赖、测试重点和开发风险。";
            case PAGE -> "请生成页面开发交接包，覆盖前端组件、接口、状态、校验、测试和 UI_CONTRACT 落地要求。";
            case FEATURE -> "请生成功能点开发说明，覆盖功能边界、接口动作、状态影响、测试点、权限点和开发风险。";
        };
    }

    public Map<String, Object> buildGenerationScope(ProjectModuleEntity module, TaskType taskType) {
        RequirementNodeType nodeType = resolveNodeType(module);
        RequirementArtifactImportance importance = resolveArtifactImportance(module, taskType);

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("nodeType", nodeType.name());
        scope.put("generationLevel", switch (nodeType) {
            case DOMAIN -> "DOMAIN_LEVEL";
            case MODULE -> "MODULE_LEVEL";
            case SUBMODULE -> "SUBMODULE_LEVEL";
            case PAGE -> "PAGE_LEVEL";
            case FEATURE -> "FEATURE_LEVEL";
        });
        scope.put("taskType", taskType == null ? null : taskType.name());
        scope.put("artifactSemantics", taskType == null ? "UNKNOWN" : nodeType.name() + "_" + taskType.name());
        scope.put("artifactImportance", importance.name());
        scope.put("defaultGenerationMode", RequirementArtifactImportance.CORE.equals(importance) ? "CORE_ASSET" : "ADVANCED_ASSET");

        if (module != null) {
            scope.put("nodeId", module.getId() == null ? null : module.getId().toString());
            scope.put("nodeKey", module.getModuleKey());
            scope.put("nodeName", module.getModuleName());
            scope.put("nodePath", module.getModulePath());
            scope.put("parentNodeId", module.getParentId() == null ? null : module.getParentId().toString());
            scope.put("extra", module.getExtra() == null ? Map.of() : module.getExtra());
        }

        return scope;
    }

    public Map<String, Object> buildCurrentModulePayload(ProjectModuleEntity module) {
        Map<String, Object> currentModule = new LinkedHashMap<>();

        if (module == null) {
            return currentModule;
        }

        currentModule.put("module_id", module.getId() == null ? null : module.getId().toString());
        currentModule.put("module_key", module.getModuleKey());
        currentModule.put("module_name", module.getModuleName());
        currentModule.put("module_type", resolveNodeType(module).name());
        currentModule.put("module_path", module.getModulePath());
        currentModule.put("level_no", module.getLevelNo());
        currentModule.put("sort_order", module.getSortOrder());
        currentModule.put("extra", module.getExtra() == null ? Map.of() : module.getExtra());

        return currentModule;
    }

    private List<RequirementArtifactPlanSpec> allPlanSpecsFor(RequirementNodeType nodeType) {
        return switch (nodeType) {
            case DOMAIN -> domainPlanSpecs();
            case MODULE -> modulePlanSpecs();
            case SUBMODULE -> submodulePlanSpecs();
            case PAGE -> pagePlanSpecs();
            case FEATURE -> featurePlanSpecs();
        };
    }

    private List<RequirementArtifactPlanSpec> domainPlanSpecs() {
        return List.of(
                core(10, TaskType.GENERATE_MODULE_PRD, "MODULE_PRD", "业务域概览", 90),
                core(20, TaskType.GENERATE_BUSINESS_FLOW, "BUSINESS_FLOW", "业务域流程总览", 80),
                core(30, TaskType.GENERATE_PAGE_SPEC, "PAGE_SPEC", "模块/页面地图", 70),
                advanced(40, TaskType.GENERATE_DATA_MODEL, "DATA_MODEL", "数据主题总览", 40),
                advanced(50, TaskType.GENERATE_API_CONTRACT, "API_CONTRACT", "接口域总览", 35),
                advanced(60, TaskType.GENERATE_TEST_SPEC, "TEST_SPEC", "业务域测试范围", 25),
                advanced(70, TaskType.GENERATE_DEV_HANDOFF_PACKAGE, "DEV_HANDOFF_PACKAGE", "业务域交付说明", 15)
        );
    }

    private List<RequirementArtifactPlanSpec> modulePlanSpecs() {
        return List.of(
                core(10, TaskType.GENERATE_MODULE_PRD, "MODULE_PRD", "模块 PRD", 90),
                core(20, TaskType.GENERATE_BUSINESS_FLOW, "BUSINESS_FLOW", "模块业务流程", 80),
                core(30, TaskType.GENERATE_PAGE_SPEC, "PAGE_SPEC", "页面地图", 70),
                core(50, TaskType.GENERATE_API_CONTRACT, "API_CONTRACT", "模块接口规划", 60),
                advanced(40, TaskType.GENERATE_DATA_MODEL, "DATA_MODEL", "模块数据模型", 45),
                advanced(60, TaskType.GENERATE_TEST_SPEC, "TEST_SPEC", "模块测试规格", 30),
                advanced(70, TaskType.GENERATE_DEV_HANDOFF_PACKAGE, "DEV_HANDOFF_PACKAGE", "模块开发交接包", 20)
        );
    }

    private List<RequirementArtifactPlanSpec> submodulePlanSpecs() {
        return List.of(
                core(10, TaskType.GENERATE_MODULE_PRD, "MODULE_PRD", "场景需求说明", 90),
                core(20, TaskType.GENERATE_BUSINESS_FLOW, "BUSINESS_FLOW", "场景业务流程", 80),
                core(30, TaskType.GENERATE_PAGE_SPEC, "PAGE_SPEC", "场景页面清单", 70),
                advanced(50, TaskType.GENERATE_API_CONTRACT, "API_CONTRACT", "场景接口清单", 40),
                advanced(60, TaskType.GENERATE_TEST_SPEC, "TEST_SPEC", "场景测试规格", 30),
                advanced(70, TaskType.GENERATE_DEV_HANDOFF_PACKAGE, "DEV_HANDOFF_PACKAGE", "场景开发说明", 20)
        );
    }

    private List<RequirementArtifactPlanSpec> pagePlanSpecs() {
        return List.of(
                core(10, TaskType.GENERATE_MODULE_PRD, "MODULE_PRD", "页面需求", 90),
                core(30, TaskType.GENERATE_PAGE_SPEC, "PAGE_SPEC", "页面规范", 80),
                core(50, TaskType.GENERATE_API_CONTRACT, "API_CONTRACT", "页面接口契约", 70),
                core(65, TaskType.GENERATE_UI_CONTRACT, "UI_CONTRACT", "交互原型", 60),
                advanced(20, TaskType.GENERATE_BUSINESS_FLOW, "BUSINESS_FLOW", "页面交互流程", 45),
                advanced(40, TaskType.GENERATE_DATA_MODEL, "DATA_MODEL", "页面数据模型", 40),
                advanced(60, TaskType.GENERATE_TEST_SPEC, "TEST_SPEC", "页面测试规格", 35),
                advanced(70, TaskType.GENERATE_DEV_HANDOFF_PACKAGE, "DEV_HANDOFF_PACKAGE", "页面开发交接包", 20)
        );
    }

    private List<RequirementArtifactPlanSpec> featurePlanSpecs() {
        return List.of(
                core(10, TaskType.GENERATE_MODULE_PRD, "MODULE_PRD", "功能点需求", 90),
                core(50, TaskType.GENERATE_API_CONTRACT, "API_CONTRACT", "功能点接口", 70),
                core(60, TaskType.GENERATE_TEST_SPEC, "TEST_SPEC", "功能点测试", 60),
                advanced(20, TaskType.GENERATE_BUSINESS_FLOW, "BUSINESS_FLOW", "功能点流程", 40),
                advanced(70, TaskType.GENERATE_DEV_HANDOFF_PACKAGE, "DEV_HANDOFF_PACKAGE", "功能点开发说明", 20)
        );
    }

    private RequirementArtifactPlanSpec core(
            int order,
            TaskType taskType,
            String artifactType,
            String title,
            int priority
    ) {
        return new RequirementArtifactPlanSpec(
                new RequirementArtifactPlan(order, taskType, artifactType, title, priority),
                RequirementArtifactImportance.CORE
        );
    }

    private RequirementArtifactPlanSpec advanced(
            int order,
            TaskType taskType,
            String artifactType,
            String title,
            int priority
    ) {
        return new RequirementArtifactPlanSpec(
                new RequirementArtifactPlan(order, taskType, artifactType, title, priority),
                RequirementArtifactImportance.ADVANCED
        );
    }

    private String readExtraAsString(ProjectModuleEntity module, String key) {
        if (module == null || module.getExtra() == null) {
            return null;
        }
        Object value = module.getExtra().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}