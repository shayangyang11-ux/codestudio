package com.codemind.platform.artifact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArtifactQualityCheckRecordRepository
        extends JpaRepository<ArtifactQualityCheckRecordEntity, UUID> {

    Optional<ArtifactQualityCheckRecordEntity>
    findFirstByProjectIdAndModuleIdOrderByCheckedAtDesc(
            UUID projectId,
            UUID moduleId
    );
}