package com.codemind.platform.agent.requirement.dependency;

import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy.RequirementArtifactImportance;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy.RequirementArtifactPlan;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RequirementTaskDependencyGuard {

    private static final String ACTIVE = "ACTIVE";

    private final RequirementTaskDependencyPolicy dependencyPolicy;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ProjectModuleRepository projectModuleRepository;
    private final RequirementNodeGenerationPolicy nodeGenerationPolicy;

    public RequirementTaskDependencyGuard(
            RequirementTaskDependencyPolicy dependencyPolicy,
            ArtifactVersionRepository artifactVersionRepository,
            ProjectModuleRepository projectModuleRepository,
            RequirementNodeGenerationPolicy nodeGenerationPolicy
    ) {
        this.dependencyPolicy = dependencyPolicy;
        this.artifactVersionRepository = artifactVersionRepository;
        this.projectModuleRepository = projectModuleRepository;
        this.nodeGenerationPolicy = nodeGenerationPolicy;
    }

    public RequirementTaskDependencyCheckResult check(
            AgentTaskEntity task
    ) {
        TaskType taskType = TaskType.valueOf(task.getTaskType());

        List<String> requiredModuleArtifacts = requiredNodeAwareModuleArtifacts(
                task,
                taskType
        );

        List<String> requiredProjectArtifacts =
                dependencyPolicy.requiredProjectArtifacts(taskType);

        List<String> allRequired = new ArrayList<>();
        allRequired.addAll(requiredModuleArtifacts);
        allRequired.addAll(requiredProjectArtifacts);

        if (allRequired.isEmpty()) {
            return RequirementTaskDependencyCheckResult.passed(List.of());
        }

        List<String> missing = new ArrayList<>();

        for (String artifactType : requiredModuleArtifacts) {
            if (task.getModuleId() == null) {
                missing.add(artifactType);
                continue;
            }

            boolean exists = artifactVersionRepository
                    .existsByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueAndStatus(
                            task.getProjectId(),
                            task.getModuleId(),
                            artifactType,
                            ACTIVE
                    );

            if (!exists) {
                boolean usableCurrentVersion = artifactVersionRepository
                        .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                                task.getProjectId(),
                                task.getModuleId(),
                                artifactType
                        )
                        .filter(artifact -> !"REJECTED".equalsIgnoreCase(artifact.getStatus()))
                        .filter(artifact -> !"ARCHIVED".equalsIgnoreCase(artifact.getStatus()))
                        .filter(artifact -> !"SUPERSEDED".equalsIgnoreCase(artifact.getStatus()))
                        .isPresent();

                if (!usableCurrentVersion) {
                    missing.add(artifactType);
                }
            }
        }

        for (String artifactType : requiredProjectArtifacts) {
            boolean exists = artifactVersionRepository
                    .existsByProjectIdAndModuleIdIsNullAndArtifactTypeAndCurrentTrueAndStatus(
                            task.getProjectId(),
                            artifactType,
                            ACTIVE
                    );

            if (!exists) {
                missing.add(artifactType);
            }
        }

        if (missing.isEmpty()) {
            return RequirementTaskDependencyCheckResult.passed(allRequired);
        }

        return RequirementTaskDependencyCheckResult.blocked(
                allRequired,
                missing
        );
    }

    /**
     * UI_CONTRACT does not use the old "all previous core assets" rule.
     *
     * Generic task dependency:
     * - PAGE_SPEC only.
     *
     * Dedicated readiness validation before generation:
     * - selected node is PAGE;
     * - interactive PAGE has FEATURE children;
     * - every FEATURE has a usable MODULE_PRD or complete structured extra;
     * - BUSINESS_FLOW / DATA_MODEL / API_CONTRACT / TEST_SPEC are warnings.
     */
    private List<String> requiredNodeAwareModuleArtifacts(
            AgentTaskEntity task,
            TaskType taskType
    ) {
        if (TaskType.GENERATE_UI_CONTRACT.equals(taskType)) {
            return List.of("PAGE_SPEC");
        }

        if (task.getModuleId() == null) {
            return dependencyPolicy.requiredModuleArtifacts(taskType);
        }

        ProjectModuleEntity module = projectModuleRepository
                .findById(task.getModuleId())
                .orElse(null);

        if (module == null) {
            return dependencyPolicy.requiredModuleArtifacts(taskType);
        }

        List<RequirementArtifactPlan> allPlans =
                nodeGenerationPolicy.allDefinitionsFor(module);

        RequirementArtifactPlan targetPlan = allPlans.stream()
                .filter(plan -> plan.taskType().equals(taskType))
                .findFirst()
                .orElse(null);

        if (targetPlan == null) {
            return dependencyPolicy.requiredModuleArtifacts(taskType);
        }

        RequirementArtifactImportance targetImportance =
                nodeGenerationPolicy.resolveArtifactImportance(
                        module,
                        taskType
                );

        List<RequirementArtifactPlan> dependencyPlans =
                RequirementArtifactImportance.CORE.equals(targetImportance)
                        ? nodeGenerationPolicy.fullChainDefinitionsFor(module)
                        : allPlans;

        Set<String> requiredArtifactTypes = new LinkedHashSet<>();

        for (RequirementArtifactPlan plan : dependencyPlans) {
            if (plan.order() >= targetPlan.order()) {
                continue;
            }

            if (!nodeGenerationPolicy.canGenerate(module, plan.taskType())) {
                continue;
            }

            requiredArtifactTypes.add(plan.artifactType());
        }

        return List.copyOf(requiredArtifactTypes);
    }
}
