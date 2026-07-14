package com.codemind.platform.prompt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, UUID> {

    Optional<PromptTemplateEntity> findByTaskTypeAndStatus(
            String taskType,
            String status
    );

    Optional<PromptTemplateEntity> findByTemplateKeyAndStatus(
            String templateKey,
            String status
    );
}