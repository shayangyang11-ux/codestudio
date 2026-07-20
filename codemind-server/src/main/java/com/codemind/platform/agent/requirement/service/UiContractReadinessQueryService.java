package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UiContractReadinessQueryService {

    private final ProjectAccessService projectAccessService;
    private final UiContractGenerationReadinessService readinessService;

    public UiContractReadinessQueryService(
            ProjectAccessService projectAccessService,
            UiContractGenerationReadinessService readinessService
    ) {
        this.projectAccessService = projectAccessService;
        this.readinessService = readinessService;
    }

    @Transactional(readOnly = true)
    public UiContractGenerationReadinessResult getReadiness(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        ProjectEntity project =
                projectAccessService.resolveAccessibleProjectEntity(
                        workspaceId,
                        projectCode
                );

        ProjectModuleEntity module =
                projectAccessService.resolveAccessibleModule(
                        project.getId(),
                        moduleId
                );

        return readinessService.evaluate(
                project.getId(),
                module
        );
    }
}
