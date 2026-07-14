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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantMessageService {

    private static final String INTENT_TASK_NOTIFICATION = "TASK_NOTIFICATION";
    private static final String INTENT_CONVERSATION_ARCHIVE = "CONVERSATION_ARCHIVE";
    private static final String MESSAGE_TYPE_ARCHIVE_MARKER = "CONVERSATION_ARCHIVE_MARKER";

    private final ProjectModuleService projectModuleService;
    private final ModuleTreePatchProposalService moduleTreePatchProposalService;
    private final AssistantAttachmentService assistantAttachmentService;
    private final AssistantAttachmentParserService assistantAttachmentParserService;
    private final AssistantIntentRouterService assistantIntentRouterService;
    private final AssistantArtifactTaskCommandResolver assistantArtifactTaskCommandResolver;
    private final AssistantArtifactTaskDispatchService assistantArtifactTaskDispatchService;
    private final AssistantArtifactReviewService assistantArtifactReviewService;
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

        UUID userMessageId = persistMessage(
                workspaceId,
                projectCode,
                "USER",
                null,
                request.getSelectedNodeId(),
                request.getSelectedArtifactType(),
                Map.of(
                        "message", messageText,
                        "messageType", "TEXT",
                        "expectedIntent", nullToEmpty(request.getExpectedIntent()),
                        "attachments", enrichedAttachments,
                        "attachmentContexts", attachmentContexts
                )
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

        String intent = classification.getIntent();

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
        if (AssistantIntentRouterService.INTENT_GENERATE_MODULE_TREE.equals(intent)) {
            return buildGenerateModuleTreeResponse(
                    buildInstructionWithAttachmentContext(
                            messageText,
                            attachmentContexts
                    )
            );
        }

        if (AssistantIntentRouterService.INTENT_PROPOSE_MODULE_TREE_PATCH.equals(intent)) {
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
            return simpleResponse(
                    intent,
                    buildAttachmentAnalysisReply(
                            attachmentContexts,
                            classification.getSuggestedReply()
                    ),
                    false,
                    List.of()
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
            String messageText
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requirementText", messageText);

        return simpleResponse(
                AssistantIntentRouterService.INTENT_GENERATE_MODULE_TREE,
                "当前项目还没有模块树。我已识别为“生成模块树”意图，请确认生成结构化模块树。",
                false,
                List.of(
                        AssistantActionResponse.of(
                                "GENERATE_MODULE_TREE",
                                "生成模块树",
                                payload
                        )
                )
        );
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

        content.put("reply", nullToEmpty(response.getReply()));
        content.put("messageType", response.getProposal() == null ? "TEXT" : "TREE_PATCH_PROPOSAL");
        content.put("intent", nullToEmpty(response.getIntent()));
        content.put("requiresUserConfirmation", response.isRequiresUserConfirmation());
        content.put("actions", response.getActions());
        content.put("proposal", response.getProposal() == null ? Map.of() : response.getProposal());
        content.put("classification", classification);

        return content;
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