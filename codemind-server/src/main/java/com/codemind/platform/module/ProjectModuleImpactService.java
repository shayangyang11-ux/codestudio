package com.codemind.platform.module;

import com.codemind.platform.artifact.ArtifactVersionRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProjectModuleImpactService {

    private final ProjectModuleRepository projectModuleRepository;
    private final ArtifactVersionRepository artifactVersionRepository;

    public ProjectModuleImpactService(
            ProjectModuleRepository projectModuleRepository,
            ArtifactVersionRepository artifactVersionRepository
    ) {
        this.projectModuleRepository = projectModuleRepository;
        this.artifactVersionRepository = artifactVersionRepository;
    }

    public void markImpactedArtifactsAfterCreate(
            UUID projectId,
            ProjectModuleEntity createdNode
    ) {
        Set<UUID> impactedModuleIds = new LinkedHashSet<>();

        addIfNotNull(impactedModuleIds, createdNode.getId());
        addIfNotNull(impactedModuleIds, createdNode.getParentId());

        artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                projectId,
                impactedModuleIds,
                List.of(
                        "MODULE_PRD",
                        "BUSINESS_FLOW",
                        "PAGE_SPEC",
                        "DATA_MODEL",
                        "API_CONTRACT",
                        "TEST_SPEC",
                        "UI_CONTRACT",
                        "DEV_HANDOFF_PACKAGE"
                )
        );
    }

    public void markImpactedArtifactsAfterUpdate(
            UUID projectId,
            ProjectModuleEntity updatedNode
    ) {
        Set<UUID> impactedModuleIds = new LinkedHashSet<>();

        addIfNotNull(impactedModuleIds, updatedNode.getId());
        addIfNotNull(impactedModuleIds, updatedNode.getParentId());

        if ("PAGE".equalsIgnoreCase(updatedNode.getModuleType())) {
            addChildren(impactedModuleIds, projectId, updatedNode.getId());
        }

        artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                projectId,
                impactedModuleIds,
                List.of(
                        "MODULE_PRD",
                        "BUSINESS_FLOW",
                        "PAGE_SPEC",
                        "DATA_MODEL",
                        "API_CONTRACT",
                        "TEST_SPEC",
                        "UI_CONTRACT",
                        "DEV_HANDOFF_PACKAGE"
                )
        );
    }

    public void markImpactedArtifactsAfterMove(
            UUID projectId,
            ProjectModuleEntity movedNode,
            UUID oldParentId,
            UUID newParentId
    ) {
        Set<UUID> impactedModuleIds = new LinkedHashSet<>();

        addIfNotNull(impactedModuleIds, movedNode.getId());
        addIfNotNull(impactedModuleIds, oldParentId);
        addIfNotNull(impactedModuleIds, newParentId);

        addDescendants(impactedModuleIds, projectId, movedNode.getId());

        artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                projectId,
                impactedModuleIds,
                List.of(
                        "MODULE_PRD",
                        "BUSINESS_FLOW",
                        "PAGE_SPEC",
                        "DATA_MODEL",
                        "API_CONTRACT",
                        "TEST_SPEC",
                        "UI_CONTRACT",
                        "DEV_HANDOFF_PACKAGE"
                )
        );
    }

    public void markImpactedArtifactsAfterDelete(
            UUID projectId,
            ProjectModuleEntity deletedRoot
    ) {
        Set<UUID> impactedModuleIds = new LinkedHashSet<>();

        addIfNotNull(impactedModuleIds, deletedRoot.getId());
        addIfNotNull(impactedModuleIds, deletedRoot.getParentId());
        addDescendantsIncludingDeleted(impactedModuleIds, projectId, deletedRoot.getId());

        artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                projectId,
                impactedModuleIds,
                List.of(
                        "MODULE_PRD",
                        "BUSINESS_FLOW",
                        "PAGE_SPEC",
                        "DATA_MODEL",
                        "API_CONTRACT",
                        "TEST_SPEC",
                        "UI_CONTRACT",
                        "DEV_HANDOFF_PACKAGE"
                )
        );
    }

    private void addChildren(
            Set<UUID> result,
            UUID projectId,
            UUID parentId
    ) {
        projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                .stream()
                .filter(module -> "ACTIVE".equals(module.getStatus()))
                .filter(module -> parentId.equals(module.getParentId()))
                .forEach(module -> addIfNotNull(result, module.getId()));
    }

    private void addDescendants(
            Set<UUID> result,
            UUID projectId,
            UUID rootId
    ) {
        Map<UUID, List<ProjectModuleEntity>> childrenByParent = childrenByParent(
                projectId,
                true
        );

        addDescendantsRecursive(
                result,
                childrenByParent,
                rootId
        );
    }

    private void addDescendantsIncludingDeleted(
            Set<UUID> result,
            UUID projectId,
            UUID rootId
    ) {
        Map<UUID, List<ProjectModuleEntity>> childrenByParent = childrenByParent(
                projectId,
                false
        );

        addDescendantsRecursive(
                result,
                childrenByParent,
                rootId
        );
    }

    private void addDescendantsRecursive(
            Set<UUID> result,
            Map<UUID, List<ProjectModuleEntity>> childrenByParent,
            UUID parentId
    ) {
        List<ProjectModuleEntity> children = childrenByParent.getOrDefault(
                parentId,
                List.of()
        );

        for (ProjectModuleEntity child : children) {
            addIfNotNull(result, child.getId());

            addDescendantsRecursive(
                    result,
                    childrenByParent,
                    child.getId()
            );
        }
    }

    private Map<UUID, List<ProjectModuleEntity>> childrenByParent(
            UUID projectId,
            boolean activeOnly
    ) {
        Map<UUID, List<ProjectModuleEntity>> result = new LinkedHashMap<>();

        projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
                .stream()
                .filter(module -> !activeOnly || "ACTIVE".equals(module.getStatus()))
                .filter(module -> module.getParentId() != null)
                .forEach(module -> result
                        .computeIfAbsent(module.getParentId(), ignored -> new ArrayList<>())
                        .add(module));

        return result;
    }

    private void addIfNotNull(
            Set<UUID> result,
            UUID value
    ) {
        if (value != null) {
            result.add(value);
        }
    }
}