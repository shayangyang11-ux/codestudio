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
    private static final String ACTION_REQUIREMENT_CHANGE_PROPOSAL = "REQUIREMENT_CHANGE_PROPOSAL";

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

        String intent = resolveDialogRequirementChangeIntent(
                classification.getIntent(),
                messageText,
                !moduleTree.isEmpty(),
                request,
                attachmentContexts
        );

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

        Map<String, Object> requirementChangeProposal =
                extractRequirementChangeProposalFromActions(response.getActions());

        content.put("reply", nullToEmpty(response.getReply()));
        content.put("messageType", resolveAssistantMessageType(response, requirementChangeProposal));
        content.put("intent", nullToEmpty(response.getIntent()));
        content.put("requiresUserConfirmation", response.isRequiresUserConfirmation());
        content.put("actions", response.getActions());
        content.put("proposal", response.getProposal() == null ? Map.of() : response.getProposal());
        content.put("requirementChangeProposal", requirementChangeProposal);
        content.put("classification", classification);

        return content;
    }


    private String resolveAssistantMessageType(
            AssistantMessageResponse response,
            Map<String, Object> requirementChangeProposal
    ) {
        if (response != null && response.getProposal() != null) {
            return "TREE_PATCH_PROPOSAL";
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
