package com.codemind.platform.task;

import java.util.List;

public record RequirementArtifactTaskDefinition(
        String artifactType,
        String taskType,
        String title,
        int sortOrder
) {

    public static final List<RequirementArtifactTaskDefinition> REQUIREMENT_ARTIFACT_TASKS = List.of(
            new RequirementArtifactTaskDefinition("MODULE_PRD", "GENERATE_MODULE_PRD", "需求说明", 10),
            new RequirementArtifactTaskDefinition("BUSINESS_FLOW", "GENERATE_BUSINESS_FLOW", "流程说明", 20),
            new RequirementArtifactTaskDefinition("PAGE_MAP", "GENERATE_PAGE_MAP", "页面地图", 25),
            new RequirementArtifactTaskDefinition("PAGE_SPEC", "GENERATE_PAGE_SPEC", "页面规范", 30),
            new RequirementArtifactTaskDefinition("DATA_MODEL", "GENERATE_DATA_MODEL", "数据模型", 40),
            new RequirementArtifactTaskDefinition("API_CONTRACT", "GENERATE_API_CONTRACT", "接口契约", 50),
            new RequirementArtifactTaskDefinition("TEST_SPEC", "GENERATE_TEST_SPEC", "测试规格", 60),
            new RequirementArtifactTaskDefinition("UI_CONTRACT", "GENERATE_UI_CONTRACT", "交互原型", 65),
            new RequirementArtifactTaskDefinition("DEV_HANDOFF_PACKAGE", "GENERATE_DEV_HANDOFF_PACKAGE", "开发交接包", 70)
    );
}
