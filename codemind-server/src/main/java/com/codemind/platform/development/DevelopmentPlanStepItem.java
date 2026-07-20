package com.codemind.platform.development;

public record DevelopmentPlanStepItem(
        int stepNo,
        String title,
        String description,
        boolean requiredBeforeCodeGeneration
) {
}
