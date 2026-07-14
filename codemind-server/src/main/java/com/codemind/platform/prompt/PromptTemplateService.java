package com.codemind.platform.prompt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class PromptTemplateService {

    private static final Set<String> GOVERNED_REQUIREMENT_TASK_TYPES = Set.of(
            "GENERATE_MODULE_TREE",
            "GENERATE_MODULE_PRD",
            "GENERATE_BUSINESS_FLOW",
            "GENERATE_PAGE_SPEC",
            "GENERATE_DATA_MODEL",
            "GENERATE_API_CONTRACT",
            "GENERATE_TEST_SPEC",
            "GENERATE_UI_CONTRACT",
            "GENERATE_DEV_HANDOFF_PACKAGE"
    );

    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptTemplateVersionRepository promptTemplateVersionRepository;

    public PromptTemplateService(
            PromptTemplateRepository promptTemplateRepository,
            PromptTemplateVersionRepository promptTemplateVersionRepository
    ) {
        this.promptTemplateRepository = promptTemplateRepository;
        this.promptTemplateVersionRepository = promptTemplateVersionRepository;
    }

    @Transactional(readOnly = true)
    public PromptTemplateSnapshot resolveCurrentSnapshotForTaskType(
            String taskType
    ) {
        if (!GOVERNED_REQUIREMENT_TASK_TYPES.contains(taskType)) {
            throw new IllegalStateException(
                    "当前任务类型尚未纳入 Prompt 模板治理，禁止创建不可追溯任务：" + taskType
            );
        }

        PromptTemplateVersionEntity version =
                promptTemplateVersionRepository.findCurrentByTaskType(taskType)
                        .orElseThrow(() -> new IllegalStateException(
                                "未找到当前可用 Prompt 模板版本，任务类型：" + taskType
                        ));

        PromptTemplateEntity template =
                promptTemplateRepository.findById(version.getTemplateId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Prompt 模板版本缺少模板定义，templateId=" + version.getTemplateId()
                        ));

        return new PromptTemplateSnapshot(
                template.getId(),
                version.getId(),
                template.getTemplateKey(),
                template.getTemplateName(),
                template.getTaskType(),
                template.getArtifactType(),
                version.getVersionLabel(),
                version.getChecksum(),
                version.getSystemPrompt(),
                version.getUserPromptTemplate(),
                version.getOutputSchema()
        );
    }
}