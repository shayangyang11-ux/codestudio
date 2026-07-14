package com.codemind.platform.prompt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateVersionRepository extends JpaRepository<PromptTemplateVersionEntity, UUID> {

    @Query(value = """
        SELECT v.*
        FROM prompt_template_version v
        JOIN prompt_template t ON t.id = v.template_id
        WHERE t.task_type = :taskType
          AND t.status = 'ACTIVE'
          AND v.status = 'ACTIVE'
          AND v.is_current = true
        LIMIT 1
        """, nativeQuery = true)
    Optional<PromptTemplateVersionEntity> findCurrentByTaskType(
            String taskType
    );
}