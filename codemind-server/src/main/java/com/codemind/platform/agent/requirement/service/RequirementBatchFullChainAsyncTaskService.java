package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.requirement.dto.BatchFullChainAsyncTaskResponse;
import com.codemind.platform.agent.requirement.dto.FullChainAsyncTaskResponse;
import com.codemind.platform.agent.requirement.dto.GenerateBatchFullChainRequest;
import com.codemind.platform.agent.requirement.dto.GenerateFullChainRequest;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RequirementBatchFullChainAsyncTaskService {

    private static final int MAX_BATCH_MODULE_COUNT = 50;

    private final ProjectAccessService projectAccessService;
    private final RequirementFullChainAsyncTaskService requirementFullChainAsyncTaskService;

    public RequirementBatchFullChainAsyncTaskService(
            ProjectAccessService projectAccessService,
            RequirementFullChainAsyncTaskService requirementFullChainAsyncTaskService
    ) {
        this.projectAccessService = projectAccessService;
        this.requirementFullChainAsyncTaskService = requirementFullChainAsyncTaskService;
    }

    public BatchFullChainAsyncTaskResponse enqueueBatchFullChainGeneration(
            UUID workspaceId,
            String projectCode,
            GenerateBatchFullChainRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Batch full-chain request body is required.");
        }

        List<UUID> moduleIds = request.moduleIdsResolved();

        if (moduleIds.isEmpty()) {
            throw new IllegalArgumentException("moduleIds must not be empty.");
        }

        if (moduleIds.size() > MAX_BATCH_MODULE_COUNT) {
            throw new IllegalArgumentException(
                    "Too many modules in one batch. max="
                            + MAX_BATCH_MODULE_COUNT
                            + ", actual="
                            + moduleIds.size()
            );
        }

        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        boolean overwriteExisting = request.overwriteExistingResolved();

        List<FullChainAsyncTaskResponse> results = new ArrayList<>();
        List<BatchFullChainAsyncTaskResponse.BatchFullChainFailedModuleResponse> failedModules =
                new ArrayList<>();

        for (UUID moduleId : moduleIds) {
            try {
                GenerateFullChainRequest singleRequest = new GenerateFullChainRequest();
                singleRequest.setRequirementText(buildRequirementText(request.getRequirementText()));
                singleRequest.setOverwriteExisting(overwriteExisting);

                FullChainAsyncTaskResponse response =
                        requirementFullChainAsyncTaskService.enqueueFullChainGeneration(
                                workspaceId,
                                projectCode,
                                moduleId,
                                singleRequest
                        );

                results.add(response);
            } catch (Exception e) {
                failedModules.add(
                        new BatchFullChainAsyncTaskResponse.BatchFullChainFailedModuleResponse(
                                moduleId,
                                e.getClass().getSimpleName(),
                                e.getMessage()
                        )
                );
            }
        }

        int totalCreatedTaskCount = results.stream()
                .mapToInt(response -> response.createdTasks().size())
                .sum();

        int totalReusedTaskCount = results.stream()
                .mapToInt(response -> response.reusedTasks().size())
                .sum();

        int totalSkippedArtifactCount = results.stream()
                .mapToInt(response -> response.skippedArtifacts().size())
                .sum();

        return new BatchFullChainAsyncTaskResponse(
                project.getId(),
                projectCode,
                moduleIds.size(),
                results.size(),
                failedModules.size(),
                totalCreatedTaskCount,
                totalReusedTaskCount,
                totalSkippedArtifactCount,
                overwriteExisting,
                results,
                failedModules,
                LocalDateTime.now()
        );
    }

    private String buildRequirementText(String requirementText) {
        if (requirementText == null || requirementText.isBlank()) {
            return "请为选中模块批量生成完整需求工程资产链路。";
        }

        return requirementText.trim();
    }
}