package com.codemind.platform.assistant;

import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ModuleTreePatchProposalResponse;
import com.codemind.platform.module.ModuleTreePatchProposalService;
import com.codemind.platform.module.ProjectModuleService;
import com.codemind.platform.module.ProposeModuleTreePatchRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssistantMessageService {

    private static final String INTENT_TASK_NOTIFICATION = "TASK_NOTIFICATION";
    private static final String INTENT_CONVERSATION_ARCHIVE = "CONVERSATION_ARCHIVE";
    private static final String MESSAGE_TYPE_ARCHIVE_MARKER = "CONVERSATION_ARCHIVE_MARKER";
    private static final String INTENT_PROPOSE_REQUIREMENT_CHANGE = "PROPOSE_REQUIREMENT_CHANGE";
    private static final String MESSAGE_TYPE_REQUIREMENT_CHANGE_PROPOSAL = "REQUIREMENT_CHANGE_PROPOSAL";
    private static final String MESSAGE_TYPE_ATTACHMENT_DRIVEN_CHANGE_PROPOSAL = "ATTACHMENT_CHANGE_PROPOSAL";
    private static final String MESSAGE_TYPE_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL = "ATTACHMENT_ARTIFACT_DIFF_PROPOSAL";
    private static final String ACTION_REQUIREMENT_CHANGE_PROPOSAL = "REQUIREMENT_CHANGE_PROPOSAL";
    private static final String ACTION_ATTACHMENT_DRIVEN_CHANGE_PROPOSAL = "ATTACHMENT_DRIVEN_CHANGE_PROPOSAL";
    private static final String ACTION_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL = "ATTACHMENT_ARTIFACT_DIFF_PROPOSAL";

    private final ProjectModuleService projectModuleService;
    private final ModuleTreePatchProposalService moduleTreePatchProposalService;
    private final AssistantAttachmentService assistantAttachmentService;
    private final AssistantAttachmentParserService assistantAttachmentParserService;
    private final AssistantIntentRouterService assistantIntentRouterService;
    private final AssistantArtifactTaskCommandResolver assistantArtifactTaskCommandResolver;
    private final AssistantArtifactTaskDispatchService assistantArtifactTaskDispatchService;
    private final AssistantArtifactReviewService assistantArtifactReviewService;
    private final AssistantAttachmentArtifactDiffService assistantAttachmentArtifactDiffService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public AssistantMessageService(
            ProjectModuleService projectModuleService,
            ModuleTreePatchProposalService moduleTreePatchProposalService,
            AssistantAttachmentService assistantAttachmentService,
            AssistantAttachmentParserService assistantAttachmentParserService,
            AssistantIntentRouterService assistantIntentRouterService,
            AssistantArtifactTaskCommandResolver assistantArtifactTaskCommandResolver,
            AssistantArtifactTaskDispatchService assistantArtifactTaskDispatchService,
            AssistantArtifactReviewService assistantArtifactReviewService,
            AssistantAttachmentArtifactDiffService assistantAttachmentArtifactDiffService,
            NamedParameterJdbcTemplate jdbcTemplate,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.projectModuleService = projectModuleService;
        this.moduleTreePatchProposalService = moduleTreePatchProposalService;
        this.assistantAttachmentService = assistantAttachmentService;
        this.assistantAttachmentParserService = assistantAttachmentParserService;
        this.assistantIntentRouterService = assistantIntentRouterService;
        this.assistantArtifactTaskCommandResolver = assistantArtifactTaskCommandResolver;
        this.assistantArtifactTaskDispatchService = assistantArtifactTaskDispatchService;
        this.assistantArtifactReviewService = assistantArtifactReviewService;
        this.assistantAttachmentArtifactDiffService = assistantAttachmentArtifactDiffService;
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AssistantConversationMessageResponse> listMessages(
            UUID workspaceId,
            String projectCode,
            int limit
    ) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));

        String sql = """
                WITH latest_archive AS (
                    SELECT MAX(created_at) AS archived_at
                    FROM assistant_message
                    WHERE workspace_id = :workspaceId
                      AND project_code = :projectCode
                      AND intent = :archiveIntent
                      AND content ->> 'messageType' = :archiveMessageType
                )
                SELECT
                    m.id,
                    m.role,
                    m.intent,
                    m.selected_node_id,
                    m.selected_artifact_type,
                    m.content::text AS content_json,
                    m.created_at
                FROM assistant_message m
                CROSS JOIN latest_archive a
                WHERE m.workspace_id = :workspaceId
                  AND m.project_code = :projectCode
                  AND (a.archived_at IS NULL OR m.created_at > a.archived_at)
                  AND NOT (
                      m.intent = :archiveIntent
                      AND m.content ->> 'messageType' = :archiveMessageType
                  )
                ORDER BY
                    m.created_at DESC,
                    CASE
                        WHEN m.role = 'ASSISTANT' THEN 0
                        WHEN m.role = 'USER' THEN 1
                        WHEN m.role = 'SYSTEM' THEN 2
                        ELSE 3
                    END ASC
                LIMIT :limit
                """;

        List<AssistantConversationMessageResponse> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectCode", projectCode)
                        .addValue("archiveIntent", INTENT_CONVERSATION_ARCHIVE)
                        .addValue("archiveMessageType", MESSAGE_TYPE_ARCHIVE_MARKER)
                        .addValue("limit", resolvedLimit),
                (rs, rowNum) -> {
                    Map<String, Object> content = parseContentJson(
                            rs.getString("content_json")
                    );

                    Timestamp createdAtTimestamp = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = createdAtTimestamp == null
                            ? null
                            : createdAtTimestamp.toLocalDateTime();

                    return toConversationMessage(
                            rs.getObject("id", UUID.class),
                            rs.getString("role"),
                            rs.getString("intent"),
                            rs.getObject("selected_node_id", UUID.class),
                            rs.getString("selected_artifact_type"),
                            content,
                            createdAt
                    );
                }
        );

        Collections.reverse(rows);

        return rows;
    }

    @Transactional
    public AssistantConversationArchiveResponse archiveMessages(
            UUID workspaceId,
            String projectCode
    ) {
        int archivedMessageCount = countVisibleMessages(
                workspaceId,
                projectCode
        );

        LocalDateTime archivedAt = LocalDateTime.now();

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("messageType", MESSAGE_TYPE_ARCHIVE_MARKER);
        content.put("message", "当前对话视图已清空。历史记录已作为项目过程审计保留，默认不再展示。");
        content.put("archivedAt", archivedAt.toString());
        content.put("archivedMessageCount", archivedMessageCount);

        UUID archiveMessageId = persistMessage(
                workspaceId,
                projectCode,
                "SYSTEM",
                INTENT_CONVERSATION_ARCHIVE,
                null,
                null,
                content
        );

        return new AssistantConversationArchiveResponse(
                archiveMessageId,
                archivedMessageCount,
                archivedAt,
                "当前 AI Assistant 对话视图已清空。历史记录已归档保留。"
        );
    }

    @Transactional
    public AssistantMessageResponse handleMessage(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request
    ) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("AI Assistant 消息不能为空。");
        }

        String messageText = request.getMessage().trim();

        List<AssistantAttachmentParsedContext> attachmentContexts =
                assistantAttachmentParserService.parseAndBuildContext(
                        workspaceId,
                        projectCode,
                        request.getAttachments()
                );

        List<AssistantAttachmentRef> enrichedAttachments =
                mergeAttachmentRefsWithParsedContexts(
                        request.getAttachments(),
                        attachmentContexts
                );

        Map<String, Object> userMessageContent = new LinkedHashMap<>();
        userMessageContent.put("message", messageText);
        userMessageContent.put("messageType", "TEXT");
        userMessageContent.put("expectedIntent", nullToEmpty(request.getExpectedIntent()));
        userMessageContent.put("attachments", enrichedAttachments);
        userMessageContent.put("attachmentContexts", attachmentContexts);
        userMessageContent.put("selectedDiffItemIds", normalizeSelectedDiffItemIds(request.getSelectedDiffItemIds()));
        userMessageContent.put("selectedDiffItems", normalizeSelectedDiffItems(request.getSelectedDiffItems()));
        userMessageContent.put(
                "baseArtifactId",
                request.getBaseArtifactId() == null ? null : request.getBaseArtifactId().toString()
        );
        userMessageContent.put("baseVersionNo", request.getBaseVersionNo());
        userMessageContent.put("baseChecksum", nullToEmpty(request.getBaseChecksum()));
        userMessageContent.put("baseModuleTreeHash", nullToEmpty(request.getBaseModuleTreeHash()));
        userMessageContent.put("baseModuleTreeNodeCount", request.getBaseModuleTreeNodeCount());

        UUID userMessageId = persistMessage(
                workspaceId,
                projectCode,
                "USER",
                null,
                request.getSelectedNodeId(),
                request.getSelectedArtifactType(),
                userMessageContent
        );

        assistantAttachmentService.bindAttachmentsToMessage(
                workspaceId,
                projectCode,
                userMessageId,
                enrichedAttachments
        );

        List<ProjectModuleService.ModuleTreeNode> moduleTree =
                projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                        workspaceId,
                        projectCode
                );

        AssistantIntentClassificationResult classification =
                assistantIntentRouterService.classify(
                        workspaceId,
                        projectCode,
                        request,
                        messageText,
                        !moduleTree.isEmpty(),
                        moduleTree,
                        attachmentContexts
                );

        String intent = resolveDialogRequirementChangeIntent(
                classification.getIntent(),
                messageText,
                !moduleTree.isEmpty(),
                request,
                attachmentContexts
        );

        /*
         * Phase 6-B: natural language instructions should be routed by their
         * real business intent, not forcibly bound to the currently opened
         * asset. For example, while the user is viewing the Login requirement
         * document, "给用户中心增加密码找回页" is still a module-tree change:
         * add a PAGE under 用户中心 / 登录注册, not a local edit to the Login doc.
         */
        if (shouldForceModuleTreePatchIntent(
                messageText,
                classification
        )) {
            intent = AssistantIntentRouterService.INTENT_PROPOSE_MODULE_TREE_PATCH;
        }

        AssistantMessageResponse response = route(
                workspaceId,
                projectCode,
                request,
                resolveEffectiveMessage(messageText, classification),
                intent,
                classification,
                attachmentContexts
        );

        UUID assistantMessageId = persistMessage(
                workspaceId,
                projectCode,
                "ASSISTANT",
                response.getIntent(),
                request.getSelectedNodeId(),
                request.getSelectedArtifactType(),
                assistantResponseContent(
                        response,
                        classification
                )
        );

        response.setMessageId(assistantMessageId);

        return response;
    }

    @Transactional
    public AssistantMessageResponse createTaskNotification(
            UUID workspaceId,
            String projectCode,
            AssistantTaskNotificationRequest request
    ) {
        if (request == null || request.getTaskId() == null) {
            throw new IllegalArgumentException("任务通知缺少 taskId。");
        }

        String status = normalizeStatus(request.getStatus());

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("任务通知缺少任务状态。");
        }

        String notificationKey = buildTaskNotificationKey(
                request.getTaskId(),
                status
        );

        String title = firstNonBlank(
                request.getArtifactTitle(),
                firstNonBlank(request.getArtifactType(), firstNonBlank(request.getTaskType(), "需求资产"))
        );

        String reply = buildTaskNotificationReply(
                title,
                status,
                request.getErrorMessage()
        );

        AssistantMessageResponse response = simpleResponse(
                INTENT_TASK_NOTIFICATION,
                reply,
                false,
                List.of(
                        AssistantActionResponse.of(
                                "OPEN_ARTIFACT",
                                "查看资产",
                                taskNotificationActionPayload(request)
                        )
                )
        );

        UUID existingMessageId = findExistingNotificationMessageId(
                workspaceId,
                projectCode,
                notificationKey
        );

        if (existingMessageId != null) {
            response.setMessageId(existingMessageId);
            return response;
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reply", reply);
        content.put("intent", INTENT_TASK_NOTIFICATION);
        content.put("messageType", "TASK_NOTIFICATION");
        content.put("notificationKey", notificationKey);
        content.put("taskId", request.getTaskId());
        content.put("moduleId", request.getModuleId());
        content.put("taskType", request.getTaskType());
        content.put("artifactType", request.getArtifactType());
        content.put("artifactTitle", title);
        content.put("status", status);
        content.put("resultArtifactId", request.getResultArtifactId());
        content.put("errorCode", request.getErrorCode());
        content.put("errorMessage", request.getErrorMessage());
        content.put("actions", response.getActions());

        UUID messageId = persistMessage(
                workspaceId,
                projectCode,
                "ASSISTANT",
                INTENT_TASK_NOTIFICATION,
                request.getModuleId(),
                request.getArtifactType(),
                content
        );

        response.setMessageId(messageId);

        return response;
    }

    private AssistantMessageResponse route(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            String intent,
            AssistantIntentClassificationResult classification,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        // A9.10.14.4.3.15.2.4_EXTERNAL_PRD_IMPORT_AND_LEGACY_PRD_MIGRATION_ASSISTANT_MESSAGE_SERVICE
        if (AssistantIntentRouterService.INTENT_GENERATE_MODULE_TREE.equals(intent)) {
            return buildGenerateModuleTreeResponse(
                    buildInstructionWithAttachmentContext(
                            messageText,
                            attachmentContexts
                    ),
                    attachmentContexts
            );
        }

        if (AssistantIntentRouterService.INTENT_EXECUTE_UI_COMMAND.equals(intent)) {
            return buildAssistantUiCommandResponse(
                    request,
                    messageText,
                    classification
            );
        }

        if (INTENT_PROPOSE_REQUIREMENT_CHANGE.equals(intent)) {
            return buildRequirementChangeProposalResponse(
                    workspaceId,
                    projectCode,
                    request,
                    messageText,
                    attachmentContexts
            );
        }

        if (AssistantIntentRouterService.INTENT_PROPOSE_MODULE_TREE_PATCH.equals(intent)) {
            if (shouldRunAttachmentModuleTreeDiffFirst(
                    request,
                    messageText,
                    attachmentContexts
            )) {
                return buildAttachmentDrivenChangeProposalResponse(
                        workspaceId,
                        projectCode,
                        request,
                        messageText,
                        classification,
                        attachmentContexts
                );
            }

            return buildTreePatchProposalResponse(
                    workspaceId,
                    projectCode,
                    request,
                    messageText,
                    attachmentContexts
            );
        }

        if (AssistantIntentRouterService.INTENT_GENERATE_REQUIREMENT_ARTIFACT.equals(intent)) {
            return buildRequirementArtifactTaskResponse(
                    workspaceId,
                    projectCode,
                    request,
                    messageText,
                    classification,
                    attachmentContexts
            );
        }

        if (AssistantIntentRouterService.INTENT_EXPLAIN_CURRENT_ARTIFACT.equals(intent)) {
            return assistantArtifactReviewService.review(
                    workspaceId,
                    projectCode,
                    request,
                    messageText,
                    classification,
                    attachmentContexts
            );
        }
        if (AssistantIntentRouterService.INTENT_ANALYZE_ATTACHMENT.equals(intent)) {
            if (shouldBuildAttachmentArtifactDiffProposal(
                    request,
                    messageText,
                    attachmentContexts
            )) {
                return assistantAttachmentArtifactDiffService.buildDiffResponse(
                        workspaceId,
                        projectCode,
                        request,
                        messageText,
                        attachmentContexts
                );
            }

            return buildAttachmentDrivenChangeProposalResponse(
                    workspaceId,
                    projectCode,
                    request,
                    messageText,
                    classification,
                    attachmentContexts
            );
        }

        return simpleResponse(
                AssistantIntentRouterService.INTENT_GENERAL_QA,
                firstNonBlank(
                        classification.getSuggestedReply(),
                        "我已收到你的问题。当前阶段 Assistant 优先支持模块树生成、模块树修改提案和需求资产相关操作。你可以直接说“生成功能点需求 / 生成功能点接口 / 生成当前页面原型 / 生成当前节点核心资产”。"
                ),
                false,
                List.of()
        );
    }

    private AssistantMessageResponse buildGenerateModuleTreeResponse(
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String modelingMode = inferModuleTreeModelingModeForImport(
                messageText,
                attachmentContexts
        );
        boolean externalPrdImport = isExternalPrdImportIntent(
                messageText,
                attachmentContexts
        );

        payload.put("requirementText", messageText);
        payload.put("modelingMode", modelingMode);
        payload.put("source", externalPrdImport
                ? "EXTERNAL_PRD_IMPORT"
                : "ASSISTANT_GENERATE_MODULE_TREE");

        String reply = externalPrdImport
                ? "已识别为外部/旧 PRD 导入。系统会把上传材料中的业务域、模块、功能点和页面入口迁移为 CodeMind 模块树；本次默认使用完整企业蓝图模式，后续 MODULE_PRD 会继续由 Requirement Agent 补齐结构化页面契约。"
                : "已识别为模块树生成请求。系统会根据需求成熟度和附件资料推荐模块骨架、完整模块树或先整理资料；生成完成后会刷新左侧模块树并给出下一步建议。";

        return simpleResponse(
                AssistantIntentRouterService.INTENT_GENERATE_MODULE_TREE,
                reply,
                false,
                List.of(
                        AssistantActionResponse.of(
                                "GENERATE_MODULE_TREE",
                                externalPrdImport ? "导入 PRD 并生成模块树" : "生成模块树",
                                payload
                        )
                )
        );
    }

    private String inferModuleTreeModelingModeForImport(
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        String normalized = normalizeForIntent(messageText);
        if (containsAny(normalized, List.of("模块骨架", "只生成模块", "先生成模块", "不要页面", "不要功能点"))) {
            return "PROGRESSIVE_MODULE_OUTLINE";
        }
        if (containsAny(normalized, List.of("先整理", "整理资料", "资料整理", "整理后生成", "不确定项", "资料太杂"))) {
            return "ORGANIZE_THEN_GENERATE";
        }
        if (containsAny(normalized, List.of("完整模块树", "完整蓝图", "直接生成完整", "功能点和页面"))) {
            return "FULL_ENTERPRISE_BLUEPRINT";
        }
        if (isExternalPrdImportIntent(messageText, attachmentContexts)) {
            return "FULL_ENTERPRISE_BLUEPRINT";
        }
        if (hasParsedAttachmentText(attachmentContexts)) {
            return "ORGANIZE_THEN_GENERATE";
        }

        return "PROGRESSIVE_MODULE_OUTLINE";
    }

    private boolean isExternalPrdImportIntent(
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        String normalized = normalizeForIntent(messageText);
        boolean hasParsedText = hasParsedAttachmentText(attachmentContexts);

        boolean mentionsPrd = containsAny(
                normalized,
                List.of(
                        "prd",
                        "需求文档",
                        "需求说明",
                        "旧需求",
                        "旧prd",
                        "外部prd",
                        "外部需求"
                )
        );

        boolean mentionsImport = containsAny(
                normalized,
                List.of(
                        "导入",
                        "迁移",
                        "识别",
                        "转换",
                        "转成",
                        "映射",
                        "匹配",
                        "接入",
                        "上传",
                        "解析"
                )
        );

        return mentionsPrd && (mentionsImport || hasParsedText);
    }


    private AssistantMessageResponse buildAssistantUiCommandResponse(
            AssistantMessageRequest request,
            String messageText,
            AssistantIntentClassificationResult classification
    ) {
        List<AssistantActionResponse> actions = buildAssistantUiCommandActions(
                request,
                messageText,
                classification
        );

        return simpleResponse(
                AssistantIntentRouterService.INTENT_EXECUTE_UI_COMMAND,
                buildAssistantUiCommandReply(actions),
                false,
                actions
        );
    }

    private List<AssistantActionResponse> buildAssistantUiCommandActions(
            AssistantMessageRequest request,
            String messageText,
            AssistantIntentClassificationResult classification
    ) {
        String normalized = normalizeForIntent(messageText);
        String artifactType = inferAssistantCommandArtifactType(
                request,
                messageText,
                classification
        );

        List<AssistantActionResponse> actions = new ArrayList<>();

        if (containsAny(normalized, List.of("质量检查", "质量检测"))) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_QUALITY_CHECK",
                    "打开质量检查",
                    assistantCommandPayload(artifactType)
            ));
        }

        if (containsAny(normalized, List.of("版本历史", "历史版本", "版本记录"))) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_ARTIFACT_VERSIONS",
                    "打开版本历史",
                    assistantCommandPayload(artifactType)
            ));
        }

        if (containsAny(normalized, List.of("影响分析", "下游影响", "受影响资产"))) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_IMPACT_ANALYSIS",
                    "打开影响分析",
                    assistantCommandPayload(artifactType)
            ));
        }

        if (containsAny(
                normalized,
                List.of(
                        "交互预览",
                        "预览界面",
                        "页面预览",
                        "交互原型预览",
                        "ui预览",
                        "ui_contract",
                        "uicontract"
                )
        )) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_UI_CONTRACT_PREVIEW",
                    "打开交互预览",
                    assistantCommandPayload("UI_CONTRACT")
            ));
        }

        if (containsAny(normalized, List.of("准备度", "刷新准备", "刷新交互原型"))) {
            actions.add(AssistantActionResponse.of(
                    "REFRESH_UI_CONTRACT_READINESS",
                    "刷新交互原型准备度",
                    assistantCommandPayload("UI_CONTRACT")
            ));
        }

        if (containsAny(normalized, List.of("开发agent", "开发 agent", "development"))) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_DEVELOPMENT_TAB",
                    "切换到开发 Agent",
                    assistantCommandPayload(artifactType)
            ));
        }

        if (containsAny(normalized, List.of("需求agent", "需求 agent", "需求工作区"))) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_REQUIREMENT_TAB",
                    "切换到需求 Agent",
                    assistantCommandPayload(artifactType)
            ));
        }

        if (containsAny(normalized, List.of("任务中心", "项目任务中心"))) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_PROJECT_TASK_CENTER",
                    "打开项目任务中心",
                    assistantCommandPayload(artifactType)
            ));
        }

        if (actions.isEmpty() && artifactType != null && !artifactType.isBlank()) {
            actions.add(AssistantActionResponse.of(
                    "VIEW_ARTIFACT",
                    "查看资产",
                    assistantCommandPayload(artifactType)
            ));
        }

        if (actions.isEmpty()) {
            actions.add(AssistantActionResponse.of(
                    "OPEN_QUALITY_CHECK",
                    "打开质量检查",
                    assistantCommandPayload(artifactType)
            ));
        }

        return actions;
    }

    private Map<String, Object> assistantCommandPayload(
            String artifactType
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactType", artifactType);
        payload.put("source", "ASSISTANT_COMMAND_ROUTER");
        return payload;
    }

    private String buildAssistantUiCommandReply(
            List<AssistantActionResponse> actions
    ) {
        if (actions == null || actions.isEmpty()) {
            return "我识别到你想通过聊天触发页面操作，但没有定位到可执行动作。";
        }

        List<String> labels = new ArrayList<>();

        for (AssistantActionResponse action : actions) {
            if (action != null && action.getLabel() != null && !action.getLabel().isBlank()) {
                labels.add(action.getLabel());
            }
        }

        return "我已识别为页面操作指令，正在为你执行：" + String.join("、", labels) + "。";
    }

    private String inferAssistantCommandArtifactType(
            AssistantMessageRequest request,
            String messageText,
            AssistantIntentClassificationResult classification
    ) {
        if (classification != null
                && classification.getArtifactType() != null
                && !classification.getArtifactType().isBlank()
                && !"FULL_CHAIN".equalsIgnoreCase(classification.getArtifactType())) {
            return classification.getArtifactType();
        }

        if (request != null
                && request.getSelectedArtifactType() != null
                && !request.getSelectedArtifactType().isBlank()) {
            return request.getSelectedArtifactType();
        }

        List<String> affectedArtifacts = inferAffectedArtifacts(messageText);

        if (affectedArtifacts == null || affectedArtifacts.isEmpty()) {
            return null;
        }

        return affectedArtifacts.get(0);
    }


    private AssistantMessageResponse buildRequirementChangeProposalResponse(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        String enrichedInstruction = buildInstructionWithAttachmentContext(
                messageText,
                attachmentContexts
        );

        List<ProjectModuleService.ModuleTreeNode> moduleTree =
                projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                        workspaceId,
                        projectCode
                );

        Map<String, Object> proposal = buildRequirementChangeProposal(
                request,
                enrichedInstruction,
                moduleTree,
                attachmentContexts
        );

        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("proposal", proposal);

        return simpleResponse(
                INTENT_PROPOSE_REQUIREMENT_CHANGE,
                buildRequirementChangeProposalReply(proposal),
                true,
                List.of(
                        AssistantActionResponse.of(
                                ACTION_REQUIREMENT_CHANGE_PROPOSAL,
                                "查看需求变更草案",
                                actionPayload
                        )
                )
        );
    }


    private boolean shouldBuildAttachmentArtifactDiffProposal(
            AssistantMessageRequest request,
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (request == null ||
                request.getSelectedNodeId() == null ||
                request.getSelectedArtifactType() == null ||
                request.getSelectedArtifactType().isBlank()) {
            return false;
        }

        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return false;
        }

        String normalized = normalizeForIntent(messageText);

        return containsAny(
                normalized,
                List.of(
                        "附件与当前资产差异",
                        "附件和当前资产差异",
                        "附件与当前需求差异",
                        "附件和当前需求差异",
                        "附件与当前文档差异",
                        "附件和当前文档差异",
                        "对比当前资产",
                        "对比当前需求",
                        "对比当前文档",
                        "差异对比",
                        "差异分析",
                        "有什么差异",
                        "哪里不一致",
                        "不一致",
                        "冲突",
                        "缺失",
                        "遗漏",
                        "已覆盖",
                        "compare",
                        "diff"
                )
        );
    }

    private AssistantMessageResponse buildAttachmentDrivenChangeProposalResponse(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            AssistantIntentClassificationResult classification,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (!hasAttachmentContext(attachmentContexts)) {
            return simpleResponse(
                    AssistantIntentRouterService.INTENT_ANALYZE_ATTACHMENT,
                    buildAttachmentAnalysisReply(
                            attachmentContexts,
                            classification == null ? null : classification.getSuggestedReply()
                    ),
                    false,
                    List.of()
            );
        }

        List<ProjectModuleService.ModuleTreeNode> moduleTree =
                projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                        workspaceId,
                        projectCode
                );

        Map<String, Object> proposal = buildAttachmentDrivenChangeProposal(
                request,
                messageText,
                moduleTree,
                attachmentContexts
        );

        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("proposal", proposal);

        return simpleResponse(
                AssistantIntentRouterService.INTENT_ANALYZE_ATTACHMENT,
                buildAttachmentDrivenChangeReply(proposal),
                true,
                List.of(
                        AssistantActionResponse.of(
                                ACTION_ATTACHMENT_DRIVEN_CHANGE_PROPOSAL,
                                "查看附件驱动变更分析",
                                actionPayload
                        )
                )
        );
    }

    private String resolveDialogRequirementChangeIntent(
            String classifiedIntent,
            String messageText,
            boolean moduleTreeExists,
            AssistantMessageRequest request,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (!moduleTreeExists) {
            return classifiedIntent;
        }

        if (request != null
                && request.getExpectedIntent() != null
                && !request.getExpectedIntent().isBlank()) {
            return classifiedIntent;
        }

        /*
         * Phase 6-B: structural module-tree changes have higher priority than
         * current-asset patching. The selected node remains context, not a
         * mandatory edit target.
         */
        if (isStrongModuleTreeStructuralInstruction(messageText)) {
            return AssistantIntentRouterService.INTENT_PROPOSE_MODULE_TREE_PATCH;
        }

        /*
         * Phase 5-I hotfix: when the user is already viewing a concrete
         * requirement artifact, short instructions such as
         * "登录失败 5 次后锁定 10 分钟" are usually incremental edits to the
         * current asset, not a request to regenerate the whole artifact.
         *
         * Keep explicit regeneration commands on the old task path, but route
         * asset-level amendments into Requirement Change Proposal -> Asset Edit
         * Proposal, so no background generation task is created unexpectedly.
         */
        if (isCurrentAssetEditIntent(
                messageText,
                request,
                attachmentContexts
        )) {
            return INTENT_PROPOSE_REQUIREMENT_CHANGE;
        }

        if (AssistantIntentRouterService.INTENT_GENERATE_MODULE_TREE.equals(classifiedIntent)
                || AssistantIntentRouterService.INTENT_EXPLAIN_CURRENT_ARTIFACT.equals(classifiedIntent)) {
            return classifiedIntent;
        }

        if (AssistantIntentRouterService.INTENT_GENERATE_REQUIREMENT_ARTIFACT.equals(classifiedIntent)
                && isExplicitArtifactGenerationIntent(messageText)) {
            return classifiedIntent;
        }

        if (isDialogRequirementChangeIntent(
                messageText,
                attachmentContexts
        )) {
            return INTENT_PROPOSE_REQUIREMENT_CHANGE;
        }

        return classifiedIntent;
    }

    private boolean shouldForceModuleTreePatchIntent(
            String messageText,
            AssistantIntentClassificationResult classification
    ) {
        String text = messageText == null ? "" : messageText.trim();

        if (text.isBlank()) {
            return false;
        }

        /*
         * Asset-local edits should stay on the asset Patch-first path:
         * "把锁定时间改成 8 分钟" / "登录页加一个密码找回入口".
         * A local page entrance/button/field is not the same as adding a new
         * page node to the module tree.
         */
        if (isExplicitAssetLocalEditInstruction(text)
                && !isStrongModuleTreeStructuralInstruction(text)) {
            return false;
        }

        String classifiedIntent = classification == null
                ? ""
                : nullToEmpty(classification.getIntent());

        if (AssistantIntentRouterService.INTENT_PROPOSE_MODULE_TREE_PATCH.equals(classifiedIntent)
                && !isInlinePageAssetInstruction(text)) {
            return true;
        }

        return isStrongModuleTreeStructuralInstruction(text);
    }

    private boolean isStrongModuleTreeStructuralInstruction(
            String messageText
    ) {
        String normalized = normalizeForIntent(messageText);

        if (normalized.isBlank()) {
            return false;
        }

        if (isInlinePageAssetInstruction(normalized)) {
            return false;
        }

        boolean hasStructuralAction = containsAny(
                normalized,
                List.of(
                        "新增",
                        "增加",
                        "添加",
                        "创建",
                        "新建",
                        "加一个",
                        "加个",
                        "删掉",
                        "删除",
                        "移除",
                        "去掉",
                        "移动",
                        "挪到",
                        "放到",
                        "放在",
                        "归到",
                        "挂到",
                        "拆成",
                        "拆分",
                        "合并",
                        "重命名",
                        "改名"
                )
        );

        if (!hasStructuralAction) {
            return false;
        }

        boolean hasExplicitTreeObject = containsAny(
                normalized,
                List.of(
                        "模块树",
                        "节点",
                        "业务域",
                        "模块",
                        "子模块",
                        "页面",
                        "功能点",
                        "路由",
                        "菜单"
                )
        );

        boolean hasDedicatedPageNodeObject = containsAny(
                normalized,
                List.of(
                        "密码找回页",
                        "找回密码页",
                        "登录页",
                        "注册页",
                        "详情页",
                        "列表页",
                        "表单页",
                        "管理页",
                        "设置页",
                        "首页"
                )
        );

        boolean hasExplicitParentOrPlacement = containsAny(
                normalized,
                List.of(
                        "用户中心",
                        "账号中心",
                        "会员中心",
                        "登录注册",
                        "后台管理",
                        "订单中心",
                        "支付中心",
                        "库存中心",
                        "权限中心",
                        "下面",
                        "下方",
                        "上面",
                        "里面",
                        "所属",
                        "属于",
                        "挂在",
                        "挂到"
                )
        );

        /*
         * Strong module-tree examples:
         * - 给用户中心增加密码找回页
         * - 把发送验证码功能点移动到注册页下面
         * - 登录注册拆成登录页和注册页
         */
        return hasExplicitTreeObject ||
                hasDedicatedPageNodeObject ||
                hasExplicitParentOrPlacement;
    }

    private boolean isInlinePageAssetInstruction(
            String messageText
    ) {
        String normalized = normalizeForIntent(messageText);

        if (normalized.isBlank()) {
            return false;
        }

        boolean mentionsExistingPage = containsAny(
                normalized,
                List.of(
                        "登录页",
                        "注册页",
                        "当前页面",
                        "这个页面",
                        "该页面",
                        "当前需求",
                        "这个需求",
                        "这份需求",
                        "当前文档",
                        "这个页面规范"
                )
        );

        boolean hasInlineComponentOrRule = containsAny(
                normalized,
                List.of(
                        "入口",
                        "按钮",
                        "字段",
                        "控件",
                        "表单项",
                        "提示",
                        "校验",
                        "文案",
                        "链接",
                        "勾选框",
                        "验证码输入",
                        "错误提示",
                        "成功提示",
                        "锁定时间",
                        "失败次数",
                        "有效期",
                        "规则"
                )
        );

        boolean explicitlyCreatesPage = containsAny(
                normalized,
                List.of(
                        "新增页面",
                        "增加页面",
                        "添加页面",
                        "创建页面",
                        "新建页面",
                        "新增页",
                        "增加页",
                        "添加页",
                        "创建页",
                        "新建页",
                        "密码找回页",
                        "找回密码页"
                )
        );

        return mentionsExistingPage && hasInlineComponentOrRule && !explicitlyCreatesPage;
    }

    private boolean isExplicitAssetLocalEditInstruction(
            String messageText
    ) {
        String normalized = normalizeForIntent(messageText);

        if (normalized.isBlank()) {
            return false;
        }

        boolean hasLocalEditAction = containsAny(
                normalized,
                List.of(
                        "改成",
                        "修改成",
                        "改为",
                        "调整为",
                        "变成",
                        "限制为",
                        "不要",
                        "去除",
                        "改掉",
                        "替换为",
                        "加入口",
                        "增加入口",
                        "添加入口",
                        "加按钮",
                        "增加按钮",
                        "添加按钮",
                        "加字段",
                        "增加字段",
                        "添加字段"
                )
        );

        boolean hasRuleOrInlineObject = containsAny(
                normalized,
                List.of(
                        "时间",
                        "分钟",
                        "次数",
                        "字段",
                        "按钮",
                        "入口",
                        "提示",
                        "校验",
                        "规则",
                        "文案",
                        "状态",
                        "流程",
                        "验证码有效期",
                        "锁定时间",
                        "失败次数",
                        "密码找回入口",
                        "忘记密码入口"
                )
        );

        boolean explicitlyCurrent = containsAny(
                normalized,
                List.of(
                        "当前文档",
                        "当前需求",
                        "这个需求",
                        "这份需求",
                        "这个页面规范",
                        "这个接口",
                        "这里",
                        "上面这段",
                        "刚才这段"
                )
        );

        return hasLocalEditAction && (hasRuleOrInlineObject || explicitlyCurrent);
    }


    private boolean isCurrentAssetEditIntent(
            String messageText,
            AssistantMessageRequest request,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (request == null
                || request.getSelectedNodeId() == null
                || request.getSelectedArtifactType() == null
                || request.getSelectedArtifactType().isBlank()) {
            return false;
        }

        String artifactType = request.getSelectedArtifactType().trim().toUpperCase(Locale.ROOT);

        if (!List.of(
                "MODULE_PRD",
                "BUSINESS_FLOW",
                "PAGE_SPEC",
                "TEST_SPEC",
                "DATA_MODEL",
                "API_CONTRACT"
        ).contains(artifactType)) {
            return false;
        }

        String normalized = normalizeForIntent(messageText);

        if (normalized.isBlank()) {
            return false;
        }

        if (isUiCommandLikeText(normalized) || isExplicitArtifactGenerationIntent(messageText)) {
            return false;
        }

        if (hasParsedAttachmentContext(attachmentContexts)
                && containsAny(normalized, List.of(
                "根据附件修改",
                "按附件修改",
                "按文档修改",
                "根据文档修改",
                "根据图片修改",
                "按图片修改"
        ))) {
            return true;
        }

        return containsAny(normalized, List.of(
                "修改",
                "改成",
                "改为",
                "调整",
                "变成",
                "补充",
                "增加",
                "新增",
                "加入",
                "添加",
                "删除",
                "移除",
                "去掉",
                "不要",
                "不能",
                "不允许",
                "只允许",
                "必须",
                "需要",
                "限制",
                "禁止",
                "锁定",
                "校验",
                "验证",
                "失败",
                "成功",
                "次数",
                "分钟",
                "小时",
                "字段",
                "按钮",
                "提示",
                "状态",
                "流程",
                "权限",
                "角色",
                "规则",
                "异常",
                "错误",
                "手机号",
                "邮箱",
                "验证码",
                "退款",
                "库存",
                "订单",
                "支付",
                "登录",
                "注册",
                "unlock",
                "lock",
                "required",
                "optional",
                "disable",
                "enable",
                "change",
                "update",
                "remove",
                "add"
        ));
    }

    private boolean isExplicitArtifactGenerationIntent(
            String messageText
    ) {
        String normalized = normalizeForIntent(messageText);

        if (normalized.isBlank()) {
            return false;
        }

        if (containsAny(normalized, List.of(
                "重新生成",
                "重生成",
                "再生成",
                "重新跑",
                "重跑",
                "重新创建",
                "生成任务",
                "创建生成任务",
                "regenerate",
                "rerun"
        ))) {
            return true;
        }

        boolean hasGenerateVerb = containsAny(normalized, List.of(
                "生成",
                "创建",
                "写一份",
                "出一份",
                "generate",
                "create"
        ));

        if (!hasGenerateVerb) {
            return false;
        }

        return containsAny(normalized, List.of(
                "页面需求",
                "页面规范",
                "模块prd",
                "prd",
                "业务流程",
                "数据模型",
                "接口契约",
                "测试规格",
                "交互原型",
                "开发交接包",
                "核心资产",
                "全链路",
                "当前资产",
                "需求资产",
                "artifact",
                "page_spec",
                "module_prd",
                "business_flow",
                "data_model",
                "api_contract",
                "test_spec",
                "ui_contract"
        ));
    }

    private boolean isUiCommandLikeText(
            String normalized
    ) {
        return containsAny(normalized, List.of(
                "打开",
                "查看",
                "预览",
                "刷新",
                "版本历史",
                "影响分析",
                "质量检查",
                "任务中心",
                "切到",
                "进入开发",
                "open",
                "show",
                "preview",
                "refresh"
        ));
    }

    private boolean isDialogRequirementChangeIntent(
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        String normalized = normalizeForIntent(messageText);

        if (normalized.isBlank()) {
            return false;
        }

        if (containsAny(
                normalized,
                List.of(
                        "改需求",
                        "修改需求",
                        "调整需求",
                        "变更需求",
                        "需求变更",
                        "新增需求",
                        "补充需求",
                        "客户改",
                        "甲方要求",
                        "客户要求",
                        "产品要求"
                )
        )) {
            return true;
        }

        boolean hasChangeVerb = containsAny(
                normalized,
                List.of(
                        "新增",
                        "增加",
                        "加一个",
                        "加上",
                        "添加",
                        "补充",
                        "去掉",
                        "删除",
                        "取消",
                        "不要",
                        "改成",
                        "改为",
                        "调整",
                        "拆成",
                        "拆分",
                        "合并",
                        "增加一个",
                        "改一下"
                )
        );

        boolean hasRequirementObject = containsAny(
                normalized,
                List.of(
                        "页面",
                        "页",
                        "功能",
                        "流程",
                        "模块",
                        "子模块",
                        "业务",
                        "接口",
                        "字段",
                        "按钮",
                        "权限",
                        "角色",
                        "状态",
                        "表单",
                        "表格",
                        "卡片",
                        "列表",
                        "验证码",
                        "退款",
                        "支付",
                        "审批",
                        "审核",
                        "登录",
                        "注册"
                )
        );

        if (hasChangeVerb && hasRequirementObject) {
            return true;
        }

        return hasParsedAttachmentContext(attachmentContexts)
                && containsAny(
                normalized,
                List.of(
                        "根据附件修改",
                        "按附件修改",
                        "根据文档修改",
                        "按文档修改",
                        "根据图片修改",
                        "按截图修改"
                )
        );
    }


    private Map<String, Object> buildAttachmentDrivenChangeProposal(
            AssistantMessageRequest request,
            String messageText,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        String materialInstruction = buildInstructionWithAttachmentContext(
                messageText,
                attachmentContexts
        );

        AttachmentModuleTreeCoverage moduleTreeCoverage =
                analyzeAttachmentModuleTreeCoverage(
                        moduleTree,
                        attachmentContexts
                );

        String recommendedDispatch = inferAttachmentDrivenExecutionAction(
                request,
                messageText,
                attachmentContexts
        );
        boolean externalPrdImport = isExternalPrdImportIntent(
                messageText,
                attachmentContexts
        );

        if (externalPrdImport) {
            recommendedDispatch = "MODULE_TREE_PATCH";
        }

        if (moduleTreeCoverage.applicable()) {
            if (moduleTreeCoverage.fullyCovered()) {
                recommendedDispatch = "ANALYSIS_ONLY";
            } else if (!moduleTreeCoverage.missingItems().isEmpty()) {
                recommendedDispatch = "MODULE_TREE_PATCH";
            }
        }

        Map<String, Object> selectedNode = resolveSelectedNodeContext(
                request == null ? null : request.getSelectedNodeId(),
                moduleTree
        );

        List<String> affectedArtifacts = inferAttachmentDrivenAffectedArtifacts(
                request,
                messageText,
                materialInstruction,
                recommendedDispatch,
                attachmentContexts
        );

        List<String> riskNotes = buildAttachmentDrivenRiskNotes(
                attachmentContexts,
                recommendedDispatch
        );
        riskNotes.addAll(moduleTreeCoverage.riskNotes());
        riskNotes = deduplicate(riskNotes);

        List<String> recommendedSteps = buildAttachmentDrivenRecommendedSteps(
                recommendedDispatch
        );
        if (moduleTreeCoverage.fullyCovered()) {
            recommendedSteps = List.of(
                    "当前附件中的主要结构性需求已被模块树覆盖，建议不要重复生成模块树变更提案。",
                    "如仍需调整，请明确说明要新增、移动、重命名或删除哪个节点。",
                    "接下来可以选择一个 MODULE 进入“一键准备模块开发资产”，或选择 PAGE 补齐 PAGE_SPEC / UI_CONTRACT。"
            );
        } else if (moduleTreeCoverage.applicable()
                && !moduleTreeCoverage.missingItems().isEmpty()) {
            recommendedSteps = List.of(
                    "先检查“可能未覆盖”清单，确认这些内容确实应该成为模块树节点。",
                    "确认后再点击“生成模块树变更提案”，系统才会进入可审查 Patch 链路。",
                    "不要直接把附件重复上传产生的大量 ADD_NODE 当成最终结果；Patch 仍需人工确认后才可应用。"
            );
        }

        List<Map<String, Object>> candidateActions = buildAttachmentDrivenCandidateActions(
                affectedArtifacts,
                recommendedDispatch
        );
        if (moduleTreeCoverage.fullyCovered()) {
            candidateActions = buildCoverageOnlyCandidateActions();
        }

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("proposalId", UUID.randomUUID().toString());
        proposal.put("source", "ATTACHMENT_DRIVEN_CHANGE");
        proposal.put("status", "DRAFT");
        proposal.put("phase", "Phase 10-A8.2");
        if (externalPrdImport) {
            proposal.put("migrationMode", "EXTERNAL_OR_LEGACY_PRD_IMPORT");
            proposal.put("migrationTarget", "MODULE_TREE_AND_MODULE_PRD");
        }
        proposal.put("originalInstruction", messageText);
        proposal.put("summary", buildAttachmentDrivenSummary(
                messageText,
                attachmentContexts,
                recommendedDispatch,
                moduleTreeCoverage
        ));
        proposal.put("materialInstruction", buildMaterialInstructionWithCoverage(
                materialInstruction,
                moduleTreeCoverage
        ));
        proposal.put("recommendedDispatch", recommendedDispatch);
        proposal.put("executionAction", recommendedDispatch);
        proposal.put("targetNode", selectedNode);
        proposal.put("targetArtifactType", resolveAttachmentDrivenTargetArtifactType(
                request,
                affectedArtifacts,
                recommendedDispatch
        ));
        proposal.put("affectedArtifacts", affectedArtifacts);
        proposal.put("riskNotes", riskNotes);
        proposal.put("recommendedSteps", recommendedSteps);
        proposal.put("candidateActions", candidateActions);
        proposal.put("moduleTreeCoverage", moduleTreeCoverage.toMap());
        proposal.put("alreadyCoveredItems", moduleTreeCoverage.coveredItems());
        proposal.put("missingRequirements", moduleTreeCoverage.missingItems());
        proposal.put("possibleConflicts", moduleTreeCoverage.needsReviewItems());
        proposal.put("attachmentSummary", buildAttachmentSummaryForRequirementChange(attachmentContexts));
        proposal.put("requiresUserConfirmation", true);
        proposal.put("executionPolicy", "当前仅生成附件驱动差异分析，不会直接修改模块树、覆盖资产或创建生成任务；只有用户点击后续动作按钮才进入可审查提案链路。");

        return proposal;
    }

    private String buildAttachmentDrivenChangeReply(
            Map<String, Object> proposal
    ) {
        String summary = textValue(proposal.get("summary"));
        String recommendedDispatch = textValue(proposal.get("recommendedDispatch"));

        return firstNonBlank(
                summary,
                "我已根据附件形成一份材料驱动需求变更分析。"
        ) + "\n\n推荐处理方式：" + firstNonBlank(
                recommendedDispatch,
                "ANALYSIS_ONLY"
        ) + "。请在右侧卡片中选择下一步动作。";
    }

    private boolean shouldRunAttachmentModuleTreeDiffFirst(
            AssistantMessageRequest request,
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (!hasAttachmentContext(attachmentContexts)) {
            return false;
        }

        if (request != null &&
                request.getExpectedIntent() != null &&
                !request.getExpectedIntent().isBlank()) {
            return false;
        }

        String normalized = normalizeForIntent(messageText);

        /*
         * Direct user messages with attachments should first produce a diff /
         * coverage analysis. A real Patch is only produced after the user
         * explicitly asks for a patch/proposal or clicks the analysis card
         * action. This prevents re-uploading the same material from creating a
         * large ADD_NODE proposal immediately.
         */
        return !containsAny(
                normalized,
                List.of(
                        "生成模块树变更提案",
                        "生成变更提案",
                        "生成模块树提案",
                        "生成patch",
                        "生成补丁",
                        "生成可审查patch",
                        "直接生成提案",
                        "请生成提案",
                        "应用提案",
                        "应用patch",
                        "应用补丁"
                )
        );
    }

    private List<Map<String, Object>> buildCoverageOnlyCandidateActions() {
        return List.of(
                attachmentCandidateAction(
                        "ANALYSIS_ONLY",
                        "仅保留本次检查结论",
                        "当前附件主要结构已被模块树覆盖，不生成模块树 Patch。",
                        null
                ),
                attachmentCandidateAction(
                        "ATTACHMENT_ARTIFACT_DIFF",
                        "对比当前资产差异",
                        "如果你怀疑附件和当前需求资产内容不一致，可以先对比当前资产差异，而不是修改模块树。",
                        "MODULE_PRD"
                )
        );
    }

    private String buildMaterialInstructionWithCoverage(
            String materialInstruction,
            AttachmentModuleTreeCoverage coverage
    ) {
        if (coverage == null || !coverage.applicable()) {
            return materialInstruction;
        }

        List<String> lines = new ArrayList<>();
        lines.add(firstNonBlank(materialInstruction, ""));
        lines.add("");
        lines.add("【模块树覆盖检查】");
        lines.add("覆盖率：" + Math.round(coverage.coverageRatio() * 100) + "%");
        if (!coverage.coveredItems().isEmpty()) {
            lines.add("当前模块树已覆盖：" + String.join("、", coverage.coveredItems()));
        }
        if (!coverage.missingItems().isEmpty()) {
            lines.add("可能未覆盖：" + String.join("、", coverage.missingItems()));
        }
        if (!coverage.needsReviewItems().isEmpty()) {
            lines.add("需要人工复核：" + String.join("、", coverage.needsReviewItems()));
        }

        return String.join("\n", lines).trim();
    }

    private AttachmentModuleTreeCoverage analyzeAttachmentModuleTreeCoverage(
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (moduleTree == null || moduleTree.isEmpty() ||
                attachmentContexts == null || attachmentContexts.isEmpty()) {
            return AttachmentModuleTreeCoverage.notApplicable();
        }

        String attachmentText = attachmentContexts.stream()
                .map(AssistantAttachmentParsedContext::getParsedTextExcerpt)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();

        if (attachmentText.isBlank()) {
            return AttachmentModuleTreeCoverage.notApplicable();
        }

        Set<String> treeTokens = new LinkedHashSet<>();
        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flattenModuleTreeForCoverage(moduleTree, flatNodes);

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            addCoverageToken(treeTokens, node.getModuleName());
            addCoverageToken(treeTokens, node.getModuleKey());
            addCoverageToken(treeTokens, node.getModulePath());
        }

        List<String> candidates = extractAttachmentStructuralCandidates(attachmentText);

        if (candidates.isEmpty()) {
            return AttachmentModuleTreeCoverage.notApplicable();
        }

        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> needsReview = new ArrayList<>();

        for (String candidate : candidates) {
            if (isCoverageCandidateCovered(candidate, treeTokens)) {
                covered.add(candidate);
                continue;
            }

            if (isWeakCoverageCandidate(candidate)) {
                needsReview.add(candidate);
            } else {
                missing.add(candidate);
            }
        }

        int total = covered.size() + missing.size() + needsReview.size();
        if (total == 0) {
            return AttachmentModuleTreeCoverage.notApplicable();
        }

        double coverageRatio = covered.size() * 1.0 / total;

        if (coverageRatio >= 0.78 && missing.size() <= 1) {
            needsReview.addAll(missing);
            missing = new ArrayList<>();
        }

        List<String> riskNotes = new ArrayList<>();
        riskNotes.add("已先执行附件与当前模块树的覆盖检查，避免重复上传同一附件时直接生成大量 ADD_NODE。");

        if (missing.isEmpty()) {
            riskNotes.add("当前没有高置信缺失项，不建议生成模块树变更提案；如需调整，请明确说明目标节点。");
        } else {
            riskNotes.add("存在可能未覆盖项，但仍需人工确认它们是否应该成为 DOMAIN / MODULE / PAGE / FEATURE 节点。");
        }

        return new AttachmentModuleTreeCoverage(
                true,
                deduplicate(covered),
                deduplicate(missing),
                deduplicate(needsReview),
                coverageRatio,
                deduplicate(riskNotes)
        );
    }

    private void flattenModuleTreeForCoverage(
            List<ProjectModuleService.ModuleTreeNode> nodes,
            List<ProjectModuleService.ModuleTreeNode> result
    ) {
        if (nodes == null) {
            return;
        }

        for (ProjectModuleService.ModuleTreeNode node : nodes) {
            if (node == null) {
                continue;
            }

            result.add(node);
            flattenModuleTreeForCoverage(node.getChildren(), result);
        }
    }

    private void addCoverageToken(
            Set<String> tokens,
            String value
    ) {
        String normalized = normalizeCoverageText(value);

        if (!normalized.isBlank()) {
            tokens.add(normalized);
        }
    }

    private List<String> extractAttachmentStructuralCandidates(
            String attachmentText
    ) {
        List<String> result = new ArrayList<>();

        List<String> knownStructuralTerms = List.of(
                "患者端",
                "医生端",
                "药师端",
                "管理端",
                "管理员",
                "患者首页",
                "慢病档案",
                "慢病档案详情",
                "续方申请",
                "续方进度",
                "待审核列表",
                "患者详情",
                "随访记录",
                "随访记录编辑",
                "药师审方",
                "审方列表",
                "处方审核",
                "处方审核详情",
                "运营看板",
                "慢病运营看板",
                "管理配置",
                "药品目录",
                "随访模板",
                "疾病标签",
                "风险等级",
                "用药提醒",
                "随访提醒",
                "异常预警",
                "操作记录"
        );

        for (String term : knownStructuralTerms) {
            if (attachmentText.contains(term)) {
                result.add(term);
            }
        }

        String[] lines = attachmentText.split("\\R+");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine
                    .replaceFirst("^\\s*[-*•·\\d.、）)]+\\s*", "")
                    .trim();

            if (line.isBlank()) {
                continue;
            }

            String compact = compactAttachmentCandidateLine(line);

            if (isStructuralCandidateText(compact)) {
                result.add(compact);
            }
        }

        return deduplicate(result).stream()
                .filter(value -> value.length() >= 2 && value.length() <= 24)
                .limit(80)
                .toList();
    }

    private String compactAttachmentCandidateLine(
            String line
    ) {
        String compact = line
                .split("[，。；;：:、]")[0]
                .trim();

        for (String marker : List.of("可以", "支持", "包括", "需要", "必须", "用于", "负责")) {
            int index = compact.indexOf(marker);
            if (index > 1) {
                compact = compact.substring(0, index).trim();
            }
        }

        return compact;
    }

    private boolean isStructuralCandidateText(
            String value
    ) {
        if (value == null || value.isBlank() || value.length() > 24) {
            return false;
        }

        String normalized = normalizeForIntent(value);

        return containsAny(
                normalized,
                List.of(
                        "业务域",
                        "模块",
                        "子模块",
                        "页面",
                        "功能点",
                        "首页",
                        "列表",
                        "详情",
                        "申请",
                        "审核",
                        "审方",
                        "进度",
                        "档案",
                        "记录",
                        "提醒",
                        "预警",
                        "看板",
                        "目录",
                        "模板",
                        "配置",
                        "管理",
                        "患者",
                        "医生",
                        "药师",
                        "管理员"
                )
        );
    }

    private boolean isCoverageCandidateCovered(
            String candidate,
            Set<String> treeTokens
    ) {
        String normalized = normalizeCoverageText(candidate);

        if (normalized.isBlank()) {
            return true;
        }

        for (String token : treeTokens) {
            if (token.isBlank()) {
                continue;
            }

            if (token.contains(normalized) || normalized.contains(token)) {
                return true;
            }

            if (coverageSimilarity(token, normalized) >= 0.72) {
                return true;
            }
        }

        return false;
    }

    private boolean isWeakCoverageCandidate(
            String candidate
    ) {
        String normalized = normalizeCoverageText(candidate);

        return normalized.length() <= 3 ||
                containsAny(
                        normalized,
                        List.of(
                                "患者",
                                "医生",
                                "药师",
                                "管理员",
                                "管理",
                                "服务"
                        )
                );
    }

    private double coverageSimilarity(
            String left,
            String right
    ) {
        if (left.isBlank() || right.isBlank()) {
            return 0;
        }

        Set<Integer> leftChars = left.codePoints()
                .boxed()
                .collect(
                        LinkedHashSet::new,
                        LinkedHashSet::add,
                        LinkedHashSet::addAll
                );
        Set<Integer> rightChars = right.codePoints()
                .boxed()
                .collect(
                        LinkedHashSet::new,
                        LinkedHashSet::add,
                        LinkedHashSet::addAll
                );

        if (leftChars.isEmpty() || rightChars.isEmpty()) {
            return 0;
        }

        int overlap = 0;
        for (Integer value : leftChars) {
            if (rightChars.contains(value)) {
                overlap++;
            }
        }

        int base = Math.min(leftChars.size(), rightChars.size());
        return overlap * 1.0 / Math.max(1, base);
    }

    private String normalizeCoverageText(
            String value
    ) {
        return String.valueOf(value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）()《》“”\"'\\[\\]{}<>/\\\\|_-]+", "")
                .replace("页面", "页")
                .replace("子模块", "")
                .replace("模块", "")
                .replace("服务", "")
                .replace("管理平台", "")
                .trim();
    }

    private static final class AttachmentModuleTreeCoverage {
        private final boolean applicable;
        private final List<String> coveredItems;
        private final List<String> missingItems;
        private final List<String> needsReviewItems;
        private final double coverageRatio;
        private final List<String> riskNotes;

        private AttachmentModuleTreeCoverage(
                boolean applicable,
                List<String> coveredItems,
                List<String> missingItems,
                List<String> needsReviewItems,
                double coverageRatio,
                List<String> riskNotes
        ) {
            this.applicable = applicable;
            this.coveredItems = coveredItems == null ? List.of() : coveredItems;
            this.missingItems = missingItems == null ? List.of() : missingItems;
            this.needsReviewItems = needsReviewItems == null ? List.of() : needsReviewItems;
            this.coverageRatio = coverageRatio;
            this.riskNotes = riskNotes == null ? List.of() : riskNotes;
        }

        private static AttachmentModuleTreeCoverage notApplicable() {
            return new AttachmentModuleTreeCoverage(
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    List.of()
            );
        }

        private boolean applicable() {
            return applicable;
        }

        private boolean fullyCovered() {
            return applicable &&
                    missingItems.isEmpty() &&
                    coverageRatio >= 0.72;
        }

        private List<String> coveredItems() {
            return coveredItems;
        }

        private List<String> missingItems() {
            return missingItems;
        }

        private List<String> needsReviewItems() {
            return needsReviewItems;
        }

        private double coverageRatio() {
            return coverageRatio;
        }

        private List<String> riskNotes() {
            return riskNotes;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("applicable", applicable);
            result.put("fullyCovered", fullyCovered());
            result.put("coverageRatio", coverageRatio);
            result.put("coveredItems", coveredItems);
            result.put("missingItems", missingItems);
            result.put("needsReviewItems", needsReviewItems);
            return result;
        }
    }

    private String buildAttachmentDrivenSummary(
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts,
            String recommendedDispatch,
            AttachmentModuleTreeCoverage moduleTreeCoverage
    ) {
        int attachmentCount = attachmentContexts == null
                ? 0
                : attachmentContexts.size();

        long parsedTextCount = attachmentContexts == null
                ? 0
                : attachmentContexts.stream()
                .filter(context -> context.getParsedTextExcerpt() != null && !context.getParsedTextExcerpt().isBlank())
                .count();

        if (isImageDrivenPageUiChangeIntent(messageText, attachmentContexts)) {
            return "已接收图片/原型图类附件。当前阶段不会直接识别或编造图片中的视觉细节，" +
                    "但可以基于图片元数据与用户描述，先生成 PAGE_SPEC 页面规范局部修订提案；" +
                    "PAGE_SPEC 确认后再重新生成或确认 UI_CONTRACT。";
        }

        if (moduleTreeCoverage != null && moduleTreeCoverage.applicable()) {
            if (moduleTreeCoverage.fullyCovered()) {
                return "已读取 " + attachmentCount + " 个附件，并先与当前模块树做了覆盖检查。当前附件中的主要结构性需求已被模块树覆盖，未发现需要立即新增的模块树节点。";
            }

            if (!moduleTreeCoverage.missingItems().isEmpty()) {
                return "已读取 " + attachmentCount + " 个附件，并先与当前模块树做了覆盖检查。发现 " +
                        moduleTreeCoverage.missingItems().size() +
                        " 项可能未被模块树覆盖的结构性需求，请先审查差异清单，再决定是否生成模块树变更提案。";
            }
        }

        return "已读取 " + attachmentCount + " 个附件，其中 " + parsedTextCount +
                " 个包含可用文本摘录。系统判断该材料当前更适合走 " +
                recommendedDispatch + " 链路。";
    }

    private String inferAttachmentDrivenExecutionAction(
            AssistantMessageRequest request,
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        String normalized = normalizeForIntent(messageText);
        boolean hasParsedText = hasParsedAttachmentText(attachmentContexts);
        boolean imageDrivenPageUiChange = isImageDrivenPageUiChangeIntent(
                messageText,
                attachmentContexts
        );

        if (!hasParsedText) {
            return imageDrivenPageUiChange
                    ? "ASSET_EDIT_PROPOSAL"
                    : "ANALYSIS_ONLY";
        }

        if (imageDrivenPageUiChange) {
            return "ASSET_EDIT_PROPOSAL";
        }

        if (containsAny(
                normalized,
                List.of(
                        "模块树",
                        "补全模块",
                        "补充模块",
                        "完善模块",
                        "页面和功能点",
                        "页面、功能点",
                        "功能清单",
                        "根据附件改模块",
                        "按附件改模块",
                        "新增页面",
                        "新增功能点",
                        "导入prd",
                        "迁移prd",
                        "外部prd",
                        "旧prd",
                        "导入需求文档",
                        "迁移需求文档",
                        "识别成模块树",
                        "匹配成模块树",
                        "转成模块树",
                        "转成系统模块树"
                )
        )) {
            return "MODULE_TREE_PATCH";
        }

        if (containsAny(
                normalized,
                List.of(
                        "生成",
                        "写",
                        "出",
                        "创建资产",
                        "需求",
                        "页面规范",
                        "接口",
                        "api",
                        "测试",
                        "用例",
                        "原型"
                )
        )) {
            return "GENERATE_REQUIREMENT_ARTIFACT";
        }

        if (
                request != null &&
                        request.getSelectedArtifactType() != null &&
                        !request.getSelectedArtifactType().isBlank()
        ) {
            return "ASSET_EDIT_PROPOSAL";
        }

        return "MODULE_TREE_PATCH";
    }

    private List<Map<String, Object>> buildAttachmentDrivenCandidateActions(
            List<String> affectedArtifacts,
            String recommendedDispatch
    ) {
        List<Map<String, Object>> actions = new ArrayList<>();

        boolean pageUiChange = isPageUiAssetImpact(affectedArtifacts);

        actions.add(attachmentCandidateAction(
                "MODULE_TREE_PATCH",
                "生成模块树变更提案",
                "从附件中提取页面、功能点或模块变化，进入可审查 ModuleTreePatchAgent 提案。",
                null
        ));

        String firstArtifact = affectedArtifacts == null || affectedArtifacts.isEmpty()
                ? "MODULE_PRD"
                : affectedArtifacts.get(0);

        String assetEditLabel = pageUiChange
                ? "生成页面规范修订提案"
                : "生成资产局部修订提案";
        String assetEditDescription = pageUiChange
                ? "基于图片/原型图元数据和用户描述，先修订 PAGE_SPEC；确认后再重新生成或确认 UI_CONTRACT。"
                : "把附件材料作为变更依据，对当前节点已有需求资产生成局部 Patch 提案。";

        actions.add(attachmentCandidateAction(
                "ASSET_EDIT_PROPOSAL",
                assetEditLabel,
                assetEditDescription,
                firstArtifact
        ));

        actions.add(attachmentCandidateAction(
                "ATTACHMENT_ARTIFACT_DIFF",
                "对比当前资产差异",
                "将附件材料与当前选中的需求资产做差异对照，先输出缺失、冲突和已覆盖清单。",
                firstArtifact
        ));

        actions.add(attachmentCandidateAction(
                "GENERATE_REQUIREMENT_ARTIFACT",
                "生成需求资产",
                "基于附件材料为当前节点创建一个需求资产生成任务。",
                firstArtifact
        ));

        actions.add(attachmentCandidateAction(
                "ANALYSIS_ONLY",
                "仅保留摘要",
                "只保留附件分析结论，暂不修改模块树或需求资产。",
                null
        ));

        if (recommendedDispatch == null || recommendedDispatch.isBlank()) {
            return actions;
        }

        actions.sort((left, right) -> {
            boolean leftPreferred = recommendedDispatch.equals(textValue(left.get("type")));
            boolean rightPreferred = recommendedDispatch.equals(textValue(right.get("type")));

            if (leftPreferred == rightPreferred) {
                return 0;
            }

            return leftPreferred ? -1 : 1;
        });

        return actions;
    }

    private Map<String, Object> attachmentCandidateAction(
            String type,
            String label,
            String description,
            String targetArtifactType
    ) {
        Map<String, Object> action = new LinkedHashMap<>();

        action.put("type", type);
        action.put("label", label);
        action.put("description", description);
        action.put("targetArtifactType", targetArtifactType);
        action.put("expectedIntent", resolveExpectedIntentForAttachmentCandidate(type));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactType", targetArtifactType);
        action.put("payload", payload);

        return action;
    }

    private String resolveExpectedIntentForAttachmentCandidate(
            String type
    ) {
        if ("MODULE_TREE_PATCH".equals(type)) {
            return AssistantIntentRouterService.INTENT_PROPOSE_MODULE_TREE_PATCH;
        }

        if ("GENERATE_REQUIREMENT_ARTIFACT".equals(type)) {
            return AssistantIntentRouterService.INTENT_GENERATE_REQUIREMENT_ARTIFACT;
        }

        if ("ASSET_EDIT_PROPOSAL".equals(type)) {
            return INTENT_PROPOSE_REQUIREMENT_CHANGE;
        }

        if ("ATTACHMENT_ARTIFACT_DIFF".equals(type)) {
            return AssistantIntentRouterService.INTENT_ANALYZE_ATTACHMENT;
        }

        return AssistantIntentRouterService.INTENT_ANALYZE_ATTACHMENT;
    }

    private List<String> inferAttachmentDrivenAffectedArtifacts(
            AssistantMessageRequest request,
            String messageText,
            String materialInstruction,
            String recommendedDispatch,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (isImageDrivenPageUiChangeIntent(messageText, attachmentContexts)) {
            return List.of("PAGE_SPEC", "UI_CONTRACT", "TEST_SPEC");
        }

        if (
                request != null &&
                        request.getSelectedArtifactType() != null &&
                        !request.getSelectedArtifactType().isBlank()
        ) {
            return deduplicate(List.of(request.getSelectedArtifactType()));
        }

        List<String> inferred = inferAffectedArtifacts(
                firstNonBlank(messageText, "") + "\n" + firstNonBlank(materialInstruction, "")
        );

        if (inferred == null || inferred.isEmpty()) {
            return "MODULE_TREE_PATCH".equals(recommendedDispatch)
                    ? List.of("MODULE_PRD", "PAGE_SPEC", "TEST_SPEC")
                    : List.of("MODULE_PRD");
        }

        return inferred;
    }

    private String resolveAttachmentDrivenTargetArtifactType(
            AssistantMessageRequest request,
            List<String> affectedArtifacts,
            String recommendedDispatch
    ) {
        if (
                request != null &&
                        request.getSelectedArtifactType() != null &&
                        !request.getSelectedArtifactType().isBlank()
        ) {
            return request.getSelectedArtifactType();
        }

        if (!"GENERATE_REQUIREMENT_ARTIFACT".equals(recommendedDispatch)
                && !"ASSET_EDIT_PROPOSAL".equals(recommendedDispatch)) {
            return null;
        }

        return affectedArtifacts == null || affectedArtifacts.isEmpty()
                ? "MODULE_PRD"
                : affectedArtifacts.get(0);
    }

    private List<String> buildAttachmentDrivenRiskNotes(
            List<AssistantAttachmentParsedContext> attachmentContexts,
            String recommendedDispatch
    ) {
        List<String> risks = new ArrayList<>();

        risks.add("该分析不会直接修改模块树或覆盖需求资产，需用户选择下一步动作。");

        boolean hasImageOnly = attachmentContexts != null &&
                !attachmentContexts.isEmpty() &&
                attachmentContexts.stream().allMatch(context ->
                        context.getMimeType() != null &&
                                context.getMimeType().toLowerCase().startsWith("image/") &&
                                (context.getParsedTextExcerpt() == null || context.getParsedTextExcerpt().isBlank())
                );

        if (hasImageOnly) {
            risks.add("当前图片附件只记录元数据，尚未接入正式视觉理解模型，不能凭图片内容编造页面结构。");
        }

        if (hasImageAttachmentContext(attachmentContexts)
                && "ASSET_EDIT_PROPOSAL".equals(recommendedDispatch)) {
            risks.add("图片驱动页面修订只会先形成 PAGE_SPEC 修订提案；UI_CONTRACT 必须在 PAGE_SPEC 确认后重新生成或重新确认。");
        }

        if ("MODULE_TREE_PATCH".equals(recommendedDispatch)) {
            risks.add("附件中提取的模块树变化仍需人工确认父子层级，确认后才可应用。");
        }

        if ("ASSET_EDIT_PROPOSAL".equals(recommendedDispatch)) {
            risks.add("附件驱动资产修订应尽量局部 Patch，避免整份重写导致旧版本可追溯性下降。");
        }

        return deduplicate(risks);
    }

    private List<String> buildAttachmentDrivenRecommendedSteps(
            String recommendedDispatch
    ) {
        if ("MODULE_TREE_PATCH".equals(recommendedDispatch)) {
            return List.of(
                    "先检查附件驱动变更范围是否符合客户真实需求。",
                    "点击“生成模块树变更提案”，由 ModuleTreePatchAgent 生成可审查 Patch。",
                    "确认 Patch 后再应用模块树变更；旧资产同步放到影响分析或 AI 局部同步修订中处理。"
            );
        }

        if ("ASSET_EDIT_PROPOSAL".equals(recommendedDispatch)) {
            return List.of(
                    "确认附件材料确实作用于当前节点或当前资产；图片/原型图类材料需要人工补充关键字段、按钮和布局说明。",
                    "点击“生成页面规范修订提案”或“生成资产局部修订提案”，由资产变更 Agent 输出局部 Patch。",
                    "确认 PAGE_SPEC 或目标资产新版本后，再重新生成或确认受影响的 UI_CONTRACT、测试规格和开发交接资产。"
            );
        }

        if ("GENERATE_REQUIREMENT_ARTIFACT".equals(recommendedDispatch)) {
            return List.of(
                    "确认要基于附件生成的目标资产类型。",
                    "点击“生成需求资产”，系统创建后台生成任务。",
                    "生成完成后进入版本确认和后续影响分析。"
            );
        }

        return List.of(
                "先查看附件摘要和解析状态。",
                "确认需要修改模块树、修订资产还是生成新资产后，再继续说明。",
                "图片附件在未接入视觉模型前，不作为正式页面结构依据。"
        );
    }

    private Map<String, Object> buildRequirementChangeProposal(
            AssistantMessageRequest request,
            String instruction,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        Map<String, Object> selectedNode = resolveSelectedNodeContext(
                request == null ? null : request.getSelectedNodeId(),
                moduleTree
        );

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("proposalId", UUID.randomUUID().toString());
        proposal.put("source", hasParsedAttachmentContext(attachmentContexts)
                ? "ASSISTANT_DIALOG_WITH_ATTACHMENTS"
                : "ASSISTANT_DIALOG");
        proposal.put("status", "DRAFT");
        proposal.put("phase", "Phase 5-C/D");
        proposal.put("originalInstruction", instruction);
        proposal.put("summary", buildRequirementChangeSummary(instruction, selectedNode));
        proposal.put("changeType", inferRequirementChangeType(instruction));
        proposal.put("scopeType", inferRequirementChangeScope(instruction));
        proposal.put("targetNode", selectedNode);
        proposal.put("affectedNodeIds", inferAffectedNodeIds(selectedNode, instruction, moduleTree));
        proposal.put("affectedArtifacts", inferAffectedArtifacts(instruction));
        proposal.put("riskNotes", inferRequirementChangeRiskNotes(instruction));
        proposal.put("recommendedSteps", buildRequirementChangeRecommendedSteps());
        proposal.put("requiresUserConfirmation", true);
        proposal.put("executionPolicy", "用户确认后，系统会进入当前阶段可用的执行链路；模块树类变更会转为可审查 Patch，资产内容改写会进入后续资产编辑提案。");
        proposal.put("executionMode", inferRequirementChangeExecutionMode(instruction));
        proposal.put("executionAction", inferRequirementChangeExecutionAction(instruction));
        proposal.put("executionActions", inferRequirementChangeExecutionActions(instruction));
        proposal.put("nextPhase", "Phase 5-E/F 将接入资产编辑提案和新版本保存；Phase 6 将接入 ModuleTreePatchAgent 正式执行模块树 Patch。");

        if (hasParsedAttachmentContext(attachmentContexts)) {
            proposal.put("attachmentSummary", buildAttachmentSummaryForRequirementChange(attachmentContexts));
        }

        return proposal;
    }

    private Map<String, Object> resolveSelectedNodeContext(
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("nodeId", selectedNodeId == null ? null : selectedNodeId.toString());
        fallback.put("nodeName", "当前项目");
        fallback.put("nodeType", "PROJECT");
        fallback.put("moduleKey", null);
        fallback.put("modulePath", null);

        if (selectedNodeId == null || moduleTree == null || moduleTree.isEmpty()) {
            return fallback;
        }

        Map<String, Object> matched = findNodeAsMap(
                selectedNodeId.toString(),
                moduleTree
        );

        if (matched == null || matched.isEmpty()) {
            return fallback;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", textValue(
                firstPresent(matched, "id", "moduleId", "nodeId")
        ));
        result.put("nodeName", firstNonBlank(
                textValue(firstPresent(matched, "moduleName", "name", "title")),
                "当前节点"
        ));
        result.put("nodeType", firstNonBlank(
                textValue(firstPresent(matched, "moduleType", "type", "nodeType")),
                "MODULE"
        ));
        result.put("moduleKey", textValue(firstPresent(matched, "moduleKey", "key")));
        result.put("modulePath", textValue(firstPresent(matched, "modulePath", "path")));

        return result;
    }

    private Map<String, Object> findNodeAsMap(
            String targetNodeId,
            List<ProjectModuleService.ModuleTreeNode> nodes
    ) {
        if (targetNodeId == null || targetNodeId.isBlank() || nodes == null) {
            return null;
        }

        for (ProjectModuleService.ModuleTreeNode node : nodes) {
            Map<String, Object> nodeMap = objectMapper.convertValue(
                    node,
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            String currentId = textValue(
                    firstPresent(nodeMap, "id", "moduleId", "nodeId")
            );

            if (targetNodeId.equals(currentId)) {
                return nodeMap;
            }

            Map<String, Object> matched = findNodeAsMap(
                    targetNodeId,
                    convertNodeChildren(firstPresent(nodeMap, "children"))
            );

            if (matched != null) {
                return matched;
            }
        }

        return null;
    }

    private List<ProjectModuleService.ModuleTreeNode> convertNodeChildren(
            Object childrenValue
    ) {
        if (!(childrenValue instanceof List<?> children) || children.isEmpty()) {
            return List.of();
        }

        List<ProjectModuleService.ModuleTreeNode> result = new ArrayList<>();

        for (Object child : children) {
            try {
                result.add(
                        objectMapper.convertValue(
                                child,
                                ProjectModuleService.ModuleTreeNode.class
                        )
                );
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed child node in assistant context building.
            }
        }

        return result;
    }

    private List<String> inferAffectedNodeIds(
            Map<String, Object> selectedNode,
            String instruction,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        List<String> result = new ArrayList<>();

        String selectedNodeId = textValue(selectedNode.get("nodeId"));

        if (!selectedNodeId.isBlank()) {
            result.add(selectedNodeId);
        }

        String normalized = normalizeForIntent(instruction);

        if (moduleTree != null) {
            for (ProjectModuleService.ModuleTreeNode node : moduleTree) {
                collectMentionedNodeIds(
                        node,
                        normalized,
                        result
                );
            }
        }

        return deduplicate(result);
    }

    private void collectMentionedNodeIds(
            ProjectModuleService.ModuleTreeNode node,
            String normalizedInstruction,
            List<String> result
    ) {
        if (node == null) {
            return;
        }

        Map<String, Object> nodeMap = objectMapper.convertValue(
                node,
                new TypeReference<Map<String, Object>>() {
                }
        );

        String nodeId = textValue(firstPresent(nodeMap, "id", "moduleId", "nodeId"));
        String nodeName = normalizeForIntent(
                textValue(firstPresent(nodeMap, "moduleName", "name", "title"))
        );
        String nodeKey = normalizeForIntent(
                textValue(firstPresent(nodeMap, "moduleKey", "key"))
        );

        if (!nodeId.isBlank()
                && (
                (!nodeName.isBlank() && normalizedInstruction.contains(nodeName)) ||
                        (!nodeKey.isBlank() && normalizedInstruction.contains(nodeKey))
        )) {
            result.add(nodeId);
        }

        for (ProjectModuleService.ModuleTreeNode child : convertNodeChildren(
                firstPresent(nodeMap, "children")
        )) {
            collectMentionedNodeIds(
                    child,
                    normalizedInstruction,
                    result
            );
        }
    }

    private List<String> inferAffectedArtifacts(
            String instruction
    ) {
        String normalized = normalizeForIntent(instruction);
        List<String> artifacts = new ArrayList<>();

        if (containsAny(normalized, List.of("页面", "页", "按钮", "表单", "表格", "卡片", "字段", "交互", "原型", "ui"))) {
            artifacts.add("PAGE_SPEC");
            artifacts.add("UI_CONTRACT");
            artifacts.add("TEST_SPEC");
        }

        if (containsAny(normalized, List.of("流程", "状态", "审批", "审核", "退款", "支付", "下单", "注册", "登录"))) {
            artifacts.add("BUSINESS_FLOW");
            artifacts.add("TEST_SPEC");
        }

        if (containsAny(normalized, List.of("数据", "字段", "表", "实体", "模型", "状态"))) {
            artifacts.add("DATA_MODEL");
        }

        if (containsAny(normalized, List.of("接口", "api", "提交", "查询", "回调", "权限", "验证码"))) {
            artifacts.add("API_CONTRACT");
            artifacts.add("TEST_SPEC");
        }

        if (artifacts.isEmpty()) {
            artifacts.add("MODULE_PRD");
            artifacts.add("BUSINESS_FLOW");
            artifacts.add("PAGE_SPEC");
        } else {
            artifacts.add(0, "MODULE_PRD");
        }

        artifacts.add("DEV_HANDOFF_PACKAGE");

        return deduplicate(artifacts);
    }

    private List<String> inferRequirementChangeRiskNotes(
            String instruction
    ) {
        String normalized = normalizeForIntent(instruction);
        List<String> risks = new ArrayList<>();

        risks.add("该草案尚未执行，当前不会修改模块树或覆盖任何需求资产。");

        if (containsAny(normalized, List.of("删除", "去掉", "取消", "不要"))) {
            risks.add("删除/取消类变更可能影响已有页面、接口、测试和开发交接包，需要确认是否保留历史资产版本。");
        }

        if (containsAny(normalized, List.of("权限", "角色", "审批", "审核"))) {
            risks.add("权限/审批类变更通常会同时影响业务流程、接口契约和测试规格。");
        }

        if (containsAny(normalized, List.of("支付", "退款", "订单", "库存"))) {
            risks.add("交易链路类变更需要重点确认数据一致性、异常流程和幂等接口。");
        }

        if (containsAny(normalized, List.of("页面", "表单", "字段", "按钮", "卡片", "表格"))) {
            risks.add("页面交互类变更需要重新确认 UI_CONTRACT，并触发页面相关资产影响分析。");
        }

        return deduplicate(risks);
    }

    private List<String> buildRequirementChangeRecommendedSteps() {
        return List.of(
                "用户先在 Assistant 面板确认该变更草案的范围是否正确。",
                "Phase 6 将把草案交给 ModuleTreePatchAgent，生成可执行模块树 Patch。",
                "应用 Patch 后进入 Phase 8，分析受影响资产并选择性重生成。",
                "重生成后的 UI_CONTRACT 和 Development Plan 需要重新确认，避免旧交接包继续被使用。"
        );
    }

    private List<Map<String, Object>> buildAttachmentContextItemsForAgent(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> items = new ArrayList<>();

        for (AssistantAttachmentParsedContext context : attachmentContexts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attachmentId", context.getAttachmentId());
            item.put("fileName", context.getFileName());
            item.put("mimeType", context.getMimeType());
            item.put("size", context.getSize());
            item.put("parseStatus", context.getParseStatus());
            item.put("parsedTextExcerpt", context.getParsedTextExcerpt());
            item.put("metadata", context.getMetadata());
            items.add(item);
        }

        return items;
    }

    private Map<String, Object> buildAttachmentSummaryForRequirementChange(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        for (AssistantAttachmentParsedContext context : attachmentContexts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attachmentId", context.getAttachmentId());
            item.put("fileName", context.getFileName());
            item.put("mimeType", context.getMimeType());
            item.put("parseStatus", context.getParseStatus());
            item.put("parsedTextExcerpt", context.getParsedTextExcerpt());
            item.put("metadata", context.getMetadata());
            items.add(item);
        }

        summary.put("count", items.size());
        summary.put("items", items);

        return summary;
    }

    private String buildRequirementChangeProposalReply(
            Map<String, Object> proposal
    ) {
        return "我已把你的描述识别为“需求变更”，并生成了一份可审查的变更草案。"
                + "你可以点击“确认执行”进入当前可用的执行链路；如果草案不符合预期，可以点击“放弃并继续说明”，然后直接在聊天里补充你的真实意图。"
                + "\n\n变更摘要："
                + firstNonBlank(textValue(proposal.get("summary")), "待确认变更范围。");
    }

    private String buildRequirementChangeSummary(
            String instruction,
            Map<String, Object> selectedNode
    ) {
        String nodeName = firstNonBlank(
                textValue(selectedNode.get("nodeName")),
                "当前项目"
        );

        return "围绕“" + nodeName + "”处理对话式需求变更：" + trimForSummary(
                instruction,
                160
        );
    }

    private String inferRequirementChangeType(
            String instruction
    ) {
        String normalized = normalizeForIntent(instruction);

        if (containsAny(normalized, List.of("删除", "去掉", "取消", "不要"))) {
            return "REMOVE";
        }

        if (containsAny(normalized, List.of("新增", "增加", "添加", "加一个", "加上", "补充"))) {
            return "ADD";
        }

        if (containsAny(normalized, List.of("拆成", "拆分"))) {
            return "SPLIT";
        }

        if (containsAny(normalized, List.of("合并"))) {
            return "MERGE";
        }

        if (containsAny(normalized, List.of("改成", "改为", "修改", "调整", "替换"))) {
            return "UPDATE";
        }

        return "CHANGE";
    }

    private String inferRequirementChangeScope(
            String instruction
    ) {
        String normalized = normalizeForIntent(instruction);

        if (containsAny(normalized, List.of("页面", "页", "按钮", "表单", "表格", "卡片", "字段", "交互", "原型"))) {
            return "PAGE_OR_UI";
        }

        if (containsAny(normalized, List.of("流程", "状态", "审批", "审核", "退款", "支付", "下单"))) {
            return "BUSINESS_FLOW";
        }

        if (containsAny(normalized, List.of("接口", "api", "提交", "查询", "回调"))) {
            return "API";
        }

        if (containsAny(normalized, List.of("数据", "字段", "表", "实体", "模型"))) {
            return "DATA_MODEL";
        }

        if (containsAny(normalized, List.of("模块", "子模块", "功能"))) {
            return "MODULE_TREE";
        }

        return "GENERAL_REQUIREMENT";
    }

    private String inferRequirementChangeExecutionMode(
            String instruction
    ) {
        String scope = inferRequirementChangeScope(instruction);

        if ("MODULE_TREE".equals(scope)) {
            return "TREE_PATCH_CONFIRMATION";
        }

        if ("PAGE_OR_UI".equals(scope)) {
            return "ASSET_EDIT_AND_UI_REGENERATION";
        }

        if ("API".equals(scope) || "DATA_MODEL".equals(scope)) {
            return "STRUCTURED_ASSET_EDIT_PROPOSAL";
        }

        if ("BUSINESS_FLOW".equals(scope)) {
            return "ASSET_EDIT_WITH_IMPACT_ANALYSIS";
        }

        return "REQUIREMENT_CHANGE_REVIEW";
    }

    private String inferRequirementChangeExecutionAction(
            String instruction
    ) {
        String scope = inferRequirementChangeScope(instruction);

        if ("MODULE_TREE".equals(scope)) {
            return "CONFIRM_TO_MODULE_TREE_PATCH";
        }

        if ("PAGE_OR_UI".equals(scope)) {
            return "CONFIRM_TO_PAGE_SPEC_AND_UI_CONTRACT_CHANGE";
        }

        if ("API".equals(scope)) {
            return "CONFIRM_TO_API_CONTRACT_CHANGE";
        }

        if ("DATA_MODEL".equals(scope)) {
            return "CONFIRM_TO_DATA_MODEL_CHANGE";
        }

        if ("BUSINESS_FLOW".equals(scope)) {
            return "CONFIRM_TO_BUSINESS_FLOW_CHANGE";
        }

        return "CONFIRM_TO_REQUIREMENT_CHANGE_REVIEW";
    }

    private List<String> inferRequirementChangeExecutionActions(
            String instruction
    ) {
        String scope = inferRequirementChangeScope(instruction);

        if ("MODULE_TREE".equals(scope)) {
            return List.of(
                    "生成可审查模块树 Patch",
                    "用户确认后应用 Patch",
                    "标记受影响资产"
            );
        }

        if ("PAGE_OR_UI".equals(scope)) {
            return List.of(
                    "生成 PAGE_SPEC 资产编辑提案",
                    "确认后创建 PAGE_SPEC 新版本",
                    "重生成 UI_CONTRACT 并重新确认页面效果"
            );
        }

        if ("API".equals(scope)) {
            return List.of(
                    "生成 API_CONTRACT 结构化编辑提案",
                    "确认后创建 API_CONTRACT 新版本",
                    "触发下游 TEST_SPEC / UI_CONTRACT 影响分析"
            );
        }

        if ("DATA_MODEL".equals(scope)) {
            return List.of(
                    "生成 DATA_MODEL 结构化编辑提案",
                    "确认后创建 DATA_MODEL 新版本",
                    "触发 API_CONTRACT / TEST_SPEC / UI_CONTRACT 影响分析"
            );
        }

        return List.of(
                "确认变更范围",
                "定位受影响资产",
                "生成资产编辑提案或模块树 Patch"
        );
    }


    private Object firstPresent(
            Map<String, Object> map,
            String... keys
    ) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }

        return null;
    }

    private boolean hasParsedAttachmentContext(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        return hasAttachmentContext(attachmentContexts);
    }

    private String normalizeForIntent(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", "");
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }

        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private List<String> deduplicate(
            List<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            if (seen.add(value)) {
                result.add(value);
            }
        }

        return result;
    }

    private String trimForSummary(
            String value,
            int maxLength
    ) {
        String text = value == null ? "" : value.trim();

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private AssistantMessageResponse buildTreePatchProposalResponse(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        ProposeModuleTreePatchRequest proposeRequest = new ProposeModuleTreePatchRequest();
        proposeRequest.setInstruction(
                buildInstructionWithAttachmentContext(
                        messageText,
                        attachmentContexts
                )
        );
        proposeRequest.setSelectedNodeId(request.getSelectedNodeId());
        proposeRequest.setBaseArtifactId(request.getBaseArtifactId());
        proposeRequest.setBaseVersionNo(request.getBaseVersionNo());
        proposeRequest.setBaseChecksum(request.getBaseChecksum());
        proposeRequest.setBaseModuleTreeHash(request.getBaseModuleTreeHash());
        proposeRequest.setBaseModuleTreeNodeCount(request.getBaseModuleTreeNodeCount());
        proposeRequest.setSelectedDiffItemIds(
                normalizeSelectedDiffItemIds(request.getSelectedDiffItemIds())
        );
        proposeRequest.setSelectedDiffItems(
                normalizeSelectedDiffItems(request.getSelectedDiffItems())
        );
        proposeRequest.setAttachmentContexts(
                buildAttachmentContextItemsForAgent(attachmentContexts)
        );
        proposeRequest.setAttachmentSummary(
                buildAttachmentSummaryForRequirementChange(attachmentContexts)
        );

        ModuleTreePatchProposalResponse proposal =
                moduleTreePatchProposalService.propose(
                        workspaceId,
                        projectCode,
                        proposeRequest
                );

        AssistantMessageResponse response = simpleResponse(
                AssistantIntentRouterService.INTENT_PROPOSE_MODULE_TREE_PATCH,
                "我已根据你的描述生成模块树变更提案。请检查操作列表和影响分析，确认后再应用。",
                true,
                List.of(
                        AssistantActionResponse.of(
                                "APPLY_TREE_PATCH",
                                "应用提案"
                        )
                )
        );

        response.setProposal(proposal);

        return response;
    }

    private AssistantMessageResponse buildRequirementArtifactTaskResponse(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            AssistantIntentClassificationResult classification,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        try {
            AssistantArtifactTaskCommand baseCommand =
                    assistantArtifactTaskCommandResolver.resolve(
                            messageText,
                            request.getSelectedArtifactType(),
                            classification
                    );

            AssistantArtifactTaskCommand command =
                    enrichCommandWithAttachmentContext(
                            baseCommand,
                            attachmentContexts
                    );

            AssistantArtifactTaskDispatchResult dispatchResult =
                    assistantArtifactTaskDispatchService.dispatch(
                            workspaceId,
                            projectCode,
                            request.getSelectedNodeId(),
                            command
                    );

            return simpleResponse(
                    AssistantIntentRouterService.INTENT_GENERATE_REQUIREMENT_ARTIFACT,
                    dispatchResult.reply(),
                    false,
                    dispatchResult.actions()
            );
        } catch (IllegalArgumentException ex) {
            return simpleResponse(
                    AssistantIntentRouterService.INTENT_GENERATE_REQUIREMENT_ARTIFACT,
                    ex.getMessage(),
                    false,
                    List.of()
            );
        }
    }

    private AssistantArtifactTaskCommand enrichCommandWithAttachmentContext(
            AssistantArtifactTaskCommand command,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (command == null || !hasAttachmentContext(attachmentContexts)) {
            return command;
        }

        String enrichedRequirementText = buildInstructionWithAttachmentContext(
                command.requirementText(),
                attachmentContexts
        );

        return new AssistantArtifactTaskCommand(
                command.fullChain(),
                command.taskType(),
                command.artifactType(),
                command.title(),
                enrichedRequirementText,
                command.overwriteExisting()
        );
    }

    private boolean hasAttachmentContext(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return false;
        }

        return attachmentContexts.stream().anyMatch(
                context ->
                        (context.getParsedTextExcerpt() != null && !context.getParsedTextExcerpt().isBlank()) ||
                                (context.getMetadata() != null && !context.getMetadata().isEmpty()) ||
                                (context.getParseStatus() != null && !context.getParseStatus().isBlank())
        );
    }

    private String buildAttachmentAnalysisReply(
            List<AssistantAttachmentParsedContext> attachmentContexts,
            String suggestedReply
    ) {
        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return firstNonBlank(
                    suggestedReply,
                    "我没有检测到可分析的附件。你可以先上传 Word、PDF、Excel、文本或图片后再发送。"
            );
        }

        StringBuilder reply = new StringBuilder();

        reply.append(firstNonBlank(
                suggestedReply,
                "我已读取本次消息中的附件，并完成基础解析状态检查。"
        ));

        reply.append("\n\n附件解析结果：");

        for (AssistantAttachmentParsedContext context : attachmentContexts) {
            reply.append("\n- ")
                    .append(firstNonBlank(context.getFileName(), "未命名附件"))
                    .append("：")
                    .append(resolveAttachmentParseStatusText(context));

            if (context.getParsedTextExcerpt() != null && !context.getParsedTextExcerpt().isBlank()) {
                String excerpt = context.getParsedTextExcerpt().trim();
                int previewLength = Math.min(excerpt.length(), 120);

                reply.append("；摘录：")
                        .append(excerpt.substring(0, previewLength).replace("\n", " "));

                if (excerpt.length() > previewLength) {
                    reply.append("...");
                }
            }
        }

        reply.append("\n\n你可以继续说：")
                .append("\n- “根据这个文档补全模块树”；")
                .append("\n- “根据附件生成当前功能点需求”；")
                .append("\n- “根据 Excel 功能清单生成页面和功能点”。");

        return reply.toString();
    }

    private String resolveAttachmentParseStatusText(
            AssistantAttachmentParsedContext context
    ) {
        String status = context.getParseStatus() == null
                ? ""
                : context.getParseStatus().trim().toUpperCase();

        boolean isImage = context.getMimeType() != null &&
                context.getMimeType().toLowerCase().startsWith("image/");

        if ("PARSED".equals(status)) {
            return "已解析出文本内容";
        }

        if ("FAILED".equals(status)) {
            Object parseError = context.getMetadata() == null
                    ? null
                    : context.getMetadata().get("parseError");

            return "解析失败" + (parseError == null ? "" : "：" + parseError);
        }

        if (isImage) {
            return "图片已上传并记录元数据；视觉内容理解将在下一阶段接入";
        }

        return "已上传，暂无可用文本摘录";
    }

    private boolean isImageDrivenPageUiChangeIntent(
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (!hasImageAttachmentContext(attachmentContexts)) {
            return false;
        }

        String normalized = normalizeForIntent(messageText);

        if (normalized.isBlank()) {
            return false;
        }

        boolean mentionsImageMaterial = containsAny(
                normalized,
                List.of(
                        "图片",
                        "图",
                        "截图",
                        "原型图",
                        "设计稿",
                        "界面图",
                        "页面图",
                        "这张图",
                        "根据图",
                        "按图",
                        "看图",
                        "mockup",
                        "figma",
                        "ui"
                )
        );

        boolean mentionsPageOrInteraction = containsAny(
                normalized,
                List.of(
                        "页面",
                        "页",
                        "界面",
                        "交互",
                        "布局",
                        "表单",
                        "字段",
                        "按钮",
                        "入口",
                        "卡片",
                        "列表",
                        "详情",
                        "登录",
                        "注册",
                        "页面规范",
                        "ui_contract",
                        "ui契约",
                        "交互原型"
                )
        );

        boolean hasChangeIntent = containsAny(
                normalized,
                List.of(
                        "优化",
                        "修改",
                        "调整",
                        "完善",
                        "补全",
                        "补充",
                        "对齐",
                        "检查",
                        "看看",
                        "分析",
                        "建议",
                        "变更建议",
                        "缺什么",
                        "缺少什么"
                )
        );

        return mentionsImageMaterial && mentionsPageOrInteraction && hasChangeIntent;
    }

    private boolean hasImageAttachmentContext(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return false;
        }

        for (AssistantAttachmentParsedContext context : attachmentContexts) {
            String mimeType = context == null ? null : context.getMimeType();
            if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return true;
            }
        }

        return false;
    }

    private boolean isPageUiAssetImpact(
            List<String> affectedArtifacts
    ) {
        if (affectedArtifacts == null || affectedArtifacts.isEmpty()) {
            return false;
        }

        return affectedArtifacts.contains("PAGE_SPEC") && affectedArtifacts.contains("UI_CONTRACT");
    }

    private String buildInstructionWithAttachmentContext(
            String messageText,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (!hasAttachmentContext(attachmentContexts)) {
            return messageText;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("用户原始指令：\n")
                .append(messageText)
                .append("\n\n");

        builder.append("以下是用户上传附件的解析内容。请结合这些内容执行用户指定操作；不要编造附件中不存在的业务内容。\n\n");

        for (AssistantAttachmentParsedContext context : attachmentContexts) {
            builder.append("【附件】")
                    .append(firstNonBlank(context.getFileName(), "未命名附件"))
                    .append("\n")
                    .append("附件 ID：")
                    .append(context.getAttachmentId())
                    .append("\n")
                    .append("MIME 类型：")
                    .append(firstNonBlank(context.getMimeType(), "-"))
                    .append("\n")
                    .append("解析状态：")
                    .append(firstNonBlank(context.getParseStatus(), "UNKNOWN"))
                    .append("\n");

            if (context.getMetadata() != null && !context.getMetadata().isEmpty()) {
                builder.append("元数据：")
                        .append(toJson(context.getMetadata()))
                        .append("\n");
            }

            if (context.getParsedTextExcerpt() != null && !context.getParsedTextExcerpt().isBlank()) {
                builder.append("内容摘录：\n")
                        .append(context.getParsedTextExcerpt())
                        .append("\n\n");
            } else {
                builder.append("内容摘录：暂无可用文本。")
                        .append("\n\n");
            }
        }

        return builder.toString();
    }

    private boolean hasParsedAttachmentText(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return false;
        }

        return attachmentContexts.stream().anyMatch(
                context -> context.getParsedTextExcerpt() != null &&
                        !context.getParsedTextExcerpt().isBlank()
        );
    }

    private List<String> normalizeSelectedDiffItemIds(
            List<String> selectedDiffItemIds
    ) {
        if (selectedDiffItemIds == null || selectedDiffItemIds.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String item : selectedDiffItemIds) {
            String value = item == null ? "" : item.trim();
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }

        return result;
    }

    private List<Map<String, Object>> normalizeSelectedDiffItems(
            List<Map<String, Object>> selectedDiffItems
    ) {
        if (selectedDiffItems == null || selectedDiffItems.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Map<String, Object> item : selectedDiffItems) {
            if (item == null || item.isEmpty()) {
                continue;
            }

            Map<String, Object> normalized = new LinkedHashMap<>(item);
            String id = String.valueOf(normalized.getOrDefault("id", "")).trim();
            String fingerprint = id.isBlank()
                    ? normalizeSelectedDiffFingerprint(String.valueOf(normalized.getOrDefault("title", ""))) + "|" + normalizeSelectedDiffFingerprint(String.valueOf(normalized.getOrDefault("attachmentExcerpt", "")))
                    : id;

            if (fingerprint.isBlank() || !seen.add(fingerprint)) {
                continue;
            }

            result.add(normalized);
        }

        return result;
    }

    private String normalizeSelectedDiffFingerprint(
            String value
    ) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ");
    }


    private AssistantMessageResponse simpleResponse(
            String intent,
            String reply,
            boolean requiresUserConfirmation,
            List<AssistantActionResponse> actions
    ) {
        AssistantMessageResponse response = new AssistantMessageResponse();
        response.setIntent(intent);
        response.setReply(reply);
        response.setRequiresUserConfirmation(requiresUserConfirmation);
        response.setActions(actions == null ? List.of() : actions);

        return response;
    }

    private UUID persistMessage(
            UUID workspaceId,
            String projectCode,
            String role,
            String intent,
            UUID selectedNodeId,
            String selectedArtifactType,
            Map<String, Object> content
    ) {
        UUID messageId = UUID.randomUUID();

        String sql = """
                INSERT INTO assistant_message (
                    id,
                    workspace_id,
                    project_code,
                    role,
                    intent,
                    selected_node_id,
                    selected_artifact_type,
                    content,
                    created_by,
                    created_at
                )
                VALUES (
                    :id,
                    :workspaceId,
                    :projectCode,
                    :role,
                    :intent,
                    :selectedNodeId,
                    :selectedArtifactType,
                    CAST(:content AS jsonb),
                    :createdBy,
                    clock_timestamp()
                )
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", messageId)
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectCode", projectCode)
                        .addValue("role", role)
                        .addValue("intent", intent)
                        .addValue("selectedNodeId", selectedNodeId)
                        .addValue("selectedArtifactType", selectedArtifactType)
                        .addValue("content", toJson(content))
                        .addValue("createdBy", currentUserProvider.getCurrentUser().username())
        );

        return messageId;
    }

    private Map<String, Object> assistantResponseContent(
            AssistantMessageResponse response,
            AssistantIntentClassificationResult classification
    ) {
        Map<String, Object> content = new LinkedHashMap<>();

        Map<String, Object> requirementChangeProposal =
                extractRequirementChangeProposalFromActions(response.getActions());
        Map<String, Object> attachmentDrivenChangeProposal =
                extractAttachmentDrivenChangeProposalFromActions(response.getActions());
        Map<String, Object> attachmentArtifactDiffProposal =
                extractAttachmentArtifactDiffProposal(response);

        content.put("reply", nullToEmpty(response.getReply()));
        content.put("messageType", resolveAssistantMessageType(
                response,
                requirementChangeProposal,
                attachmentDrivenChangeProposal,
                attachmentArtifactDiffProposal
        ));
        content.put("intent", nullToEmpty(response.getIntent()));
        content.put("requiresUserConfirmation", response.isRequiresUserConfirmation());
        content.put("actions", response.getActions());
        content.put("proposal", response.getProposal() == null ? Map.of() : response.getProposal());
        content.put("requirementChangeProposal", requirementChangeProposal);
        content.put("attachmentDrivenChangeProposal", attachmentDrivenChangeProposal);
        content.put("attachmentArtifactDiffProposal", attachmentArtifactDiffProposal);
        content.put("classification", classification);

        return content;
    }


    private String resolveAssistantMessageType(
            AssistantMessageResponse response,
            Map<String, Object> requirementChangeProposal,
            Map<String, Object> attachmentDrivenChangeProposal,
            Map<String, Object> attachmentArtifactDiffProposal
    ) {
        if (response != null && response.getProposal() != null) {
            return "TREE_PATCH_PROPOSAL";
        }

        if (attachmentArtifactDiffProposal != null && !attachmentArtifactDiffProposal.isEmpty()) {
            return MESSAGE_TYPE_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL;
        }

        if (attachmentDrivenChangeProposal != null && !attachmentDrivenChangeProposal.isEmpty()) {
            return MESSAGE_TYPE_ATTACHMENT_DRIVEN_CHANGE_PROPOSAL;
        }

        if (requirementChangeProposal != null && !requirementChangeProposal.isEmpty()) {
            return MESSAGE_TYPE_REQUIREMENT_CHANGE_PROPOSAL;
        }

        return "TEXT";
    }

    private Map<String, Object> extractRequirementChangeProposalFromActions(
            List<AssistantActionResponse> actions
    ) {
        if (actions == null || actions.isEmpty()) {
            return new LinkedHashMap<>();
        }

        List<Map<String, Object>> actionMaps = objectMapper.convertValue(
                actions,
                new TypeReference<List<Map<String, Object>>>() {
                }
        );

        for (Map<String, Object> action : actionMaps) {
            if (!ACTION_REQUIREMENT_CHANGE_PROPOSAL.equals(textValue(action.get("type")))) {
                continue;
            }

            Map<String, Object> payload = asStringObjectMap(action.get("payload"));
            Map<String, Object> proposal = asStringObjectMap(payload.get("proposal"));

            return proposal.isEmpty() ? payload : proposal;
        }

        return new LinkedHashMap<>();
    }


    private Map<String, Object> extractAttachmentArtifactDiffProposal(
            AssistantMessageResponse response
    ) {
        if (response == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> direct = response.getAttachmentArtifactDiffProposal();

        if (direct != null && !direct.isEmpty()) {
            return direct;
        }

        return extractAttachmentArtifactDiffProposalFromActions(response.getActions());
    }

    private Map<String, Object> extractAttachmentArtifactDiffProposalFromActions(
            List<AssistantActionResponse> actions
    ) {
        if (actions == null || actions.isEmpty()) {
            return new LinkedHashMap<>();
        }

        List<Map<String, Object>> actionMaps = objectMapper.convertValue(
                actions,
                new TypeReference<List<Map<String, Object>>>() {
                }
        );

        for (Map<String, Object> action : actionMaps) {
            if (!ACTION_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL.equals(textValue(action.get("type")))) {
                continue;
            }

            Map<String, Object> payload = asStringObjectMap(action.get("payload"));
            Map<String, Object> proposal = asStringObjectMap(payload.get("proposal"));

            return proposal.isEmpty() ? payload : proposal;
        }

        return new LinkedHashMap<>();
    }


    private Map<String, Object> extractAttachmentDrivenChangeProposalFromActions(
            List<AssistantActionResponse> actions
    ) {
        if (actions == null || actions.isEmpty()) {
            return new LinkedHashMap<>();
        }

        List<Map<String, Object>> actionMaps = objectMapper.convertValue(
                actions,
                new TypeReference<List<Map<String, Object>>>() {
                }
        );

        for (Map<String, Object> action : actionMaps) {
            if (!ACTION_ATTACHMENT_DRIVEN_CHANGE_PROPOSAL.equals(textValue(action.get("type")))) {
                continue;
            }

            Map<String, Object> payload = asStringObjectMap(action.get("payload"));
            Map<String, Object> proposal = asStringObjectMap(payload.get("proposal"));

            return proposal.isEmpty() ? payload : proposal;
        }

        return new LinkedHashMap<>();
    }

    private Map<String, Object> asStringObjectMap(
            Object value
    ) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(
                    String.valueOf(entry.getKey()),
                    entry.getValue()
            );
        }

        return result;
    }

    private AssistantConversationMessageResponse toConversationMessage(
            UUID messageId,
            String role,
            String intent,
            UUID selectedNodeId,
            String selectedArtifactType,
            Map<String, Object> content,
            LocalDateTime createdAt
    ) {
        ModuleTreePatchProposalResponse proposal = parseProposal(content.get("proposal"));
        String messageType = firstNonBlank(
                stringValue(content.get("messageType")),
                proposal == null ? "TEXT" : "TREE_PATCH_PROPOSAL"
        );

        String text = resolveConversationText(
                role,
                content
        );

        return new AssistantConversationMessageResponse(
                messageId,
                role,
                intent,
                selectedNodeId,
                selectedArtifactType,
                messageType,
                text,
                parseAttachments(content.get("attachments")),
                proposal,
                content,
                createdAt
        );
    }

    private String resolveConversationText(
            String role,
            Map<String, Object> content
    ) {
        if ("USER".equalsIgnoreCase(role)) {
            return firstNonBlank(
                    stringValue(content.get("message")),
                    stringValue(content.get("reply"))
            );
        }

        return firstNonBlank(
                stringValue(content.get("reply")),
                stringValue(content.get("message"))
        );
    }

    private List<AssistantAttachmentRef> mergeAttachmentRefsWithParsedContexts(
            List<AssistantAttachmentRef> attachments,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<AssistantAttachmentRef> result = new ArrayList<>();

        for (AssistantAttachmentRef attachment : attachments) {
            AssistantAttachmentRef copy = copyAttachmentRef(attachment);
            AssistantAttachmentParsedContext context = findAttachmentContext(
                    attachment.getAttachmentId(),
                    attachmentContexts
            );

            if (context != null) {
                copy.setFileName(firstNonBlank(context.getFileName(), copy.getFileName()));
                copy.setMimeType(firstNonBlank(context.getMimeType(), copy.getMimeType()));
                copy.setSize(context.getSize() == null ? copy.getSize() : context.getSize());
                copy.setParseStatus(context.getParseStatus());
                copy.setParsedTextExcerpt(context.getParsedTextExcerpt());
                copy.setMetadata(context.getMetadata() == null ? new LinkedHashMap<>() : context.getMetadata());
            }

            result.add(copy);
        }

        return result;
    }

    private AssistantAttachmentRef copyAttachmentRef(
            AssistantAttachmentRef source
    ) {
        AssistantAttachmentRef copy = new AssistantAttachmentRef();

        if (source == null) {
            return copy;
        }

        copy.setAttachmentId(source.getAttachmentId());
        copy.setFileName(source.getFileName());
        copy.setMimeType(source.getMimeType());
        copy.setSize(source.getSize());
        copy.setParseStatus(source.getParseStatus());
        copy.setParsedTextExcerpt(source.getParsedTextExcerpt());
        copy.setMetadata(source.getMetadata() == null ? new LinkedHashMap<>() : source.getMetadata());

        return copy;
    }

    private AssistantAttachmentParsedContext findAttachmentContext(
            UUID attachmentId,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentId == null || attachmentContexts == null || attachmentContexts.isEmpty()) {
            return null;
        }

        return attachmentContexts.stream()
                .filter(context -> attachmentId.equals(context.getAttachmentId()))
                .findFirst()
                .orElse(null);
    }

    private List<AssistantAttachmentRef> parseAttachments(
            Object rawValue
    ) {
        if (rawValue == null) {
            return List.of();
        }

        try {
            return objectMapper.convertValue(
                    rawValue,
                    new TypeReference<List<AssistantAttachmentRef>>() {
                    }
            );
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private ModuleTreePatchProposalResponse parseProposal(
            Object rawValue
    ) {
        if (!(rawValue instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.convertValue(
                    rawValue,
                    ModuleTreePatchProposalResponse.class
            );
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, Object> parseContentJson(
            String json
    ) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Assistant 历史消息解析失败。", ex);
        }
    }

    private int countVisibleMessages(
            UUID workspaceId,
            String projectCode
    ) {
        String sql = """
                WITH latest_archive AS (
                    SELECT MAX(created_at) AS archived_at
                    FROM assistant_message
                    WHERE workspace_id = :workspaceId
                      AND project_code = :projectCode
                      AND intent = :archiveIntent
                      AND content ->> 'messageType' = :archiveMessageType
                )
                SELECT COUNT(1)
                FROM assistant_message m
                CROSS JOIN latest_archive a
                WHERE m.workspace_id = :workspaceId
                  AND m.project_code = :projectCode
                  AND (a.archived_at IS NULL OR m.created_at > a.archived_at)
                  AND NOT (
                      m.intent = :archiveIntent
                      AND m.content ->> 'messageType' = :archiveMessageType
                  )
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectCode", projectCode)
                        .addValue("archiveIntent", INTENT_CONVERSATION_ARCHIVE)
                        .addValue("archiveMessageType", MESSAGE_TYPE_ARCHIVE_MARKER),
                Integer.class
        );

        return count == null ? 0 : count;
    }

    private UUID findExistingNotificationMessageId(
            UUID workspaceId,
            String projectCode,
            String notificationKey
    ) {
        String sql = """
                SELECT id
                FROM assistant_message
                WHERE workspace_id = :workspaceId
                  AND project_code = :projectCode
                  AND role = 'ASSISTANT'
                  AND content ->> 'notificationKey' = :notificationKey
                ORDER BY created_at DESC
                LIMIT 1
                """;

        List<UUID> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectCode", projectCode)
                        .addValue("notificationKey", notificationKey),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        );

        return rows.isEmpty() ? null : rows.get(0);
    }

    private String buildTaskNotificationReply(
            String title,
            String status,
            String errorMessage
    ) {
        if ("SUCCESS".equals(status)) {
            return "“" + title + "”已生成完成，可在当前节点资产面板查看。";
        }

        if ("PARTIAL_SUCCESS".equals(status)) {
            return "“" + title + "”已生成完成，但存在部分警告。建议打开资产内容或项目任务中心查看详情。";
        }

        if ("FAILED".equals(status) || "TIMEOUT".equals(status)) {
            String reason = firstNonBlank(errorMessage, "未返回具体错误原因。");
            return "“" + title + "”生成失败：" + reason + " 可在项目任务中心查看详情或重试。";
        }

        if ("CANCELLED".equals(status)) {
            return "“" + title + "”生成任务已取消。需要时可以重新生成。";
        }

        if ("BLOCKED".equals(status)) {
            String reason = firstNonBlank(errorMessage, "正在等待上游依赖。");
            return "“" + title + "”正在等待依赖：" + reason;
        }

        return "“" + title + "”任务状态已更新为 " + status + "。";
    }

    private Map<String, Object> taskNotificationActionPayload(
            AssistantTaskNotificationRequest request
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("taskId", request.getTaskId());
        payload.put("moduleId", request.getModuleId());
        payload.put("taskType", request.getTaskType());
        payload.put("artifactType", request.getArtifactType());
        payload.put("artifactTitle", request.getArtifactTitle());
        payload.put("status", request.getStatus());
        payload.put("resultArtifactId", request.getResultArtifactId());

        return payload;
    }

    private String buildTaskNotificationKey(
            UUID taskId,
            String status
    ) {
        return "TASK_STATUS:" + taskId + ":" + status;
    }

    private String normalizeStatus(
            String status
    ) {
        return status == null ? null : status.trim().toUpperCase();
    }

    private String resolveEffectiveMessage(
            String messageText,
            AssistantIntentClassificationResult classification
    ) {
        if (
                classification != null &&
                        classification.getNormalizedMessage() != null &&
                        !classification.getNormalizedMessage().isBlank()
        ) {
            return classification.getNormalizedMessage().trim();
        }

        return messageText;
    }

    private String firstNonBlank(
            String first,
            String fallback
    ) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        return fallback;
    }


    private String textValue(
            Object value
    ) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value).trim();
    }

    private String stringValue(
            Object value
    ) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(
            Object value
    ) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Assistant 消息内容序列化失败。", e);
        }
    }

    private String nullToEmpty(
            String value
    ) {
        return value == null ? "" : value;
    }
}
