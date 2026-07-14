package com.codemind.platform.agent.requirement.dependency;

import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Legacy dependency policy kept only as a safe fallback for project-level tasks.
 *
 * Requirement artifact dependencies are now node-aware and are resolved in
 * RequirementTaskDependencyGuard through RequirementNodeGenerationPolicy:
 *
 * DOMAIN / MODULE / SUBMODULE / PAGE / FEATURE have different core / advanced
 * asset chains. Do not put global module-level upstream rules here again,
 * otherwise FEATURE API tasks will incorrectly depend on PAGE_SPEC / DATA_MODEL.
 */
@Component
public class RequirementTaskDependencyPolicy {

    public List<String> requiredModuleArtifacts(
            TaskType taskType
    ) {
        return List.of();
    }

    public List<String> requiredProjectArtifacts(
            TaskType taskType
    ) {
        return List.of();
    }
}
