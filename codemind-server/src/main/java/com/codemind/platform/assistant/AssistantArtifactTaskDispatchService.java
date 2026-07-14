package com.codemind.platform.assistant;

import com.codemind.platform.agent.requirement.dto.AsyncAgentTaskResponse;
import com.codemind.platform.agent.requirement.dto.FullChainAsyncTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateFullChainRequest;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy.RequirementArtifactImportance;
import com.codemind.platform.agent.requirement.policy.RequirementNodeGenerationPolicy.RequirementArtifactPlan;
import com.codemind.platform.agent.requirement.service.RequirementArtifactAsyncTaskService;
import com.codemind.platform.agent.requirement.service.RequirementFullChainAsyncTaskService;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantArtifactTaskDispatchService {

    private static final String ACTIVE = "ACTIVE";

    private final RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService;
    private final RequirementFullChainAsyncTaskService requirementFullChainAsyncTaskService;
    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final RequirementNodeGenerationPolicy nodeGenerationPolicy;

    public AssistantArtifactTaskDispatchService(
            RequirementArtifactAsyncTaskService requirementArtifactAsyncTaskService,
            RequirementFullChainAsyncTaskService requirementFullChainAsyncTaskService,
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            RequirementNodeGenerationPolicy nodeGenerationPolicy
    ) {
        this.requirementArtifactAsyncTaskService = requirementArtifactAsyncTaskService;
        this.requirementFullChainAsyncTaskService = requirementFullChainAsyncTaskService;
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.nodeGenerationPolicy = nodeGenerationPolicy;
    }

    @Transactional
    public AssistantArtifactTaskDispatchResult dispatch(
            UUID workspaceId,
            String projectCode,
            UUID selectedNodeId,
            AssistantArtifactTaskCommand command
    ) {
        if (selectedNodeId == null) {
            return new AssistantArtifactTaskDispatchResult(
                    "请先在左侧模块树中选择一个节点，再生成需求资产。比如先点中某个 PAGE 页面或 FEATURE 功能点，再说“生成功能点接口”。",
                    List.of()
            );
        }

        if (command.fullChain()) {
            return dispatchFullChain(
                    workspaceId,
                    projectCode,
                    selectedNodeId,
                    command
            );
        }

        return dispatchSingleArtifactWithDependencies(
                workspaceId,
                projectCode,
                selectedNodeId,
                command
        );
    }

    private AssistantArtifactTaskDispatchResult dispatchSingleArtifactWithDependencies(
            UUID workspaceId,
            String projectCode,
            UUID selectedNodeId,
            AssistantArtifactTaskCommand command
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );
        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                selectedNodeId
        );

        List<RequirementArtifactPlan> allPlans = nodeGenerationPolicy.allDefinitionsFor(module);
        RequirementArtifactPlan targetPlan = allPlans.stream()
                .filter(plan -> command.taskType().equals(plan.taskType()))
                .findFirst()
                .orElse(null);

        if (targetPlan == null) {
            String nodeType = nodeGenerationPolicy.resolveNodeType(module).name();
            return new AssistantArtifactTaskDispatchResult(
                    "当前选中的节点类型是 " + nodeType + "，不支持生成“" + command.title() + "”。请切换到合适节点后再试。比如交互原型只能基于 PAGE 页面节点生成。",
                    List.of()
            );
        }

        String targetTitle = targetPlan.title();

        RequirementArtifactImportance targetImportance =
                nodeGenerationPolicy.resolveArtifactImportance(
                        module,
                        command.taskType()
                );

        List<RequirementArtifactPlan> dependencyPlans =
                RequirementArtifactImportance.CORE.equals(targetImportance)
                        ? nodeGenerationPolicy.fullChainDefinitionsFor(module)
                        : allPlans;

        List<RequirementArtifactPlan> upstreamPlans = dependencyPlans.stream()
                .filter(plan -> plan.order() < targetPlan.order())
                .toList();

        List<AsyncAgentTaskResponse> createdOrReusedUpstreamTasks = new ArrayList<>();
        List<String> createdUpstreamTitles = new ArrayList<>();
        List<String> reusedUpstreamTitles = new ArrayList<>();
        List<Map<String, Object>> existingUpstreamArtifacts = new ArrayList<>();
        List<String> existingUpstreamTitles = new ArrayList<>();
        List<Map<String, Object>> skippedUpstreamTasks = new ArrayList<>();
        List<String> skippedUpstreamTitles = new ArrayList<>();

        for (RequirementArtifactPlan upstreamPlan : upstreamPlans) {
            if (!nodeGenerationPolicy.canGenerate(module, upstreamPlan.taskType())) {
                skippedUpstreamTasks.add(buildSkippedDependencyPayload(
                        upstreamPlan,
                        "当前节点类型不支持该上游资产。"
                ));
                skippedUpstreamTitles.add(upstreamPlan.title());
                continue;
            }

            boolean artifactExists = artifactVersionRepository.existsByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueAndStatus(
                    project.getId(),
                    module.getId(),
                    upstreamPlan.artifactType(),
                    ACTIVE
            );

            if (artifactExists) {
                existingUpstreamArtifacts.add(buildExistingArtifactPayload(upstreamPlan));
                existingUpstreamTitles.add(upstreamPlan.title());
                continue;
            }

            AsyncAgentTaskResponse upstreamTask = requirementArtifactAsyncTaskService.enqueueArtifactGeneration(
                    workspaceId,
                    projectCode,
                    selectedNodeId,
                    upstreamPlan.taskType(),
                    "由 AI Assistant 依赖感知调度自动补齐上游资产；用户原始指令：" + command.requirementText()
            );

            createdOrReusedUpstreamTasks.add(upstreamTask);

            if (upstreamTask.reused()) {
                reusedUpstreamTitles.add(upstreamPlan.title());
            } else {
                createdUpstreamTitles.add(upstreamPlan.title());
            }
        }

        AsyncAgentTaskResponse targetTask = requirementArtifactAsyncTaskService.enqueueArtifactGeneration(
                workspaceId,
                projectCode,
                selectedNodeId,
                command.taskType(),
                command.requirementText()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dispatchType", "SINGLE_ARTIFACT_TASK_WITH_DEPENDENCIES");
        payload.put("artifactType", targetPlan.artifactType());
        payload.put("artifactTitle", targetTitle);
        payload.put("taskType", targetPlan.taskType().name());
        payload.put("targetTask", targetTask);
        payload.put("upstreamTasks", createdOrReusedUpstreamTasks);
        payload.put("createdUpstreamTitles", createdUpstreamTitles);
        payload.put("reusedUpstreamTitles", reusedUpstreamTitles);
        payload.put("existingUpstreamArtifacts", existingUpstreamArtifacts);
        payload.put("existingUpstreamTitles", existingUpstreamTitles);
        payload.put("skippedUpstreamTasks", skippedUpstreamTasks);
        payload.put("skippedUpstreamTitles", skippedUpstreamTitles);
        payload.put("dependencyAware", true);

        String reply = buildSingleArtifactReply(
                targetTitle,
                targetTask,
                createdUpstreamTitles,
                reusedUpstreamTitles,
                existingUpstreamTitles,
                skippedUpstreamTitles
        );

        return new AssistantArtifactTaskDispatchResult(
                reply,
                List.of(
                        AssistantActionResponse.of(
                                "VIEW_TASK",
                                "查看任务",
                                payload
                        ),
                        AssistantActionResponse.of(
                                "VIEW_PROJECT_TASK_CENTER",
                                "查看项目任务中心",
                                payload
                        )
                )
        );
    }

    private String buildSingleArtifactReply(
            String targetTitle,
            AsyncAgentTaskResponse targetTask,
            List<String> createdUpstreamTitles,
            List<String> reusedUpstreamTitles,
            List<String> existingUpstreamTitles,
            List<String> skippedUpstreamTitles
    ) {
        StringBuilder reply = new StringBuilder();

        reply.append(targetTask.reused()
                ? "当前节点已有正在执行的“" + targetTitle + "”任务，我已复用该任务。"
                : "已为当前节点创建“" + targetTitle + "”生成任务。");

        int upstreamHandledCount =
                createdUpstreamTitles.size()
                        + reusedUpstreamTitles.size()
                        + existingUpstreamTitles.size();

        if (upstreamHandledCount > 0 || !skippedUpstreamTitles.isEmpty()) {
            reply.append("\n\n这个资产存在上游依赖。");

            if (!createdUpstreamTitles.isEmpty()) {
                reply.append("\n- 已自动新建上游任务：")
                        .append(String.join("、", createdUpstreamTitles))
                        .append("。");
            }

            if (!reusedUpstreamTitles.isEmpty()) {
                reply.append("\n- 已复用上游任务：")
                        .append(String.join("、", reusedUpstreamTitles))
                        .append("。");
            }

            if (!existingUpstreamTitles.isEmpty()) {
                reply.append("\n- 已存在上游资产：")
                        .append(String.join("、", existingUpstreamTitles))
                        .append("。");
            }

            if (!skippedUpstreamTitles.isEmpty()) {
                reply.append("\n- 已跳过不适用于当前节点的上游项：")
                        .append(String.join("、", skippedUpstreamTitles))
                        .append("。");
            }

            reply.append("\n目标任务如果显示“等待依赖”，系统会在上游资产就绪后自动继续。");
        }

        return reply.toString();
    }

    private AssistantArtifactTaskDispatchResult dispatchFullChain(
            UUID workspaceId,
            String projectCode,
            UUID selectedNodeId,
            AssistantArtifactTaskCommand command
    ) {
        GenerateFullChainRequest request = new GenerateFullChainRequest();
        request.setRequirementText(command.requirementText());
        request.setOverwriteExisting(command.overwriteExisting());

        FullChainAsyncTaskResponse response =
                requirementFullChainAsyncTaskService.enqueueFullChainGeneration(
                        workspaceId,
                        projectCode,
                        selectedNodeId,
                        request
                );

        int createdCount = response.createdTasks() == null ? 0 : response.createdTasks().size();
        int reusedCount = response.reusedTasks() == null ? 0 : response.reusedTasks().size();
        int skippedCount = response.skippedArtifacts() == null ? 0 : response.skippedArtifacts().size();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dispatchType", "CORE_ASSET_CHAIN_TASKS");
        payload.put("overwriteExisting", command.overwriteExisting());
        payload.put("createdTaskCount", createdCount);
        payload.put("reusedTaskCount", reusedCount);
        payload.put("skippedArtifactCount", skippedCount);
        payload.put("fullChain", response);

        String reply =
                "已为当前节点提交核心资产生成任务。" +
                        "新建任务 " + createdCount + " 个，复用任务 " + reusedCount + " 个，跳过资产 " + skippedCount + " 个。";

        return new AssistantArtifactTaskDispatchResult(
                reply,
                List.of(
                        AssistantActionResponse.of(
                                "VIEW_PROJECT_TASK_CENTER",
                                "查看项目任务中心",
                                payload
                        )
                )
        );
    }

    private Map<String, Object> buildExistingArtifactPayload(
            RequirementArtifactPlan plan
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskType", plan.taskType().name());
        payload.put("artifactType", plan.artifactType());
        payload.put("title", plan.title());
        payload.put("reason", "已有当前有效资产");
        return payload;
    }

    private Map<String, Object> buildSkippedDependencyPayload(
            RequirementArtifactPlan plan,
            String reason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskType", plan.taskType().name());
        payload.put("artifactType", plan.artifactType());
        payload.put("title", plan.title());
        payload.put("reason", reason);
        return payload;
    }
}
