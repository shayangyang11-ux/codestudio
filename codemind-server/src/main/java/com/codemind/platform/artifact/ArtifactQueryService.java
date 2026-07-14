package com.codemind.platform.artifact;

import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ArtifactQueryService {

    private final ArtifactVersionRepository artifactVersionRepository;
    private final ProjectAccessService projectAccessService;

    public ArtifactQueryService(
            ArtifactVersionRepository artifactVersionRepository,
            ProjectAccessService projectAccessService
    ) {
        this.artifactVersionRepository = artifactVersionRepository;
        this.projectAccessService = projectAccessService;
    }

    public Map<String, Object> getCurrentModuleArtifact(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String artifactType
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        Optional<ArtifactVersionEntity> artifactOptional =
                artifactVersionRepository
                        .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                                project.getId(),
                                module.getId(),
                                artifactType
                        );

        if (artifactOptional.isEmpty()) {
            return buildEmptyCurrentArtifactResult(
                    projectCode,
                    module,
                    artifactType
            );
        }

        return buildArtifactResult(
                artifactOptional.get(),
                module
        );
    }

    public Map<String, Object> getCurrentModuleArtifactByProjectCode(
            String projectCode,
            UUID moduleId,
            String artifactType
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntityByCode(projectCode);

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        Optional<ArtifactVersionEntity> artifactOptional =
                artifactVersionRepository
                        .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                                project.getId(),
                                module.getId(),
                                artifactType
                        );

        if (artifactOptional.isEmpty()) {
            return buildEmptyCurrentArtifactResult(
                    projectCode,
                    module,
                    artifactType
            );
        }

        return buildArtifactResult(
                artifactOptional.get(),
                module
        );
    }

    public Map<String, Object> getLatestProjectModuleArtifact(
            UUID workspaceId,
            String projectCode,
            String artifactType
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        Optional<ArtifactVersionEntity> artifactOptional =
                artifactVersionRepository.findLatestCurrentModuleArtifact(
                        project.getId(),
                        artifactType
                );

        if (artifactOptional.isEmpty()) {
            Map<String, Object> emptyResult = new LinkedHashMap<>();
            emptyResult.put("exists", false);
            emptyResult.put("projectCode", projectCode);
            emptyResult.put("artifactType", artifactType);
            return emptyResult;
        }

        ArtifactVersionEntity artifact = artifactOptional.get();

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                artifact.getModuleId()
        );

        return buildArtifactResult(
                artifact,
                module
        );
    }

    public Map<String, Object> getLatestProjectModuleArtifactByProjectCode(
            String projectCode,
            String artifactType
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntityByCode(projectCode);

        Optional<ArtifactVersionEntity> artifactOptional =
                artifactVersionRepository.findLatestCurrentModuleArtifact(
                        project.getId(),
                        artifactType
                );

        if (artifactOptional.isEmpty()) {
            Map<String, Object> emptyResult = new LinkedHashMap<>();
            emptyResult.put("exists", false);
            emptyResult.put("projectCode", projectCode);
            emptyResult.put("artifactType", artifactType);
            return emptyResult;
        }

        ArtifactVersionEntity artifact = artifactOptional.get();

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                artifact.getModuleId()
        );

        return buildArtifactResult(
                artifact,
                module
        );
    }

    private Map<String, Object> buildEmptyCurrentArtifactResult(
            String projectCode,
            ProjectModuleEntity module,
            String artifactType
    ) {
        Map<String, Object> emptyResult = new LinkedHashMap<>();
        emptyResult.put("exists", false);
        emptyResult.put("projectCode", projectCode);
        emptyResult.put("moduleId", module.getId());
        emptyResult.put("moduleKey", module.getModuleKey());
        emptyResult.put("moduleName", module.getModuleName());
        emptyResult.put("artifactType", artifactType);
        return emptyResult;
    }

    private Map<String, Object> buildArtifactResult(
            ArtifactVersionEntity artifact,
            ProjectModuleEntity module
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("artifactId", artifact.getId());
        result.put("projectId", artifact.getProjectId());
        result.put("moduleId", artifact.getModuleId());
        result.put("moduleKey", module.getModuleKey());
        result.put("moduleName", module.getModuleName());
        result.put("artifactType", artifact.getArtifactType());
        result.put("versionNo", artifact.getVersionNo());
        result.put("title", artifact.getTitle());
        result.put("status", artifact.getStatus());
        result.put("current", artifact.getCurrent());
        result.put("content", artifact.getContent());
        result.put("contentMarkdown", artifact.getContentMarkdown());
        result.put("createdAt", artifact.getCreatedAt());
        result.put("updatedAt", artifact.getUpdatedAt());
        return result;
    }
}