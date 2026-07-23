package com.codemind.platform.assistant;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.module.ModuleTreeBaselineSupport;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectAccessService.ProjectAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigDecimal;

@Service
public class AssistantAttachmentArtifactDiffService {

    private static final String INTENT_ANALYZE_ATTACHMENT = "ANALYZE_ATTACHMENT";
    private static final String ACTION_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL =
            "ATTACHMENT_ARTIFACT_DIFF_PROPOSAL";

    private static final Pattern MEASURED_VALUE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(毫秒|秒|分钟|小时|天|次|个|条|页|%|％|元|kb|mb|gb)",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> VALUE_CONFLICT_SIGNALS = List.of(
            "验证码",
            "有效期",
            "超时",
            "过期",
            "重试",
            "次数",
            "限制",
            "上限",
            "下限",
            "分页",
            "每页",
            "金额",
            "价格",
            "费用",
            "密码",
            "锁定",
            "文件",
            "大小",
            "容量",
            "数量",
            "时长",
            "间隔"
    );

    private final ProjectAccessService projectAccessService;
    private final ProjectModuleRepository projectModuleRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ObjectMapper objectMapper;

    public AssistantAttachmentArtifactDiffService(
            ProjectAccessService projectAccessService,
            ProjectModuleRepository projectModuleRepository,
            ArtifactVersionRepository artifactVersionRepository,
            ObjectMapper objectMapper
    ) {
        this.projectAccessService = projectAccessService;
        this.projectModuleRepository = projectModuleRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AssistantMessageResponse buildDiffResponse(
            UUID workspaceId,
            String projectCode,
            AssistantMessageRequest request,
            String instruction,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (request == null || request.getSelectedNodeId() == null) {
            throw new IllegalArgumentException("请先选择一个模块树节点，再对比附件与当前资产差异。");
        }

        String artifactType = normalizeArtifactType(request.getSelectedArtifactType());

        if (artifactType.isBlank()) {
            throw new IllegalArgumentException("请先在资产区选择一个需求资产，再对比附件与当前资产差异。");
        }

        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            throw new IllegalArgumentException("请先上传附件，再执行附件与当前资产差异对比。");
        }

        ProjectAccess project = projectAccessService.resolveAccessibleProject(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = resolveModule(
                project.projectId(),
                request.getSelectedNodeId()
        );

        ArtifactVersionEntity currentArtifact = loadCurrentArtifact(
                project.projectId(),
                module.getId(),
                artifactType
        );

        ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline =
                captureModuleTreeBaseline(project.projectId());

        String currentMarkdown = firstNonBlank(
                currentArtifact.getContentMarkdown(),
                safeJson(currentArtifact.getContent()),
                ""
        );

        String attachmentText = buildAttachmentText(attachmentContexts);
        Map<String, Object> proposal = buildDiffProposal(
                instruction,
                project,
                module,
                currentArtifact,
                artifactType,
                currentMarkdown,
                attachmentText,
                attachmentContexts,
                moduleTreeBaseline
        );

        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("proposal", proposal);

        AssistantMessageResponse response = new AssistantMessageResponse();
        response.setIntent(INTENT_ANALYZE_ATTACHMENT);
        response.setReply(String.valueOf(proposal.get("summary")));
        response.setRequiresUserConfirmation(true);
        response.setAttachmentArtifactDiffProposal(proposal);
        response.setActions(List.of(
                AssistantActionResponse.of(
                        ACTION_ATTACHMENT_ARTIFACT_DIFF_PROPOSAL,
                        "查看附件与当前资产差异分析",
                        actionPayload
                )
        ));

        return response;
    }

    private Map<String, Object> buildDiffProposal(
            String instruction,
            ProjectAccess project,
            ProjectModuleEntity module,
            ArtifactVersionEntity currentArtifact,
            String artifactType,
            String currentMarkdown,
            String attachmentText,
            List<AssistantAttachmentParsedContext> attachmentContexts,
            ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline
    ) {
        List<String> attachmentRequirements = extractAttachmentRequirementItems(
                attachmentText
        );

        List<Map<String, Object>> diffItems = new ArrayList<>();
        List<String> missingRequirements = new ArrayList<>();
        List<String> possibleConflicts = new ArrayList<>();
        List<String> alreadyCoveredItems = new ArrayList<>();

        int sequence = 1;
        for (String item : attachmentRequirements) {
            CoverageResult coverage = evaluateCoverage(
                    currentMarkdown,
                    item
            );

            ValueConflictResult valueConflict = evaluateValueConflict(
                    item,
                    currentMarkdown
            );
            boolean conflict = looksLikeConflict(item, currentMarkdown)
                    || valueConflict.conflict();

            String type;
            String severity;
            String recommendation;

            if (conflict) {
                type = "POTENTIAL_CONFLICT";
                severity = "WARNING";
                recommendation = "附件表述可能与当前资产已有约束存在方向差异，建议人工核对后再决定是否修订。";
                possibleConflicts.add(item);
            } else if (coverage.covered()) {
                type = "ALREADY_COVERED";
                severity = "INFO";
                recommendation = "当前资产中已发现相近描述，建议只做人工确认，不要重复写入。";
                alreadyCoveredItems.add(item);
            } else {
                type = "MISSING";
                severity = "WARNING";
                recommendation = "当前资产未发现明确覆盖内容，建议进入资产局部修订提案。";
                missingRequirements.add(item);
            }

            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("id", "diff-" + sequence);
            diff.put("type", type);
            diff.put("title", buildDiffTitle(item));
            diff.put("severity", severity);
            diff.put("attachmentExcerpt", trim(item, 520));
            diff.put(
                    "currentEvidence",
                    firstNonBlank(valueConflict.evidence(), coverage.evidence())
            );
            diff.put("recommendation", recommendation);
            diff.put("targetArtifactType", artifactType);
            diff.put("canAutoPatch", "MISSING".equals(type));
            diffItems.add(diff);

            sequence++;
        }

        if (diffItems.isEmpty()) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("id", "diff-1");
            diff.put("type", "NEEDS_REVIEW");
            diff.put("title", "附件没有可稳定抽取的需求条目");
            diff.put("severity", "INFO");
            diff.put("attachmentExcerpt", trim(attachmentText, 520));
            diff.put("currentEvidence", trim(currentMarkdown, 520));
            diff.put("recommendation", "建议先补充说明附件中的关键字段、页面、流程、规则或验收点，再生成局部修订提案。");
            diff.put("targetArtifactType", artifactType);
            diff.put("canAutoPatch", false);
            diffItems.add(diff);
        }

        Map<String, Object> targetNode = new LinkedHashMap<>();
        targetNode.put("nodeId", module.getId().toString());
        targetNode.put("nodeName", module.getModuleName());
        targetNode.put("nodeType", module.getModuleType());
        targetNode.put("moduleKey", module.getModuleKey());
        targetNode.put("modulePath", module.getModulePath());

        Map<String, Object> targetArtifact = new LinkedHashMap<>();
        targetArtifact.put("artifactId", currentArtifact.getId().toString());
        targetArtifact.put("artifactType", artifactType);
        targetArtifact.put("artifactTitle", currentArtifact.getTitle());
        targetArtifact.put("versionNo", currentArtifact.getVersionNo());
        targetArtifact.put("status", currentArtifact.getStatus());
        targetArtifact.put("checksum", currentArtifact.getChecksum());

        String summary = buildSummary(
                artifactType,
                attachmentContexts.size(),
                missingRequirements.size(),
                possibleConflicts.size(),
                alreadyCoveredItems.size()
        );

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("proposalId", UUID.randomUUID().toString());
        proposal.put("source", "ATTACHMENT_ARTIFACT_DIFF");
        proposal.put("status", "DRAFT");
        proposal.put("phase", "Phase 7-D");
        proposal.put("originalInstruction", firstNonBlank(instruction, ""));
        proposal.put("summary", summary);
        proposal.put("targetNode", targetNode);
        proposal.put("targetArtifact", targetArtifact);
        proposal.put("targetArtifactType", artifactType);
        proposal.put("baseArtifactId", currentArtifact.getId().toString());
        proposal.put("baseVersionNo", currentArtifact.getVersionNo());
        proposal.put("baseChecksum", currentArtifact.getChecksum());
        proposal.put("baseModuleTreeHash", moduleTreeBaseline.hash());
        proposal.put("baseModuleTreeNodeCount", moduleTreeBaseline.nodeCount());
        proposal.put("baseModuleTreeSummary", moduleTreeBaseline.summary());
        proposal.put("baselineValidationPolicy", "BLOCK_IF_CURRENT_ARTIFACT_OR_MODULE_TREE_CHANGED");
        proposal.put("currentArtifactExcerpt", trim(currentMarkdown, 1200));
        proposal.put("attachmentExcerpt", trim(attachmentText, 1200));
        proposal.put("attachmentSummary", buildAttachmentSummary(attachmentContexts));
        proposal.put("diffItems", diffItems);
        proposal.put("missingRequirements", limitStrings(missingRequirements, 12));
        proposal.put("possibleConflicts", limitStrings(possibleConflicts, 12));
        proposal.put("alreadyCoveredItems", limitStrings(alreadyCoveredItems, 12));
        proposal.put("affectedArtifacts", List.of(artifactType));
        proposal.put("riskNotes", buildRiskNotes(artifactType, attachmentContexts));
        proposal.put("recommendedSteps", buildRecommendedSteps(artifactType));
        proposal.put("candidateActions", buildCandidateActions(artifactType, missingRequirements));
        proposal.put("requiresUserConfirmation", true);
        proposal.put("confidence", estimateConfidence(diffItems, attachmentText, currentMarkdown));
        proposal.put("projectId", project.projectId().toString());
        proposal.put("projectCode", project.projectCode());

        return proposal;
    }

    private ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot captureModuleTreeBaseline(
            UUID projectId
    ) {
        return ModuleTreeBaselineSupport.capture(
                projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
        );
    }

    private ArtifactVersionEntity loadCurrentArtifact(
            UUID projectId,
            UUID moduleId,
            String artifactType
    ) {
        return artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        artifactType
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "当前节点没有可对比的 " + artifactType + " 资产。请先生成或选择该资产。"
                ));
    }

    private ProjectModuleEntity resolveModule(
            UUID projectId,
            UUID moduleId
    ) {
        ProjectModuleEntity module = projectModuleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("模块不存在：" + moduleId));

        if (!Objects.equals(projectId, module.getProjectId())) {
            throw new IllegalArgumentException("模块不属于当前项目：" + moduleId);
        }

        return module;
    }

    private List<Map<String, Object>> buildCandidateActions(
            String artifactType,
            List<String> missingRequirements
    ) {
        List<Map<String, Object>> actions = new ArrayList<>();

        actions.add(candidateAction(
                "ASSET_EDIT_PROPOSAL",
                "基于差异生成资产局部修订提案",
                "只基于已选择的差异生成资产变更 Agent 的 Patch-first 局部修订提案，不会直接覆盖资产。",
                artifactType
        ));

        actions.add(candidateAction(
                "MODULE_TREE_PATCH",
                "生成模块树变更提案",
                "只基于已选择的结构性差异进入模块树 Patch 提案，不会直接修改模块树。",
                null
        ));

        actions.add(candidateAction(
                "ANALYSIS_ONLY",
                "仅保留差异摘要",
                "只保留对比结论，不修改模块树和需求资产。",
                null
        ));

        return actions;
    }

    private Map<String, Object> candidateAction(
            String type,
            String label,
            String description,
            String artifactType
    ) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", type);
        action.put("label", label);
        action.put("description", description);
        action.put("targetArtifactType", artifactType);
        action.put("expectedIntent", "MODULE_TREE_PATCH".equals(type)
                ? "PROPOSE_MODULE_TREE_PATCH"
                : "ANALYZE_ATTACHMENT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("artifactType", artifactType);
        action.put("payload", payload);

        return action;
    }

    private String buildSummary(
            String artifactType,
            int attachmentCount,
            int missingCount,
            int conflictCount,
            int coveredCount
    ) {
        return "已将 " + attachmentCount + " 个附件材料与当前 " + artifactType +
                " 进行对比：发现 " + missingCount + " 项当前资产可能缺失，" +
                conflictCount + " 项可能冲突，" + coveredCount + " 项已被当前资产覆盖。";
    }

    private List<String> buildRiskNotes(
            String artifactType,
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        List<String> risks = new ArrayList<>();
        risks.add("本次差异分析不会直接修改 " + artifactType + "，只输出可审查的差异清单。");
        risks.add("规则对比只能判断文本相似度，不能替代人工确认客户真实意图。");
        risks.add("后续生成资产局部修订提案时，应优先 Patch 缺失项，避免整份重写。");

        if (hasImageAttachment(attachmentContexts)) {
            risks.add("图片/原型图类附件当前仍主要依赖元数据和用户描述；未接入正式视觉理解模型时，不得编造图片中的字段、按钮和布局。");
        }

        return risks;
    }

    private List<String> buildRecommendedSteps(
            String artifactType
    ) {
        return List.of(
                "先查看差异清单，确认哪些差异是真正需要进入当前 " + artifactType + " 的需求。",
                "点击“基于差异生成资产局部修订提案”，让资产变更 Agent 生成局部 Patch。",
                "确认资产编辑提案后保存为新版本；下游资产再通过影响分析选择性同步。"
        );
    }

    private Map<String, Object> buildAttachmentSummary(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", attachmentContexts == null ? 0 : attachmentContexts.size());

        List<Map<String, Object>> items = new ArrayList<>();
        if (attachmentContexts != null) {
            for (AssistantAttachmentParsedContext context : attachmentContexts) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("attachmentId", context.getAttachmentId() == null ? null : context.getAttachmentId().toString());
                item.put("fileName", context.getFileName());
                item.put("mimeType", context.getMimeType());
                item.put("size", context.getSize());
                item.put("parseStatus", context.getParseStatus());
                item.put("parsedTextExcerpt", context.getParsedTextExcerpt());
                item.put("metadata", context.getMetadata() == null ? Map.of() : context.getMetadata());
                items.add(item);
            }
        }
        summary.put("items", items);

        return summary;
    }

    private String buildAttachmentText(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts == null || attachmentContexts.isEmpty()) {
            return "";
        }

        List<String> sections = new ArrayList<>();

        for (AssistantAttachmentParsedContext context : attachmentContexts) {
            String excerpt = firstNonBlank(
                    context.getParsedTextExcerpt(),
                    ""
            );

            if (excerpt.isBlank()) {
                excerpt = "附件暂未提供可用文本摘录。文件名：" + firstNonBlank(context.getFileName(), "未命名附件");
            }

            sections.add(
                    "附件：" + firstNonBlank(context.getFileName(), "未命名附件") +
                            "\nMIME：" + firstNonBlank(context.getMimeType(), "") +
                            "\n解析状态：" + firstNonBlank(context.getParseStatus(), "") +
                            "\n内容：\n" + excerpt
            );
        }

        return String.join("\n\n", sections);
    }

    private List<String> extractAttachmentRequirementItems(
            String attachmentText
    ) {
        String normalizedText = firstNonBlank(attachmentText, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        List<String> candidates = new ArrayList<>();

        for (String line : normalizedText.split("\n")) {
            String item = cleanRequirementLine(line);

            if (item.isBlank() || item.length() < 4) {
                continue;
            }

            if (looksLikeRequirementLine(item)) {
                addUnique(candidates, item);
            }
        }

        if (candidates.isEmpty()) {
            for (String part : normalizedText.split("[。；;.!?？]")) {
                String item = cleanRequirementLine(part);

                if (!item.isBlank() && item.length() >= 6) {
                    addUnique(candidates, item);
                }
            }
        }

        return limitStrings(candidates, 24);
    }

    private boolean looksLikeRequirementLine(
            String item
    ) {
        String normalized = item.toLowerCase(Locale.ROOT);

        return containsAny(
                normalized,
                List.of(
                        "新增",
                        "增加",
                        "添加",
                        "补充",
                        "修改",
                        "调整",
                        "优化",
                        "删除",
                        "移除",
                        "取消",
                        "支持",
                        "需要",
                        "必须",
                        "应当",
                        "可以",
                        "字段",
                        "按钮",
                        "页面",
                        "功能",
                        "接口",
                        "流程",
                        "权限",
                        "角色",
                        "状态",
                        "验证码",
                        "审核",
                        "导出",
                        "导入",
                        "查询",
                        "上传",
                        "下载",
                        "列表",
                        "详情",
                        "表单",
                        "校验",
                        "通知",
                        "短信",
                        "邮件",
                        "api",
                        "excel"
                )
        );
    }

    private CoverageResult evaluateCoverage(
            String currentMarkdown,
            String requirement
    ) {
        String normalizedCurrent = normalizeComparableText(currentMarkdown);
        String normalizedRequirement = normalizeComparableText(requirement);

        if (!normalizedRequirement.isBlank() && normalizedCurrent.contains(normalizedRequirement)) {
            return new CoverageResult(true, "当前资产中存在高度相似的完整表述。");
        }

        List<String> keywords = extractKeywords(requirement);
        int hit = 0;
        String firstEvidence = "";

        for (String keyword : keywords) {
            String normalizedKeyword = normalizeComparableText(keyword);

            if (normalizedKeyword.isBlank()) {
                continue;
            }

            if (normalizedCurrent.contains(normalizedKeyword)) {
                hit++;
                if (firstEvidence.isBlank()) {
                    firstEvidence = findEvidence(currentMarkdown, keyword);
                }
            }
        }

        boolean covered = !keywords.isEmpty() && hit >= Math.max(2, Math.min(3, keywords.size()));

        return new CoverageResult(
                covered,
                firstEvidence.isBlank()
                        ? "当前资产未发现稳定匹配片段。"
                        : firstEvidence
        );
    }

    private List<String> extractKeywords(
            String requirement
    ) {
        String cleaned = cleanRequirementLine(requirement);
        List<String> result = new ArrayList<>();

        for (String token : cleaned.split("[\\s,，、:：;；/|（）()【】\\[\\]{}<>《》\"'“”]+")) {
            String item = token.trim();

            if (item.length() < 2) {
                continue;
            }

            if (isStopWord(item)) {
                continue;
            }

            addUnique(result, item);
        }

        if (result.isEmpty() && cleaned.length() >= 4) {
            addUnique(result, cleaned.length() > 18 ? cleaned.substring(0, 18) : cleaned);
        }

        return limitStrings(result, 8);
    }

    private ValueConflictResult evaluateValueConflict(
            String requirement,
            String currentMarkdown
    ) {
        if (requirement == null || requirement.isBlank()
                || currentMarkdown == null || currentMarkdown.isBlank()) {
            return ValueConflictResult.none();
        }

        List<MeasuredValue> requirementValues = extractMeasuredValues(requirement);

        if (requirementValues.isEmpty()) {
            return ValueConflictResult.none();
        }

        Matcher currentMatcher = MEASURED_VALUE_PATTERN.matcher(currentMarkdown);

        while (currentMatcher.find()) {
            MeasuredValue currentValue = new MeasuredValue(
                    currentMatcher.group(1),
                    normalizeMeasuredUnit(currentMatcher.group(2))
            );

            for (MeasuredValue requirementValue : requirementValues) {
                if (!requirementValue.unit().equals(currentValue.unit())) {
                    continue;
                }

                if (sameNumericValue(requirementValue.value(), currentValue.value())) {
                    continue;
                }

                String evidence = currentMarkdown.substring(
                        Math.max(0, currentMatcher.start() - 120),
                        Math.min(currentMarkdown.length(), currentMatcher.end() + 180)
                );

                if (sharesValueConflictSignal(requirement, evidence)) {
                    return new ValueConflictResult(
                            true,
                            trim(evidence, 420)
                    );
                }
            }
        }

        return ValueConflictResult.none();
    }

    private List<MeasuredValue> extractMeasuredValues(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<MeasuredValue> values = new ArrayList<>();
        Matcher matcher = MEASURED_VALUE_PATTERN.matcher(text);

        while (matcher.find()) {
            MeasuredValue value = new MeasuredValue(
                    matcher.group(1),
                    normalizeMeasuredUnit(matcher.group(2))
            );

            if (!values.contains(value)) {
                values.add(value);
            }
        }

        return values;
    }

    private String normalizeMeasuredUnit(
            String unit
    ) {
        String normalized = firstNonBlank(unit, "")
                .trim()
                .toLowerCase(Locale.ROOT);

        return "％".equals(normalized) ? "%" : normalized;
    }

    private boolean sameNumericValue(
            String left,
            String right
    ) {
        try {
            return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0;
        } catch (NumberFormatException ex) {
            return Objects.equals(left, right);
        }
    }

    private boolean sharesValueConflictSignal(
            String requirement,
            String evidence
    ) {
        String normalizedRequirement = firstNonBlank(requirement, "")
                .toLowerCase(Locale.ROOT);
        String normalizedEvidence = firstNonBlank(evidence, "")
                .toLowerCase(Locale.ROOT);

        return VALUE_CONFLICT_SIGNALS.stream().anyMatch(signal ->
                normalizedRequirement.contains(signal)
                        && normalizedEvidence.contains(signal)
        );
    }

    private boolean looksLikeConflict(
            String item,
            String currentMarkdown
    ) {
        String normalized = item.toLowerCase(Locale.ROOT);
        boolean negative = containsAny(
                normalized,
                List.of("删除", "移除", "取消", "不允许", "不能", "禁止", "无需", "不需要")
        );

        if (!negative) {
            return false;
        }

        List<String> keywords = extractKeywords(item);
        CoverageResult coverage = evaluateCoverage(currentMarkdown, item);

        return coverage.covered() || keywords.stream().anyMatch(keyword ->
                normalizeComparableText(currentMarkdown).contains(normalizeComparableText(keyword))
        );
    }

    private String findEvidence(
            String currentMarkdown,
            String keyword
    ) {
        if (currentMarkdown == null || currentMarkdown.isBlank()) {
            return "";
        }

        String lower = currentMarkdown.toLowerCase(Locale.ROOT);
        String keywordLower = keyword.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(keywordLower);

        if (index < 0) {
            return "";
        }

        int start = Math.max(0, index - 100);
        int end = Math.min(currentMarkdown.length(), index + keyword.length() + 160);

        return trim(currentMarkdown.substring(start, end), 420);
    }

    private boolean containsPageOrFeatureSignal(
            List<String> items
    ) {
        if (items == null || items.isEmpty()) {
            return false;
        }

        return items.stream().anyMatch(item ->
                containsAny(
                        item,
                        List.of(
                                "新增页面",
                                "增加页面",
                                "页面",
                                "功能点",
                                "菜单",
                                "路由",
                                "入口",
                                "按钮",
                                "勾选项",
                                "字段",
                                "表单",
                                "校验",
                                "流程"
                        )
                )
        );
    }

    private boolean hasImageAttachment(
            List<AssistantAttachmentParsedContext> attachmentContexts
    ) {
        if (attachmentContexts == null) {
            return false;
        }

        return attachmentContexts.stream().anyMatch(context ->
                context.getMimeType() != null &&
                        context.getMimeType().toLowerCase(Locale.ROOT).startsWith("image/")
        );
    }

    private double estimateConfidence(
            List<Map<String, Object>> diffItems,
            String attachmentText,
            String currentMarkdown
    ) {
        if (attachmentText == null || attachmentText.isBlank() ||
                currentMarkdown == null || currentMarkdown.isBlank()) {
            return 0.35;
        }

        long stableItems = diffItems.stream()
                .filter(item -> !"NEEDS_REVIEW".equals(item.get("type")))
                .count();

        if (stableItems >= 5) {
            return 0.72;
        }

        if (stableItems >= 2) {
            return 0.62;
        }

        return 0.48;
    }

    private String buildDiffTitle(
            String item
    ) {
        String text = cleanRequirementLine(item);
        if (text.length() <= 36) {
            return text;
        }
        return text.substring(0, 36) + "...";
    }

    private String cleanRequirementLine(
            String line
    ) {
        return firstNonBlank(line, "")
                .replaceAll("^[-*•·\\d.、\\)）\\s]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeArtifactType(
            String artifactType
    ) {
        return firstNonBlank(artifactType, "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeComparableText(
            String value
    ) {
        return firstNonBlank(value, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）【】《》“”‘’！？]", "");
    }

    private boolean isStopWord(
            String value
    ) {
        return List.of(
                "附件",
                "内容",
                "需求",
                "当前",
                "资产",
                "页面",
                "功能",
                "系统",
                "用户",
                "需要",
                "可以",
                "支持",
                "进行",
                "实现"
        ).contains(value);
    }

    private boolean containsAny(
            String text,
            List<String> keywords
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private void addUnique(
            List<String> items,
            String value
    ) {
        String item = firstNonBlank(value, "").trim();
        if (item.isBlank()) {
            return;
        }

        if (!items.contains(item)) {
            items.add(item);
        }
    }

    private List<String> limitStrings(
            List<String> values,
            int limit
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    private String trim(
            String value,
            int limit
    ) {
        String text = firstNonBlank(value, "").trim();

        if (text.length() <= limit) {
            return text;
        }

        return text.substring(0, limit) + "...";
    }

    private String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String safeJson(
            Object value
    ) {
        if (value == null) {
            return "";
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private record MeasuredValue(
            String value,
            String unit
    ) {
    }

    private record ValueConflictResult(
            boolean conflict,
            String evidence
    ) {
        private static ValueConflictResult none() {
            return new ValueConflictResult(false, "");
        }
    }

    private record CoverageResult(
            boolean covered,
            String evidence
    ) {
    }
}
