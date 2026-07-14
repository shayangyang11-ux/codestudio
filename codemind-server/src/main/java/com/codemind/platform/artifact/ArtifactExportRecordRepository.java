package com.codemind.platform.artifact;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArtifactExportRecordRepository
        extends JpaRepository<ArtifactExportRecordEntity, UUID> {

    Optional<ArtifactExportRecordEntity>
    findFirstByProjectIdAndModuleIdOrderByExportedAtDesc(
            UUID projectId,
            UUID moduleId
    );

    Optional<ArtifactExportRecordEntity>
    findFirstByProjectIdAndExportScopeOrderByExportedAtDesc(
            UUID projectId,
            String exportScope
    );
}