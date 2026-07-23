package com.codemind.platform.artifact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersionEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                UPDATE artifact_version
                SET status = 'NEEDS_REVIEW',
                    updated_at = CURRENT_TIMESTAMP
                WHERE project_id = :projectId
                  AND module_id IN (:moduleIds)
                  AND is_current = true
                  AND status = 'ACTIVE'
                  AND artifact_type IN (:artifactTypes)
                """,
            nativeQuery = true
    )
    int markCurrentModuleArtifactsNeedsReview(
            @Param("projectId") UUID projectId,
            @Param("moduleIds") Collection<UUID> moduleIds,
            @Param("artifactTypes") Collection<String> artifactTypes
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
                UPDATE artifact_version
                SET status = 'NEEDS_REVIEW',
                    updated_at = CURRENT_TIMESTAMP
                WHERE project_id = :projectId
                  AND module_id = :moduleId
                  AND is_current = true
                  AND status = 'ACTIVE'
                """,
            nativeQuery = true
    )
    int markAllCurrentModuleArtifactsNeedsReview(
            @Param("projectId") UUID projectId,
            @Param("moduleId") UUID moduleId
    );

    @Query(value = """
        SELECT COALESCE(MAX(version_no), 0)
        FROM artifact_version
        WHERE project_id = :projectId
          AND module_id IS NULL
          AND artifact_type = :artifactType
        """, nativeQuery = true)
    Integer findMaxProjectArtifactVersionNo(
            UUID projectId,
            String artifactType
    );

    @Query(value = """
        SELECT COALESCE(MAX(version_no), 0)
        FROM artifact_version
        WHERE project_id = :projectId
          AND module_id = :moduleId
          AND artifact_type = :artifactType
        """, nativeQuery = true)
    Integer findMaxModuleArtifactVersionNo(
            UUID projectId,
            UUID moduleId,
            String artifactType
    );

    @Modifying
    @Query(value = """
        UPDATE artifact_version
        SET is_current = false,
            status = 'SUPERSEDED',
            updated_at = CURRENT_TIMESTAMP
        WHERE project_id = :projectId
          AND module_id IS NULL
          AND artifact_type = :artifactType
          AND is_current = true
        """, nativeQuery = true)
    int markPreviousProjectArtifactsAsNotCurrent(
            UUID projectId,
            String artifactType
    );

    @Modifying
    @Query(value = """
        UPDATE artifact_version
        SET is_current = false,
            status = 'SUPERSEDED',
            updated_at = CURRENT_TIMESTAMP
        WHERE project_id = :projectId
          AND module_id = :moduleId
          AND artifact_type = :artifactType
          AND is_current = true
        """, nativeQuery = true)
    int markPreviousModuleArtifactsAsNotCurrent(
            UUID projectId,
            UUID moduleId,
            String artifactType
    );

    Optional<ArtifactVersionEntity>
    findFirstByProjectIdAndModuleIdIsNullAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
            UUID projectId,
            String artifactType
    );

    Optional<ArtifactVersionEntity>
    findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
            UUID projectId,
            UUID moduleId,
            String artifactType
    );

    @Query(value = """
        SELECT *
        FROM artifact_version
        WHERE project_id = :projectId
          AND artifact_type = :artifactType
          AND is_current = true
          AND module_id IS NOT NULL
        ORDER BY updated_at DESC, created_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<ArtifactVersionEntity> findLatestCurrentModuleArtifact(
            UUID projectId,
            String artifactType
    );

    boolean existsByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueAndStatus(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            String status
    );

    boolean existsByProjectIdAndModuleIdIsNullAndArtifactTypeAndCurrentTrueAndStatus(
            UUID projectId,
            String artifactType,
            String status
    );

    List<ArtifactVersionEntity> findByProjectIdAndModuleIdAndArtifactTypeOrderByVersionNoDesc(
            UUID projectId,
            UUID moduleId,
            String artifactType
    );

    Optional<ArtifactVersionEntity> findByIdAndProjectIdAndModuleIdAndArtifactType(
            UUID id,
            UUID projectId,
            UUID moduleId,
            String artifactType
    );

    List<ArtifactVersionEntity> findByProjectIdAndModuleIdAndCurrentTrue(
            UUID projectId,
            UUID moduleId
    );
}
