package com.codemind.platform.module;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProjectModuleRepository extends JpaRepository<ProjectModuleEntity, UUID> {

    List<ProjectModuleEntity> findByProjectIdOrderByLevelNoAscSortOrderAsc(UUID projectId);

    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM project_module
                    WHERE project_id = :projectId
                    """,
            nativeQuery = true
    )
    int deleteByProjectId(UUID projectId);

    void flush();
}