package com.codemind.platform.assistant;

import com.codemind.platform.agent.common.client.PythonAgentClient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantArtifactReviewService {

    private static final int MAX_ARTIFACT_TEXT_CHARS = 18_000;
    private static final int MAX_RELATED_ARTIFACT_TEXT_CHARS = 6_000;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PythonAgentClient pythonAgentClient;
    private final ObjectMapper objectMapper;

    public AssistantArtifactReviewService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PythonAgentClient pythonAgentClient,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.pythonAgentClient = pythonAgentClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AssistantMessageResponse review(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String messageText,
            AssistantIntentClassificationResult classification,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (request == null || request.getSelectedNodeId() == null) {
            return response(
                    "请先在左侧模块树中选择一个节点，再让我检查或解释当前资产。你可以选择具体 PAGE 页面、FEATURE 功能点或 MODULE 模块。",
                    List.of()
            );
        }

        Map<String, Object> currentNode = loadCurrentNode(request.getSelectedNodeId());
        List<Map<String, Object>> currentArtifacts = loadCurrentArtifacts(request.getSelectedNodeId());

        if (currentArtifacts.isEmpty()) {
            return response(
                    "当前节点还没有可审查的需求资产。请先生成一个资产，例如“生成功能点需求 / 生成页面规范 / 生成当前节点核心资产”，再让我进行资产审查。",
                    List.of(
                            AssistantActionResponse.of(
                                    "GENERATE_CORE_ASSETS",
                                    "生成当前节点核心资产",
                                    Map.of(
                                            "moduleId", request.getSelectedNodeId().toString(),
                                            "fullChain", true
                                    )
                            )
                    )
            );
        }

        String targetArtifactType = resolveTargetArtifactType(
                messageText,
                request.getSelectedArtifactType(),
                currentArtifacts
        );

        Map<String, Object> targetArtifact = findArtifactByType(
                currentArtifacts,
                targetArtifactType
        );

        if (targetArtifact == null) {
            String available = currentArtifacts.stream()
                    .map(item -> String.valueOf(item.get("artifactType")))
                    .distinct()
                    .reduce((left, right) -> left + "、" + right)
                    .orElse("无");

            return response(
                    "当前节点没有找到要审查的资产“" + targetArtifactType + "”。当前可审查资产包括：" + available + "。请先切换到对应资产，或明确说明要检查哪一个资产。",
                    List.of()
            );
        }

        Map<String, Object> reviewInput = new LinkedHashMap<>();
        reviewInput.put("instruction", messageText);
        reviewInput.put("classification", classification == null ? Map.of() : classification);
        reviewInput.put("currentNode", currentNode);
        reviewInput.put("targetArtifact", compactArtifact(targetArtifact, MAX_ARTIFACT_TEXT_CHARS));
        reviewInput.put("relatedArtifacts", compactRelatedArtifacts(currentArtifacts, targetArtifactType));
        reviewInput.put("attachmentContexts", attachmentContexts == null ? List.of() : attachmentContexts);
        reviewInput.put("reviewPolicy", buildReviewPolicy(targetArtifactType));

        Map<String, Object> content = callPythonReviewAgent(
                projectCode,
                request.getSelectedNodeId(),
                targetArtifactType,
                reviewInput
        );

        if (content == null || content.isEmpty()) {
            content = buildLocalReviewFallback(
                    messageText,
                    targetArtifactType,
                    targetArtifact,
                    currentArtifacts,
                    attachmentContexts
            );
        }

        String reply = formatReviewReply(content, targetArtifactType, targetArtifact);

        return response(
                reply,
                buildReviewActions(request.getSelectedNodeId(), targetArtifactType, targetArtifact)
        );
    }

    private Map<String, Object> callPythonReviewAgent(
            String projectCode,
            UUID selectedNodeId,
            String targetArtifactType,
            Map<String, Object> reviewInput
    ) {
        try {
            Object raw = pythonAgentClient.reviewAssistantArtifact(
                    UUID.randomUUID(),
                    projectCode,
                    selectedNodeId,
                    targetArtifactType,
                    String.valueOf(reviewInput.getOrDefault("instruction", "")),
                    reviewInput
            );

            Map<String, Object> responseMap = toStringObjectMap(raw);
            Map<String, Object> content = toStringObjectMap(responseMap.get("content"));

            return content.isEmpty() ? responseMap : content;
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("summary", "AI 审查 Agent 调用失败，已切换为本地规则审查。");
            fallback.put("riskLevel", "MEDIUM");
            fallback.put("conclusion", "未能完成大模型深度审查：" + ex.getMessage());
            fallback.put("issues", List.of(
                    issue("AI_REVIEW_UNAVAILABLE", "WARNING", "AI 审查服务暂不可用", ex.getMessage(), "稍后重试，或先根据本地规则检查结果处理明显问题。", "GENERAL")
            ));
            return fallback;
        }
    }

    private Map<String, Object> loadCurrentNode(
            UUID moduleId
    ) {
        String sql = """
                SELECT
                    id,
                    parent_id,
                    module_key,
                    module_name,
                    module_type,
                    module_path,
                    level_no,
                    sort_order,
                    extra::text AS extra_json
                FROM project_module
                WHERE id = :moduleId
                LIMIT 1
                """;

        List<Map<String, Object>> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("moduleId", moduleId),
                (rs, rowNum) -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", String.valueOf(rs.getObject("id")));
                    Object parentId = rs.getObject("parent_id");
                    node.put("parentId", parentId == null ? null : String.valueOf(parentId));
                    node.put("moduleKey", rs.getString("module_key"));
                    node.put("moduleName", rs.getString("module_name"));
                    node.put("moduleType", rs.getString("module_type"));
                    node.put("modulePath", rs.getString("module_path"));
                    node.put("levelNo", rs.getObject("level_no"));
                    node.put("sortOrder", rs.getObject("sort_order"));
                    node.put("extra", parseJsonMap(rs.getString("extra_json")));
                    return node;
                }
        );

        if (!rows.isEmpty()) {
            return rows.get(0);
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("id", moduleId.toString());
        fallback.put("moduleName", "当前节点");
        return fallback;
    }

    private List<Map<String, Object>> loadCurrentArtifacts(
            UUID moduleId
    ) {
        String sql = """
                SELECT
                    id,
                    artifact_type,
                    version_no,
                    title,
                    status,
                    is_current,
                    content::text AS content_json,
                    content_markdown,
                    created_at,
                    updated_at
                FROM artifact_version
                WHERE module_id = :moduleId
                  AND is_current = true
                ORDER BY updated_at DESC, created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("moduleId", moduleId),
                (rs, rowNum) -> {
                    Map<String, Object> artifact = new LinkedHashMap<>();
                    artifact.put("artifactId", String.valueOf(rs.getObject("id")));
                    artifact.put("artifactType", rs.getString("artifact_type"));
                    artifact.put("versionNo", rs.getObject("version_no"));
                    artifact.put("title", rs.getString("title"));
                    artifact.put("status", rs.getString("status"));
                    artifact.put("current", rs.getBoolean("is_current"));
                    artifact.put("content", parseJsonMap(rs.getString("content_json")));
                    artifact.put("contentMarkdown", rs.getString("content_markdown"));
                    artifact.put("createdAt", toLocalDateTimeString(rs.getTimestamp("created_at")));
                    artifact.put("updatedAt", toLocalDateTimeString(rs.getTimestamp("updated_at")));
                    return artifact;
                }
        );
    }

    private String resolveTargetArtifactType(
            String messageText,
            String selectedArtifactType,
            List<Map<String, Object>> artifacts
    ) {
        String hinted = inferArtifactTypeFromText(messageText);

        if (hinted != null && findArtifactByType(artifacts, hinted) != null) {
            return hinted;
        }

        if (selectedArtifactType != null && !selectedArtifactType.isBlank()) {
            return selectedArtifactType.trim().toUpperCase(Locale.ROOT);
        }

        if (hinted != null) {
            return hinted;
        }

        if (!artifacts.isEmpty()) {
            return String.valueOf(artifacts.get(0).get("artifactType"));
        }

        return "MODULE_PRD";
    }

    private String inferArtifactTypeFromText(
            String messageText
    ) {
        if (messageText == null || messageText.isBlank()) {
            return null;
        }

        String text = messageText.trim();
        String upper = text.toUpperCase(Locale.ROOT);

        if (upper.contains("UI_CONTRACT") || containsAny(text, List.of("原型", "界面", "UI契约", "UI 契约", "模拟界面"))) {
            return "UI_CONTRACT";
        }
        if (upper.contains("API_CONTRACT") || containsAny(text, List.of("接口", "API", "端点"))) {
            return "API_CONTRACT";
        }
        if (upper.contains("TEST_SPEC") || containsAny(text, List.of("测试", "用例", "验收"))) {
            return "TEST_SPEC";
        }
        if (upper.contains("DATA_MODEL") || containsAny(text, List.of("数据模型", "表结构", "数据库", "ViewModel"))) {
            return "DATA_MODEL";
        }
        if (upper.contains("PAGE_SPEC") || containsAny(text, List.of("页面规范", "页面说明", "页面地图"))) {
            return "PAGE_SPEC";
        }
        if (upper.contains("BUSINESS_FLOW") || containsAny(text, List.of("流程", "业务流程", "交互流程"))) {
            return "BUSINESS_FLOW";
        }
        if (upper.contains("DEV_HANDOFF_PACKAGE") || containsAny(text, List.of("开发说明", "交接包", "开发交接"))) {
            return "DEV_HANDOFF_PACKAGE";
        }
        if (upper.contains("PRD") || containsAny(text, List.of("需求", "需求说明"))) {
            return "MODULE_PRD";
        }

        return null;
    }

    private Map<String, Object> findArtifactByType(
            List<Map<String, Object>> artifacts,
            String artifactType
    ) {
        if (artifactType == null || artifactType.isBlank()) {
            return null;
        }

        String expected = artifactType.trim().toUpperCase(Locale.ROOT);

        return artifacts.stream()
                .filter(item -> expected.equals(String.valueOf(item.get("artifactType")).toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> compactArtifact(
            Map<String, Object> artifact,
            int maxTextChars
    ) {
        Map<String, Object> result = new LinkedHashMap<>(artifact);
        result.put("contentMarkdown", truncate(String.valueOf(artifact.getOrDefault("contentMarkdown", "")), maxTextChars));
        result.put("content", artifact.getOrDefault("content", Map.of()));
        return result;
    }

    private List<Map<String, Object>> compactRelatedArtifacts(
            List<Map<String, Object>> artifacts,
            String targetArtifactType
    ) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> artifact : artifacts) {
            String artifactType = String.valueOf(artifact.get("artifactType"));

            if (artifactType.equals(targetArtifactType)) {
                continue;
            }

            result.add(compactArtifact(artifact, MAX_RELATED_ARTIFACT_TEXT_CHARS));
        }

        return result;
    }

    private Map<String, Object> buildReviewPolicy(
            String artifactType
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("reviewDimensions", List.of(
                "完整性",
                "可执行性",
                "前后一致性",
                "字段/接口/测试覆盖",
                "权限、异常、边界条件",
                "是否符合当前节点层级职责"
        ));
        policy.put("targetArtifactType", artifactType);
        policy.put("mustReturnActionableSuggestions", true);
        policy.put("doNotInventMissingBusinessFacts", true);
        return policy;
    }

    private Map<String, Object> buildLocalReviewFallback(
            String messageText,
            String targetArtifactType,
            Map<String, Object> targetArtifact,
            List<Map<String, Object>> artifacts,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> suggestions = new ArrayList<>();

        String markdown = String.valueOf(targetArtifact.getOrDefault("contentMarkdown", ""));
        String status = String.valueOf(targetArtifact.getOrDefault("status", ""));

        if (markdown.isBlank() || markdown.length() < 120) {
            issues.add(issue(
                    "CONTENT_TOO_THIN",
                    "BLOCKER",
                    "资产正文内容不足",
                    "当前资产正文为空或过短，难以作为正式需求工程交付物。",
                    "重新生成该资产，并确保包含目标、范围、规则、异常、验收或接口等核心章节。",
                    targetArtifactType
            ));
        }

        if (!"ACTIVE".equalsIgnoreCase(status) && !"APPROVED".equalsIgnoreCase(status)) {
            issues.add(issue(
                    "STATUS_NOT_CONFIRMED",
                    "WARNING",
                    "资产状态尚未确认",
                    "当前资产状态为 " + status + "，正式交付前建议提交审核并确认。",
                    "完成修改后提交审核，或确认当前版本是否可作为后续资产依据。",
                    targetArtifactType
            ));
        }

        if (("API_CONTRACT".equals(targetArtifactType) || "TEST_SPEC".equals(targetArtifactType))
                && findArtifactByType(artifacts, "MODULE_PRD") == null) {
            issues.add(issue(
                    "MISSING_PRD_CONTEXT",
                    "WARNING",
                    "缺少需求说明作为上游依据",
                    "当前节点未发现需求说明资产，接口或测试可能缺少业务规则来源。",
                    "先补齐当前节点需求说明，再重新审查接口或测试资产。",
                    "MODULE_PRD"
            ));
        }

        if ("UI_CONTRACT".equals(targetArtifactType) && findArtifactByType(artifacts, "PAGE_SPEC") == null) {
            issues.add(issue(
                    "MISSING_PAGE_SPEC_CONTEXT",
                    "WARNING",
                    "交互原型缺少页面规范依据",
                    "当前节点未发现页面规范，UI 契约可能缺少字段、布局和状态依据。",
                    "先生成或更新页面规范，再重新生成交互原型。",
                    "PAGE_SPEC"
            ));
        }

        if (attachmentContexts != null && !attachmentContexts.isEmpty()) {
            suggestions.add(suggestion(
                    "ATTACHMENT_ALIGNMENT",
                    "建议对照附件逐项核对遗漏",
                    "本次消息包含附件。请重点核对资产内容是否覆盖附件中的页面、字段、规则、异常和验收要求。"
            ));
        }

        if (issues.isEmpty()) {
            issues.add(issue(
                    "NO_BLOCKER_FOUND_BY_LOCAL_RULES",
                    "INFO",
                    "本地规则未发现明显阻塞问题",
                    "当前资产具备基础内容。更深层的一致性与语义审查需要 AI 审查 Agent 可用后完成。",
                    "继续检查上下游资产一致性，或提交人工审核。",
                    targetArtifactType
            ));
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("summary", "已完成当前资产的本地规则审查。");
        content.put("riskLevel", issues.stream().anyMatch(item -> "BLOCKER".equals(item.get("severity"))) ? "HIGH" : "MEDIUM");
        content.put("conclusion", issues.stream().anyMatch(item -> "BLOCKER".equals(item.get("severity")))
                ? "当前资产存在阻塞问题，建议先修复后再进入后续链路。"
                : "当前资产未发现明显阻塞问题，但仍建议结合上下游资产做一致性复核。");
        content.put("issues", issues);
        content.put("suggestions", suggestions);
        content.put("consistencyChecks", List.of());
        content.put("nextActions", List.of("必要时重新生成当前资产", "打开质量检查", "对照附件补充遗漏内容"));
        return content;
    }

    private String formatReviewReply(
            Map<String, Object> content,
            String targetArtifactType,
            Map<String, Object> targetArtifact
    ) {
        String title = String.valueOf(targetArtifact.getOrDefault("title", targetArtifactType));
        String riskLevel = stringValue(content.get("riskLevel"), "MEDIUM");
        String conclusion = stringValue(content.get("conclusion"), "已完成资产审查。");
        String summary = stringValue(content.get("summary"), "");

        StringBuilder builder = new StringBuilder();
        builder.append("已完成“")
                .append(title)
                .append("”资产审查。")
                .append("\n\n")
                .append("结论：")
                .append(conclusion)
                .append("\n")
                .append("风险等级：")
                .append(toRiskText(riskLevel));

        if (!summary.isBlank()) {
            builder.append("\n\n概要：")
                    .append(summary);
        }

        appendIssueSection(builder, "主要问题", content.get("issues"));
        appendIssueSection(builder, "一致性检查", content.get("consistencyChecks"));
        appendIssueSection(builder, "修改建议", content.get("suggestions"));
        appendTextListSection(builder, "建议下一步", content.get("nextActions"));

        return builder.toString();
    }

    private void appendIssueSection(
            StringBuilder builder,
            String title,
            Object rawItems
    ) {
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            return;
        }

        builder.append("\n\n")
                .append(title)
                .append("：");

        int index = 1;
        for (Object rawItem : items) {
            Map<String, Object> item = toStringObjectMap(rawItem);

            if (item.isEmpty()) {
                builder.append("\n")
                        .append(index++)
                        .append(". ")
                        .append(String.valueOf(rawItem));
                continue;
            }

            String severity = stringValue(item.get("severity"), stringValue(item.get("level"), ""));
            String itemTitle = stringValue(item.get("title"), stringValue(item.get("dimension"), "问题"));
            String description = stringValue(item.get("description"), stringValue(item.get("result"), ""));
            String suggestion = stringValue(item.get("suggestion"), stringValue(item.get("recommendation"), ""));

            builder.append("\n")
                    .append(index++)
                    .append(". ")
                    .append(severity.isBlank() ? "" : "【" + severity + "】")
                    .append(itemTitle);

            if (!description.isBlank()) {
                builder.append("：")
                        .append(description);
            }

            if (!suggestion.isBlank()) {
                builder.append(" 建议：")
                        .append(suggestion);
            }
        }
    }

    private void appendTextListSection(
            StringBuilder builder,
            String title,
            Object rawItems
    ) {
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            return;
        }

        builder.append("\n\n")
                .append(title)
                .append("：");

        int index = 1;
        for (Object item : items) {
            builder.append("\n")
                    .append(index++)
                    .append(". ")
                    .append(String.valueOf(item));
        }
    }

    private List<AssistantActionResponse> buildReviewActions(
            UUID moduleId,
            String artifactType,
            Map<String, Object> targetArtifact
    ) {
        Map<String, Object> artifactPayload = new LinkedHashMap<>();
        artifactPayload.put("moduleId", moduleId.toString());
        artifactPayload.put("artifactType", artifactType);
        artifactPayload.put("artifactId", targetArtifact.get("artifactId"));

        return List.of(
                AssistantActionResponse.of(
                        "OPEN_ARTIFACT",
                        "查看资产",
                        artifactPayload
                ),
                AssistantActionResponse.of(
                        "REGENERATE_ARTIFACT",
                        "重新生成该资产",
                        artifactPayload
                ),
                AssistantActionResponse.of(
                        "OPEN_QUALITY_CHECK",
                        "打开质量检查",
                        artifactPayload
                )
        );
    }

    private AssistantMessageResponse response(
            String reply,
            List<AssistantActionResponse> actions
    ) {
        AssistantMessageResponse response = new AssistantMessageResponse();
        response.setIntent(AssistantIntentRouterService.INTENT_EXPLAIN_CURRENT_ARTIFACT);
        response.setReply(reply);
        response.setRequiresUserConfirmation(false);
        response.setActions(actions == null ? List.of() : actions);
        return response;
    }

    private Map<String, Object> issue(
            String code,
            String severity,
            String title,
            String description,
            String suggestion,
            String artifactType
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("severity", severity);
        item.put("title", title);
        item.put("description", description);
        item.put("suggestion", suggestion);
        item.put("artifactType", artifactType);
        return item;
    }

    private Map<String, Object> suggestion(
            String code,
            String title,
            String description
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("title", title);
        item.put("description", description);
        item.put("severity", "INFO");
        return item;
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return keywords.stream().anyMatch(text::contains);
    }

    private String truncate(
            String text,
            int maxLength
    ) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }

        return text.substring(0, maxLength) + "\n...【内容已截断】";
    }

    private String toLocalDateTimeString(
            Timestamp timestamp
    ) {
        if (timestamp == null) {
            return null;
        }

        LocalDateTime value = timestamp.toLocalDateTime();
        return value == null ? null : value.toString();
    }

    private Map<String, Object> parseJsonMap(
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
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> toStringObjectMap(
            Object value
    ) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        return result;
    }

    private String stringValue(
            Object value,
            String fallback
    ) {
        if (value == null) {
            return fallback;
        }

        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private String toRiskText(
            String riskLevel
    ) {
        String normalized = riskLevel == null ? "" : riskLevel.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "LOW" -> "低";
            case "MEDIUM" -> "中";
            case "HIGH" -> "高";
            case "BLOCKER" -> "阻塞";
            default -> riskLevel;
        };
    }
}
