package com.codemind.platform.agent.requirement.dependency;

import java.util.List;

public record RequirementTaskDependencyCheckResult(
        boolean passed,
        List<String> requiredArtifactTypes,
        List<String> missingArtifactTypes,
        String message
) {

    public static RequirementTaskDependencyCheckResult passed(
            List<String> requiredArtifactTypes
    ) {
        return new RequirementTaskDependencyCheckResult(
                true,
                requiredArtifactTypes,
                List.of(),
                "All required upstream artifacts are available."
        );
    }

    public static RequirementTaskDependencyCheckResult blocked(
            List<String> requiredArtifactTypes,
            List<String> missingArtifactTypes
    ) {
        return new RequirementTaskDependencyCheckResult(
                false,
                requiredArtifactTypes,
                missingArtifactTypes,
                "Missing required upstream artifacts: " + String.join(", ", missingArtifactTypes)
        );
    }
}