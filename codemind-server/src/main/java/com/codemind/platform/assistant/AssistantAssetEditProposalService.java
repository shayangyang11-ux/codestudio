package com.codemind.platform.assistant;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.agent.common.client.PythonAgentClient;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectAccessService.ProjectAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AssistantAssetEditProposalService {

    private static final String INTENT_PROPOSE_ASSET_EDIT = "PROPOSE_ASSET_EDIT";
    private static final String ACTION_ASSET_EDIT_PROPOSAL = "ASSET_EDIT_PROPOSAL";

    private static final List<String> EDITABLE_ARTIFACTS = List.of(
            "MODULE_PRD",
            "BUSINESS_FLOW",
            "PAGE_SPEC",
            "TEST_SPEC"
    );

    private static final List<String> STRUCTURED_EDIT_ARTIFACTS = List.of(
            "DATA_MODEL",
            "API_CONTRACT"
    );

    private final ProjectAccessService projectAccessService;
    private final ProjectModuleRepository projectModuleRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final PythonAgentClient pythonAgentClient;

    public AssistantAssetEditProposalService(
            ProjectAccessService projectAccessService,
            ProjectModuleRepository projectModuleRepository,
            ArtifactVersionRepository artifactVersionRepository,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper,
            PythonAgentClient pythonAgentClient
    ) {
        this.projectAccessService = projectAccessService;
        this.projectModuleRepository = projectModuleRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.pythonAgentClient = pythonAgentClient;
    }

    @Transactional(readOnly = true)
    public AssistantMessageResponse propose(
            UUID workspaceId,
            String projectCode,
            AssistantAssetEditProposalRequest request
    ) {
        ProjectAccess project = projectAccessService.resolveAccessibleProject(
                workspaceId,
                projectCode
        );

        Map<String, Object> requirementChangeProposal = buildRequirementChangeProposalWithSelection(
                request
        );

        UUID moduleId = resolveModuleId(
                request == null ? null : request.getSelectedNodeId(),
                requirementChangeProposal
        );

        ProjectModuleEntity module = moduleId == null
                ? null
                : resolveModule(project.projectId(), moduleId);

        String instruction = firstNonBlank(
                request == null ? null : request.getInstruction(),
                textValue(deepGet(
                        requirementChangeProposal,
                        "originalInstruction"
                )),
                textValue(deepGet(
                        requirementChangeProposal,
                        "summary"
                )),
                "请根据确认后的需求变更更新当前资产。"
        );

        String targetArtifactType = resolveTargetArtifactType(request);

        ArtifactVersionEntity currentArtifact = loadCurrentArtifact(
                project.projectId(),
                moduleId,
                targetArtifactType
        );

        validateCurrentArtifactBaseline(
                requirementChangeProposal,
                currentArtifact,
                "当前资产已经发生变化，请重新执行附件差异分析后再生成资产局部修订提案。"
        );

        Map<String, Object> proposal = buildAssetEditProposal(
                requirementChangeProposal,
                instruction,
                project,
                module,
                currentArtifact,
                targetArtifactType
        );

        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("proposal", proposal);

        AssistantMessageResponse response = new AssistantMessageResponse();
        response.setMessageId(UUID.randomUUID());
        response.setIntent(INTENT_PROPOSE_ASSET_EDIT);
        response.setRequiresUserConfirmation(true);
        response.setReply("已根据确认后的需求变更生成资产局部修订提案。请检查目标资产、拟局部修改内容和影响范围，确认后系统会保存一个新的待确认版本快照，旧版本会保留。");
        response.setActions(List.of(
                AssistantActionResponse.of(
                        ACTION_ASSET_EDIT_PROPOSAL,
                        "查看资产编辑提案",
                        actionPayload
                )
        ));

        return response;
    }

    @Transactional
    public ConfirmAssistantAssetEditProposalResponse confirm(
            UUID workspaceId,
            String projectCode,
            ConfirmAssistantAssetEditProposalRequest request
    ) {
        if (request == null || request.getProposal() == null || request.getProposal().isEmpty()) {
            throw new IllegalArgumentException("缺少资产编辑提案，无法保存局部修订版本。");
        }

        ProjectAccess project = projectAccessService.resolveAccessibleProject(
                workspaceId,
                projectCode
        );

        Map<String, Object> proposal = request.getProposal();
        String artifactType = firstNonBlank(
                textValue(proposal.get("targetArtifactType")),
                textValue(deepGet(proposal, "targetArtifact", "artifactType"))
        );

        if (!isSupportedTargetArtifact(artifactType)) {
            throw new IllegalArgumentException("当前资产类型暂不支持直接保存局部修订版本：" + artifactType);
        }

        UUID moduleId = parseUuid(firstNonBlank(
                textValue(deepGet(proposal, "targetNode", "nodeId")),
                textValue(proposal.get("moduleId"))
        ));

        if (moduleId != null) {
            resolveModule(project.projectId(), moduleId);
        }

        ArtifactVersionEntity previous = loadCurrentArtifact(
                project.projectId(),
                moduleId,
                artifactType
        );

        validateCurrentArtifactBaseline(
                proposal,
                previous,
                "当前资产已经发生变化，请重新执行附件差异分析后再生成新的资产局部修订提案。"
        );

        String proposedContentMarkdown = firstNonBlank(
                textValue(proposal.get("proposedContentMarkdown")),
                previous.getContentMarkdown(),
                ""
        );

        Map<String, Object> newContent = copyContent(previous.getContent());
        appendAssistantEditMetadata(
                newContent,
                proposal,
                request.getConfirmationNote(),
                previous
        );

        int nextVersionNo = nextVersionNo(
                project.projectId(),
                moduleId,
                artifactType
        );

        if (moduleId == null) {
            artifactVersionRepository.markPreviousProjectArtifactsAsNotCurrent(
                    project.projectId(),
                    artifactType
            );
        } else {
            artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                    project.projectId(),
                    moduleId,
                    artifactType
            );
        }

        ArtifactVersionEntity next = new ArtifactVersionEntity();
        next.setProjectId(project.projectId());
        next.setModuleId(moduleId);
        next.setTaskId(null);
        next.setArtifactType(artifactType);
        next.setVersionNo(nextVersionNo);
        next.setTitle(firstNonBlank(
                previous.getTitle(),
                artifactType + "（AI 编辑版）"
        ));
        next.setContent(newContent);
        next.setContentMarkdown(proposedContentMarkdown);
        next.setStatus("PENDING_REVIEW");
        next.setCurrent(true);
        next.setChecksum(sha256Hex(artifactType + "\n" + proposedContentMarkdown + "\n" + safeJson(newContent)));
        next.setCreatedBy(currentUserProvider.getCurrentUser().username());
        next.setPromptTemplateId(previous.getPromptTemplateId());
        next.setPromptTemplateVersionId(previous.getPromptTemplateVersionId());
        next.setPromptTemplateKey(previous.getPromptTemplateKey());
        next.setPromptTemplateVersion(previous.getPromptTemplateVersion());
        next.setPromptTemplateChecksum(previous.getPromptTemplateChecksum());

        ArtifactVersionEntity saved = artifactVersionRepository.saveAndFlush(next);

        List<String> downstreamArtifacts = stringList(proposal.get("downstreamArtifacts"));
        if (downstreamArtifacts.isEmpty()) {
            downstreamArtifacts = defaultDownstreamArtifacts(artifactType);
        }

        int marked = 0;
        if (moduleId != null && !downstreamArtifacts.isEmpty()) {
            marked = artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                    project.projectId(),
                    List.of(moduleId),
                    downstreamArtifacts
            );
        }

        ConfirmAssistantAssetEditProposalResponse response = new ConfirmAssistantAssetEditProposalResponse();
        response.setArtifactId(saved.getId());
        response.setProjectId(saved.getProjectId());
        response.setModuleId(saved.getModuleId());
        response.setArtifactType(saved.getArtifactType());
        response.setVersionNo(saved.getVersionNo());
        response.setTitle(saved.getTitle());
        response.setStatus(saved.getStatus());
        response.setPreviousArtifactId(previous.getId());
        response.setPreviousVersionNo(previous.getVersionNo());
        response.setAffectedArtifacts(downstreamArtifacts);
        response.setDownstreamMarkedReviewCount(marked);
        response.setMessage("已保存 " + artifactType + " 局部修订版本 V" + saved.getVersionNo() + "。旧版本已保留，新版本进入“新版本待确认”；受影响下游资产已标记为“受上游影响”。");

        return response;
    }

    private Map<String, Object> buildAssetEditProposal(
            Map<String, Object> requirementChangeProposal,
            String instruction,
            ProjectAccess project,
            ProjectModuleEntity module,
            ArtifactVersionEntity currentArtifact,
            String targetArtifactType
    ) {
        String beforeMarkdown = firstNonBlank(
                currentArtifact.getContentMarkdown(),
                safeJson(currentArtifact.getContent())
        );

        Map<String, Object> agentResult = runAssetChangeAgent(
                requirementChangeProposal,
                instruction,
                project,
                module,
                currentArtifact,
                targetArtifactType,
                beforeMarkdown
        );

        List<Map<String, Object>> proposedPatchOperations = objectMapList(agentResult.get("proposedPatchOperations"));
        PatchApplicationResult patchApplication = applyPatchOperations(
                beforeMarkdown,
                proposedPatchOperations
        );

        String proposedMarkdown = firstNonBlank(
                patchApplication.contentMarkdown(),
                textValue(agentResult.get("proposedContentMarkdown")),
                buildFallbackProposedMarkdown(
                        beforeMarkdown,
                        instruction,
                        requirementChangeProposal,
                        targetArtifactType
                )
        );

        List<String> downstream = objectStringList(agentResult.get("downstreamArtifacts"));
        if (downstream.isEmpty()) {
            downstream = defaultDownstreamArtifacts(targetArtifactType);
        }

        List<String> proposedPatchSummary = objectStringList(agentResult.get("proposedPatchSummary"));
        if (proposedPatchSummary.isEmpty()) {
            proposedPatchSummary = buildPatchSummaryFromSelectedDiffItems(
                    objectMapList(requirementChangeProposal.get("selectedDiffItems")),
                    targetArtifactType
            );
        }
        if (proposedPatchSummary.isEmpty()) {
            proposedPatchSummary = buildPatchSummary(instruction, targetArtifactType);
        }

        List<String> agentRiskNotes = objectStringList(agentResult.get("riskNotes"));
        if (agentRiskNotes.isEmpty()) {
            agentRiskNotes = buildRiskNotes(targetArtifactType, downstream);
        }

        List<String> agentRecommendedSteps = objectStringList(agentResult.get("reviewChecklist"));
        if (agentRecommendedSteps.isEmpty()) {
            agentRecommendedSteps = buildRecommendedSteps(targetArtifactType, downstream);
        }

        Map<String, Object> targetNode = new LinkedHashMap<>();
        targetNode.put("nodeId", module == null ? null : module.getId().toString());
        targetNode.put("nodeName", module == null ? "当前项目" : module.getModuleName());
        targetNode.put("nodeType", module == null ? "PROJECT" : module.getModuleType());
        targetNode.put("moduleKey", module == null ? null : module.getModuleKey());
        targetNode.put("modulePath", module == null ? null : module.getModulePath());

        Map<String, Object> targetArtifact = new LinkedHashMap<>();
        targetArtifact.put("artifactId", currentArtifact.getId().toString());
        targetArtifact.put("artifactType", targetArtifactType);
        targetArtifact.put("artifactTitle", currentArtifact.getTitle());
        targetArtifact.put("versionNo", currentArtifact.getVersionNo());
        targetArtifact.put("status", currentArtifact.getStatus());
        targetArtifact.put("checksum", currentArtifact.getChecksum());

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("proposalId", UUID.randomUUID().toString());
        proposal.put("source", "ASSISTANT_ASSET_EDIT_PROPOSAL");
        proposal.put("status", "DRAFT");
        proposal.put("originalInstruction", instruction);
        proposal.put("summary", firstNonBlank(
                textValue(agentResult.get("summary")),
                "基于用户确认的需求变更，生成 " + targetArtifactType + " 的 Patch-first 局部修订提案。"
        ));
        proposal.put("editMode", STRUCTURED_EDIT_ARTIFACTS.contains(targetArtifactType)
                ? "STRUCTURED_ASSET_EDIT"
                : "DOCUMENT_ASSET_EDIT");
        proposal.put("editPolicy", "确认后应用局部 patch，并保存为新的 artifact_version 完整快照；旧版本保留，新版本进入 PENDING_REVIEW，受影响下游资产标记为 NEEDS_REVIEW（受上游影响）。");
        proposal.put("targetNode", targetNode);
        proposal.put("targetArtifact", targetArtifact);
        proposal.put("targetArtifactType", targetArtifactType);
        proposal.put("targetArtifactId", currentArtifact.getId().toString());
        proposal.put("currentVersionNo", currentArtifact.getVersionNo());
        proposal.put("currentChecksum", currentArtifact.getChecksum());
        proposal.put("baseArtifactId", currentArtifact.getId().toString());
        proposal.put("baseVersionNo", currentArtifact.getVersionNo());
        proposal.put("baseChecksum", currentArtifact.getChecksum());
        proposal.put("baselineValidationPolicy", "BLOCK_IF_CURRENT_ARTIFACT_CHANGED");
        proposal.put("currentTitle", currentArtifact.getTitle());
        proposal.put("beforeExcerpt", trimForSummary(beforeMarkdown, 520));
        proposal.put("proposedPatchOperations", proposedPatchOperations);
        proposal.put("patchApplicationMode", patchApplication.appliedCount() > 0
                ? "PATCH_FIRST_APPLIED_BY_JAVA"
                : "PATCH_FIRST_FALLBACK_FULL_MARKDOWN_OR_APPEND");
        proposal.put("patchApplicationSummary", patchApplication.summary());
        proposal.put("proposedContentMarkdown", proposedMarkdown);
        proposal.put("proposedPatchSummary", proposedPatchSummary);
        proposal.put("affectedArtifacts", stringList(requirementChangeProposal.get("affectedArtifacts")));
        proposal.put("downstreamArtifacts", downstream);
        proposal.put("riskNotes", agentRiskNotes);
        proposal.put("recommendedSteps", agentRecommendedSteps);
        proposal.put("requiresUserConfirmation", true);
        proposal.put("changedSections", objectList(agentResult.get("changedSections")));
        proposal.put("agentConfidence", agentResult.get("confidence"));
        proposal.put("agentSource", firstNonBlank(textValue(agentResult.get("source")), "AssistantArtifactReviewAgent"));
        proposal.put("createdAt", LocalDateTime.now().toString());
        proposal.put("projectId", project.projectId().toString());
        proposal.put("projectCode", project.projectCode());

        List<String> selectedDiffItemIds = normalizeSelectedDiffItemIds(
                objectStringList(requirementChangeProposal.get("selectedDiffItemIds"))
        );
        List<Map<String, Object>> selectedDiffItems = objectMapList(
                requirementChangeProposal.get("selectedDiffItems")
        );
        if (!selectedDiffItemIds.isEmpty()) {
            proposal.put("selectedDiffItemIds", selectedDiffItemIds);
            proposal.put("selectionPolicy", "ONLY_SELECTED_ATTACHMENT_DIFF_ITEMS");
        }
        if (!selectedDiffItems.isEmpty()) {
            proposal.put("selectedDiffItems", selectedDiffItems);
        }

        proposal.put("sourceRequirementChangeProposal", requirementChangeProposal);

        return proposal;
    }

    private Map<String, Object> runAssetChangeAgent(
            Map<String, Object> requirementChangeProposal,
            String instruction,
            ProjectAccess project,
            ProjectModuleEntity module,
            ArtifactVersionEntity currentArtifact,
            String targetArtifactType,
            String beforeMarkdown
    ) {
        Map<String, Object> reviewInput = new LinkedHashMap<>();
        reviewInput.put("mode", "ASSET_CHANGE_PROPOSAL");
        reviewInput.put("instruction", instruction);
        reviewInput.put("targetArtifactType", targetArtifactType);
        reviewInput.put("currentMarkdown", buildFocusedMarkdownContext(beforeMarkdown, instruction));
        reviewInput.put("currentMarkdownContextMode", "FOCUSED_PATCH_CONTEXT");
        reviewInput.put("fullMarkdownLength", beforeMarkdown == null ? 0 : beforeMarkdown.length());
        reviewInput.put("currentContent", currentArtifact.getContent() == null ? Map.of() : currentArtifact.getContent());
        Map<String, Object> currentArtifactPayload = new LinkedHashMap<>();
        currentArtifactPayload.put("artifactId", currentArtifact.getId() == null ? null : currentArtifact.getId().toString());
        currentArtifactPayload.put("artifactType", currentArtifact.getArtifactType());
        currentArtifactPayload.put("title", currentArtifact.getTitle());
        currentArtifactPayload.put("versionNo", currentArtifact.getVersionNo());
        currentArtifactPayload.put("status", currentArtifact.getStatus());
        reviewInput.put("currentArtifact", currentArtifactPayload);

        Map<String, Object> targetNodePayload = new LinkedHashMap<>();
        targetNodePayload.put("nodeId", module == null ? null : module.getId().toString());
        targetNodePayload.put("nodeName", module == null ? "当前项目" : module.getModuleName());
        targetNodePayload.put("nodeType", module == null ? "PROJECT" : module.getModuleType());
        targetNodePayload.put("moduleKey", module == null ? null : module.getModuleKey());
        reviewInput.put("targetNode", targetNodePayload);
        reviewInput.put("relatedArtifacts", buildRelatedArtifactContext(
                project.projectId(),
                module == null ? null : module.getId(),
                targetArtifactType
        ));
        reviewInput.put("requirementChangeProposal", requirementChangeProposal == null ? Map.of() : requirementChangeProposal);
        if (requirementChangeProposal != null) {
            List<String> selectedDiffItemIds = normalizeSelectedDiffItemIds(
                    objectStringList(requirementChangeProposal.get("selectedDiffItemIds"))
            );
            List<Map<String, Object>> selectedDiffItems = objectMapList(
                    requirementChangeProposal.get("selectedDiffItems")
            );

            if (!selectedDiffItemIds.isEmpty()) {
                reviewInput.put("selectedDiffItemIds", selectedDiffItemIds);
                reviewInput.put("selectionPolicy", "ONLY_SELECTED_ATTACHMENT_DIFF_ITEMS");
            }
            if (!selectedDiffItems.isEmpty()) {
                reviewInput.put("selectedDiffItems", selectedDiffItems);
            }
        }
        reviewInput.put("expectedOutput", "Patch-first mode. Return proposedPatchOperations and proposal metadata. Do not return full proposedContentMarkdown unless patch operations are not enough.");

        try {
            Object raw = pythonAgentClient.reviewAssistantArtifact(
                    UUID.randomUUID(),
                    project.projectCode(),
                    module == null ? null : module.getId(),
                    targetArtifactType,
                    instruction,
                    reviewInput
            );

            Map<String, Object> rawMap = toMap(raw);
            Map<String, Object> content = toMap(rawMap.get("content"));

            if (content.isEmpty()) {
                return Map.of();
            }

            content.putIfAbsent("source", "AssistantArtifactReviewAgent");
            Object contentMarkdown = rawMap.get("content_markdown");
            if (contentMarkdown != null && textValue(content.get("proposedContentMarkdown")).isBlank()) {
                content.put("proposedContentMarkdown", String.valueOf(contentMarkdown));
            }

            return content;
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("source", "JAVA_SAFE_FALLBACK_AFTER_AGENT_FAILURE");
            fallback.put("summary", "资产变更 Agent 调用失败，已生成保守补充型编辑提案。");
            fallback.put("riskNotes", List.of("Python LangGraph Agent 调用失败：" + ex.getMessage(), "本次提案采用保守补充方式，不做不可验证的自动替换。"));
            return fallback;
        }
    }

    private PatchApplicationResult applyPatchOperations(
            String beforeMarkdown,
            List<Map<String, Object>> operations
    ) {
        String result = firstNonBlank(beforeMarkdown, "# 需求资产");
        List<String> summary = new ArrayList<>();
        int appliedCount = 0;

        for (Map<String, Object> operation : operations) {
            if (operation == null || operation.isEmpty()) {
                continue;
            }

            String op = firstNonBlank(
                    textValue(operation.get("op")),
                    textValue(operation.get("operation")),
                    "replace"
            ).toLowerCase(Locale.ROOT);
            String sectionTitle = textValue(operation.get("sectionTitle"));
            String findText = firstNonBlank(
                    textValue(operation.get("findText")),
                    textValue(operation.get("beforeText")),
                    textValue(operation.get("anchorText"))
            );
            String replaceWith = firstNonBlank(
                    textValue(operation.get("replaceWith")),
                    textValue(operation.get("afterText")),
                    textValue(operation.get("newText"))
            );
            String insertAfter = firstNonBlank(
                    textValue(operation.get("insertAfter")),
                    textValue(operation.get("afterAnchor"))
            );
            String insertBefore = firstNonBlank(
                    textValue(operation.get("insertBefore")),
                    textValue(operation.get("beforeAnchor"))
            );

            boolean applied = false;
            if ((op.contains("replace") || op.contains("update"))
                    && !findText.isBlank()
                    && !replaceWith.isBlank()
                    && result.contains(findText)) {
                result = result.replace(findText, replaceWith);
                applied = true;
            } else if ((op.contains("insert_after") || op.contains("append") || op.contains("add"))
                    && !insertAfter.isBlank()
                    && !replaceWith.isBlank()
                    && result.contains(insertAfter)) {
                int index = result.indexOf(insertAfter) + insertAfter.length();
                result = result.substring(0, index)
                        + normalizeInsertedMarkdown(replaceWith, false)
                        + result.substring(index);
                applied = true;
            } else if ((op.contains("insert_before") || op.contains("prepend"))
                    && !insertBefore.isBlank()
                    && !replaceWith.isBlank()
                    && result.contains(insertBefore)) {
                int index = result.indexOf(insertBefore);
                result = result.substring(0, index)
                        + normalizeInsertedMarkdown(replaceWith, true)
                        + result.substring(index);
                applied = true;
            } else if ((op.contains("append_section") || op.contains("add_section"))
                    && !replaceWith.isBlank()) {
                result = firstNonBlank(result, "# 需求资产").trim()
                        + "\n\n"
                        + normalizeSectionMarkdown(sectionTitle, replaceWith);
                applied = true;
            }

            if (applied) {
                appliedCount++;
                summary.add(firstNonBlank(
                        sectionTitle,
                        findText.length() > 48 ? findText.substring(0, 48) + "..." : findText,
                        "局部修订项 " + appliedCount
                ));
            }
        }

        if (appliedCount == 0) {
            return new PatchApplicationResult("", 0, List.of("未找到可稳定应用的局部 patch，已退回全文草案或安全补充逻辑。"));
        }

        return new PatchApplicationResult(result, appliedCount, summary);
    }

    private String buildFocusedMarkdownContext(
            String markdown,
            String instruction
    ) {
        String text = firstNonBlank(markdown, "# 需求资产");
        if (text.length() <= 12000) {
            return text;
        }

        String normalizedInstruction = normalize(instruction);
        List<String> keywords = extractPatchKeywords(normalizedInstruction);
        List<String> sections = splitMarkdownSections(text);
        StringBuilder focused = new StringBuilder();
        focused.append("<!-- 当前资产较长，以下为 Patch-first 局部修订相关上下文，不是全文。 -->\n\n");

        for (String section : sections) {
            String normalizedSection = normalize(section);
            boolean hit = keywords.stream().anyMatch(normalizedSection::contains);
            if (hit && focused.length() + section.length() < 12000) {
                focused.append(section.trim()).append("\n\n");
            }
        }

        if (focused.length() < 320) {
            focused.append(text, 0, Math.min(text.length(), 10000));
        }

        return focused.toString().trim();
    }

    private List<String> splitMarkdownSections(
            String markdown
    ) {
        String[] lines = firstNonBlank(markdown).split("\\R", -1);
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("#") && current.length() > 0) {
                sections.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }

        if (current.length() > 0) {
            sections.add(current.toString());
        }

        return sections.isEmpty() ? List.of(firstNonBlank(markdown)) : sections;
    }

    private List<String> extractPatchKeywords(
            String normalizedInstruction
    ) {
        List<String> keywords = new ArrayList<>();
        String source = firstNonBlank(normalizedInstruction);
        for (String token : source.split("[^0-9A-Z\\u4E00-\\u9FFF]+")) {
            String item = token.trim();
            if (item.length() >= 2 && !keywords.contains(item)) {
                keywords.add(item);
            }
        }

        for (String item : List.of("登录", "注册", "锁定", "验证码", "账号", "账户", "失败", "异常", "规则", "接口", "测试")) {
            if (source.contains(item) && !keywords.contains(item)) {
                keywords.add(item);
            }
        }

        return keywords;
    }

    private String normalizeInsertedMarkdown(
            String value,
            boolean trailing
    ) {
        String text = firstNonBlank(value);
        if (text.isBlank()) {
            return "";
        }
        return trailing ? text.trim() + "\n\n" : "\n" + text.trim();
    }

    private String normalizeSectionMarkdown(
            String sectionTitle,
            String value
    ) {
        String text = firstNonBlank(value);
        if (text.startsWith("#")) {
            return text.trim();
        }
        if (!sectionTitle.isBlank()) {
            return "### " + sectionTitle.trim() + "\n" + text.trim();
        }
        return text.trim();
    }

    private record PatchApplicationResult(
            String contentMarkdown,
            int appliedCount,
            List<String> summary
    ) {
    }

    private String buildFallbackProposedMarkdown(
            String beforeMarkdown,
            String instruction,
            Map<String, Object> requirementChangeProposal,
            String targetArtifactType
    ) {
        StringBuilder builder = new StringBuilder(firstNonBlank(beforeMarkdown, "# 需求资产").trim());
        builder.append("\n\n---\n\n");
        builder.append("## AI Assistant 需求变更补充\n\n");
        builder.append("**目标资产**：").append(targetArtifactType).append("\n\n");
        builder.append("**用户要求**：").append(instruction).append("\n\n");

        String summary = textValue(requirementChangeProposal.get("summary"));
        if (!summary.isBlank()) {
            builder.append("**变更摘要**：").append(summary).append("\n\n");
        }

        builder.append("**待落地修改**：\n");
        List<Map<String, Object>> selectedDiffItems = objectMapList(
                requirementChangeProposal.get("selectedDiffItems")
        );
        if (selectedDiffItems.isEmpty()) {
            builder.append("- 请根据用户要求修订本资产中的业务规则、页面字段、接口约束、流程步骤或测试点。\n");
        } else {
            int index = 1;
            for (Map<String, Object> diffItem : selectedDiffItems) {
                builder.append("- 仅处理已选择差异 #")
                        .append(index)
                        .append("：")
                        .append(firstNonBlank(textValue(diffItem.get("title")), textValue(diffItem.get("attachmentExcerpt")), "待核对差异"))
                        .append("\n");
                String excerpt = textValue(diffItem.get("attachmentExcerpt"));
                if (!excerpt.isBlank()) {
                    builder.append("  - 附件依据：")
                            .append(trimForSummary(excerpt, 240))
                            .append("\n");
                }
                index++;
            }
        }
        builder.append("- 本版本由安全兜底逻辑生成，未执行不可验证的关键词替换。\n");
        builder.append("- 确认前请人工核对修改位置是否准确，并在必要时使用文档视图继续编辑。\n");

        return builder.toString();
    }

    private String buildProposedMarkdown(
            String beforeMarkdown,
            String instruction,
            Map<String, Object> requirementChangeProposal,
            String targetArtifactType
    ) {
        String baseMarkdown = firstNonBlank(beforeMarkdown, "# ").trim();
        String patchedMarkdown = applyDeterministicRequirementPatch(
                baseMarkdown,
                instruction,
                targetArtifactType
        );

        StringBuilder builder = new StringBuilder();
        builder.append(patchedMarkdown.trim());
        builder.append("\n\n---\n\n");
        builder.append("## AI Assistant 需求变更执行记录\n\n");
        builder.append("**变更来源**：对话式需求变更确认 / 资产更新闭环\n\n");
        builder.append("**目标资产**：").append(targetArtifactType).append("\n\n");
        builder.append("**用户要求**：").append(instruction).append("\n\n");

        String summary = textValue(requirementChangeProposal.get("summary"));
        if (!summary.isBlank()) {
            builder.append("**变更摘要**：").append(summary).append("\n\n");
        }

        builder.append("**已执行的文档修订**：\n");
        for (String item : buildPatchSummary(instruction, targetArtifactType)) {
            builder.append("- ").append(item).append("\n");
        }

        builder.append("\n**确认要求**：该版本由 AI Assistant 根据用户确认生成，需要人工确认后再进入下游同步修订或开发交接。\n");

        return builder.toString();
    }

    private String applyDeterministicRequirementPatch(
            String markdown,
            String instruction,
            String targetArtifactType
    ) {
        String result = markdown == null ? "" : markdown;
        String normalized = normalize(instruction);

        if (containsAny(normalized, List.of("不允许", "不要", "禁止", "移除", "取消", "去掉"))
                && normalized.contains("邮箱")
                && containsAny(normalized, List.of("手机号", "手机号码", "手机"))) {
            result = replaceEmailLoginWithPhoneLogin(result);
        }

        if (normalized.contains("短信验证码")
                && containsAny(normalized, List.of("图形验证码", "图片验证码"))
                && containsAny(normalized, List.of("不要", "不需要", "取消", "去掉", "移除"))) {
            result = result
                    .replace("图形验证码", "短信验证码")
                    .replace("图片验证码", "短信验证码");
        }

        if (normalized.contains("短信验证码")
                && containsAny(normalized, List.of("增加", "新增", "添加", "加一个", "加入"))
                && !result.contains("短信验证码")) {
            result = appendRequirementClause(
                    result,
                    "短信验证码要求",
                    List.of(
                            "登录或注册流程涉及手机号验证时，应支持短信验证码。",
                            "短信验证码需包含获取验证码、倒计时、防重复发送、过期校验和错误提示。",
                            "相关接口契约、异常提示和测试规格需同步修订并确认。"
                    )
            );
        }

        if (Objects.equals(result, markdown)) {
            result = appendRequirementClause(
                    result,
                    "AI Assistant 需求变更落地项",
                    buildPatchSummary(instruction, targetArtifactType)
            );
        }

        return result;
    }

    private String replaceEmailLoginWithPhoneLogin(
            String markdown
    ) {
        String result = markdown == null ? "" : markdown;

        String[][] replacements = new String[][]{
                {"手机号/邮箱", "手机号"},
                {"手机号码/邮箱", "手机号"},
                {"手机号或邮箱", "手机号"},
                {"手机号码或邮箱", "手机号"},
                {"邮箱或手机号", "手机号"},
                {"邮箱/手机号", "手机号"},
                {"手机号、邮箱", "手机号"},
                {"手机号码、邮箱", "手机号"},
                {"手机号和邮箱", "手机号"},
                {"手机号码和邮箱", "手机号"},
                {"邮箱和手机号", "手机号"},
                {"邮箱登录", "手机号登录"},
                {"邮箱账号", "手机号"},
                {"邮箱地址", "手机号"},
                {"邮箱", "手机号"}
        };

        for (String[] replacement : replacements) {
            result = result.replace(replacement[0], replacement[1]);
        }

        if (!result.contains("仅允许手机号登录") && !result.contains("只允许手机号登录")) {
            result = appendRequirementClause(
                    result,
                    "登录方式约束",
                    List.of(
                            "仅允许用户使用手机号和密码进行登录。",
                            "登录表单、页面提示、接口入参、校验规则和测试用例中不得再保留邮箱登录入口。",
                            "手机号字段需要校验格式、必填状态和错误提示。"
                    )
            );
        }

        return result;
    }

    private String appendRequirementClause(
            String markdown,
            String title,
            List<String> items
    ) {
        StringBuilder builder = new StringBuilder(firstNonBlank(markdown, "# 需求资产").trim());
        builder.append("\n\n### ").append(title).append("\n");

        for (String item : deduplicate(items)) {
            if (item == null || item.isBlank()) {
                continue;
            }

            builder.append("- ").append(item.trim()).append("\n");
        }

        return builder.toString();
    }

    private List<String> buildPatchSummary(
            String instruction,
            String targetArtifactType
    ) {
        List<String> items = new ArrayList<>();
        items.add("采用 Patch-first 局部修订：Agent 输出修改片段，后端应用到当前资产正文并保存完整版本快照。");
        items.add("围绕用户要求补充或修正 " + targetArtifactType + " 中对应的业务规则、页面字段、接口约束或测试点。");
        items.add("保存局部修订版本后需检查与上游资产的一致性，并评估是否需要继续用 AI 局部同步修订下游资产。");

        String normalized = normalize(instruction);
        if (normalized.contains("邮箱") && containsAny(normalized, List.of("手机号", "手机号码", "手机"))) {
            items.add("登录方式相关描述已按手机号登录约束进行修订，邮箱登录入口需要从页面、接口和测试中同步移除。");
        }
        if (containsAny(normalized, List.of("验证码", "登录", "注册"))) {
            items.add("涉及验证码/登录/注册类内容时，需要同步修订安全规则、接口契约和异常提示。");
        }
        if (containsAny(normalized, List.of("退款", "支付", "订单"))) {
            items.add("涉及交易链路时，需要补充状态流转、幂等控制、异常分支和回滚策略。");
        }
        if (containsAny(normalized, List.of("权限", "角色", "审批", "审核"))) {
            items.add("涉及权限/审批时，需要同步补充权限点、角色边界和测试用例。");
        }

        return deduplicate(items);
    }

    private List<String> buildRiskNotes(
            String artifactType,
            List<String> downstream
    ) {
        List<String> notes = new ArrayList<>();
        notes.add("确认后会保存新的当前版本快照，旧版本不会删除，可在版本历史中查看。 ");

        if (STRUCTURED_EDIT_ARTIFACTS.contains(artifactType)) {
            notes.add("该资产属于结构化资产，当前采用保守补充方式保存修订版本；复杂字段/接口结构应在后续专用结构化编辑器中细化。 ");
        }

        if (!downstream.isEmpty()) {
            notes.add("受影响下游资产会标记为“受上游影响”。优先使用 AI 局部同步修订，只有结构差异过大时再选择重生成。 ");
        }

        if ("PAGE_SPEC".equals(artifactType)) {
            notes.add("页面规范变更后，UI_CONTRACT 应由专用 UI_CONTRACT Agent 对齐修订或重新生成，并再次确认页面效果。 ");
        }

        return deduplicate(notes);
    }

    private List<String> buildRecommendedSteps(
            String artifactType,
            List<String> downstream
    ) {
        List<String> steps = new ArrayList<>();
        steps.add("确认该资产局部修订提案，保存新的当前版本快照。");
        steps.add("在版本历史中确认新版本内容，必要时确认通过或退回修改。");

        if (!downstream.isEmpty()) {
            steps.add("打开影响分析，优先选择 AI 局部同步修订受影响资产；确需整体重跑时再选择重生成：" + String.join("、", downstream) + "。");
        }

        if ("PAGE_SPEC".equals(artifactType)) {
            steps.add("重新生成 UI_CONTRACT，并让用户再次确认页面效果。 ");
        }

        return steps;
    }

    private Map<String, Object> buildRequirementChangeProposalWithSelection(
            AssistantAssetEditProposalRequest request
    ) {
        Map<String, Object> proposal = new LinkedHashMap<>(
                request == null ? Map.of() : request.getRequirementChangeProposal()
        );

        putBaselineMetadataFromRequest(request, proposal);

        List<String> selectedDiffItemIds = mergeSelectedDiffItemIds(
                request == null ? List.of() : request.getSelectedDiffItemIds(),
                objectStringList(proposal.get("selectedDiffItemIds"))
        );

        List<Map<String, Object>> selectedDiffItems = resolveSelectedDiffItems(
                selectedDiffItemIds,
                request == null ? List.of() : request.getSelectedDiffItems(),
                objectMapList(proposal.get("selectedDiffItems")),
                objectMapList(proposal.get("diffItems"))
        );

        if (!selectedDiffItemIds.isEmpty()) {
            proposal.put("selectedDiffItemIds", selectedDiffItemIds);
            proposal.put("selectionPolicy", "ONLY_SELECTED_ATTACHMENT_DIFF_ITEMS");
            proposal.put("selectionSource", "ATTACHMENT_ARTIFACT_DIFF_CARD");
        }

        if (!selectedDiffItems.isEmpty()) {
            proposal.put("selectedDiffItems", selectedDiffItems);
            proposal.put("diffItems", selectedDiffItems);
            proposal.put("selectedDiffItemCount", selectedDiffItems.size());
            proposal.put("missingRequirements", selectedDiffItemExcerptsByType(
                    selectedDiffItems,
                    "MISSING"
            ));
            proposal.put("possibleConflicts", selectedDiffItemExcerptsByType(
                    selectedDiffItems,
                    "POTENTIAL_CONFLICT"
            ));
            proposal.put("alreadyCoveredItems", selectedDiffItemExcerptsByType(
                    selectedDiffItems,
                    "ALREADY_COVERED"
            ));
            proposal.put("selectedDiffSummary", buildSelectedDiffSummary(selectedDiffItems));
            proposal.put("summary", buildSelectedRequirementChangeSummary(
                    proposal,
                    selectedDiffItems
            ));
        }

        return proposal;
    }

    private void putBaselineMetadataFromRequest(
            AssistantAssetEditProposalRequest request,
            Map<String, Object> proposal
    ) {
        if (proposal == null) {
            return;
        }

        if (request != null && request.getBaseArtifactId() != null) {
            proposal.put("baseArtifactId", request.getBaseArtifactId().toString());
        }
        if (request != null && request.getBaseVersionNo() != null) {
            proposal.put("baseVersionNo", request.getBaseVersionNo());
        }
        if (request != null && request.getBaseChecksum() != null && !request.getBaseChecksum().isBlank()) {
            proposal.put("baseChecksum", request.getBaseChecksum().trim());
        }

        Map<String, Object> targetArtifact = toMap(proposal.get("targetArtifact"));

        if (!targetArtifact.isEmpty()) {
            String artifactId = textValue(targetArtifact.get("artifactId"));
            Integer versionNo = integerValue(targetArtifact.get("versionNo"));
            String checksum = textValue(targetArtifact.get("checksum"));

            if (!artifactId.isBlank()) {
                proposal.putIfAbsent("baseArtifactId", artifactId);
            }
            if (versionNo != null) {
                proposal.putIfAbsent("baseVersionNo", versionNo);
            }
            if (!checksum.isBlank()) {
                proposal.putIfAbsent("baseChecksum", checksum);
            }
        }

        if (proposal.containsKey("baseArtifactId")
                || proposal.containsKey("baseVersionNo")
                || proposal.containsKey("baseChecksum")) {
            proposal.put("baselineValidationPolicy", "BLOCK_IF_CURRENT_ARTIFACT_CHANGED");
        }
    }

    private List<String> mergeSelectedDiffItemIds(
            List<String> primary,
            List<String> secondary
    ) {
        List<String> result = new ArrayList<>();
        for (String item : primary == null ? List.<String>of() : primary) {
            String value = item == null ? "" : item.trim();
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }
        for (String item : secondary == null ? List.<String>of() : secondary) {
            String value = item == null ? "" : item.trim();
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    @SafeVarargs
    private final List<Map<String, Object>> resolveSelectedDiffItems(
            List<String> selectedDiffItemIds,
            List<Map<String, Object>>... candidates
    ) {
        Set<String> selectedIds = new LinkedHashSet<>(
                selectedDiffItemIds == null ? List.of() : selectedDiffItemIds
        );
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (List<Map<String, Object>> candidateList : candidates) {
            if (candidateList == null || candidateList.isEmpty()) {
                continue;
            }

            for (Map<String, Object> item : candidateList) {
                if (item == null || item.isEmpty()) {
                    continue;
                }

                Map<String, Object> copy = new LinkedHashMap<>(item);
                String id = textValue(copy.get("id"));

                if (!selectedIds.isEmpty() && !selectedIds.contains(id)) {
                    continue;
                }

                String fingerprint = id.isBlank()
                        ? textValue(copy.get("title")) + "|" + textValue(copy.get("attachmentExcerpt"))
                        : id;

                if (!fingerprint.isBlank() && seen.add(fingerprint)) {
                    result.add(copy);
                }
            }
        }

        return result;
    }

    private List<String> selectedDiffItemExcerptsByType(
            List<Map<String, Object>> selectedDiffItems,
            String expectedType
    ) {
        List<String> result = new ArrayList<>();

        for (Map<String, Object> item : selectedDiffItems == null ? List.<Map<String, Object>>of() : selectedDiffItems) {
            String type = textValue(item.get("type"));
            if (!expectedType.equals(type)) {
                continue;
            }

            String value = firstNonBlank(
                    textValue(item.get("attachmentExcerpt")),
                    textValue(item.get("title"))
            );

            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }

        return result;
    }

    private List<String> buildSelectedDiffSummary(
            List<Map<String, Object>> selectedDiffItems
    ) {
        List<String> result = new ArrayList<>();
        int index = 1;

        for (Map<String, Object> item : selectedDiffItems == null ? List.<Map<String, Object>>of() : selectedDiffItems) {
            result.add(index + ". " + firstNonBlank(
                    textValue(item.get("title")),
                    textValue(item.get("attachmentExcerpt")),
                    "待核对差异"
            ));
            index++;
        }

        return result;
    }

    private String buildSelectedRequirementChangeSummary(
            Map<String, Object> proposal,
            List<Map<String, Object>> selectedDiffItems
    ) {
        String originalSummary = textValue(proposal.get("summary"));
        int count = selectedDiffItems == null ? 0 : selectedDiffItems.size();

        if (count <= 0) {
            return originalSummary;
        }

        String prefix = "仅基于用户选择的 " + count + " 条附件差异生成资产局部修订。";

        if (originalSummary.isBlank() || originalSummary.startsWith("仅基于用户选择")) {
            return prefix;
        }

        return prefix + "原差异摘要：" + originalSummary;
    }

    private List<String> buildPatchSummaryFromSelectedDiffItems(
            List<Map<String, Object>> selectedDiffItems,
            String targetArtifactType
    ) {
        List<String> result = new ArrayList<>();

        for (Map<String, Object> item : selectedDiffItems == null ? List.<Map<String, Object>>of() : selectedDiffItems) {
            String type = textValue(item.get("type"));
            if ("ALREADY_COVERED".equals(type)) {
                continue;
            }

            String title = firstNonBlank(
                    textValue(item.get("title")),
                    textValue(item.get("attachmentExcerpt")),
                    "待核对差异"
            );
            result.add("仅基于已选择差异修订 " + targetArtifactType + "：" + title);
        }

        return result;
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

    private String resolveTargetArtifactType(
            AssistantAssetEditProposalRequest request
    ) {
        Map<String, Object> requirementChangeProposal = request == null
                ? Map.of()
                : request.getRequirementChangeProposal();

        List<String> affectedArtifacts = stringList(requirementChangeProposal.get("affectedArtifacts"));

        String selected = normalizeArtifactType(request == null ? null : request.getSelectedArtifactType());
        if (isSupportedTargetArtifact(selected)) {
            return selected;
        }

        for (String item : affectedArtifacts) {
            String artifactType = normalizeArtifactType(item);
            if (isSupportedTargetArtifact(artifactType)) {
                return artifactType;
            }
        }

        String scope = normalize(textValue(requirementChangeProposal.get("scopeType")));
        if (scope.contains("PAGE") || scope.contains("UI")) {
            return "PAGE_SPEC";
        }
        if (scope.contains("API")) {
            return "API_CONTRACT";
        }
        if (scope.contains("DATA")) {
            return "DATA_MODEL";
        }
        if (scope.contains("FLOW")) {
            return "BUSINESS_FLOW";
        }

        return "MODULE_PRD";
    }

    private boolean isSupportedTargetArtifact(
            String artifactType
    ) {
        return EDITABLE_ARTIFACTS.contains(artifactType)
                || STRUCTURED_EDIT_ARTIFACTS.contains(artifactType);
    }

    private void validateCurrentArtifactBaseline(
            Map<String, Object> proposal,
            ArtifactVersionEntity currentArtifact,
            String staleMessage
    ) {
        if (proposal == null || proposal.isEmpty() || currentArtifact == null) {
            return;
        }

        String expectedArtifactIdText = firstNonBlank(
                textValue(proposal.get("baseArtifactId")),
                textValue(deepGet(proposal, "targetArtifact", "artifactId")),
                textValue(proposal.get("targetArtifactId"))
        );
        UUID expectedArtifactId = parseUuid(expectedArtifactIdText);

        Integer expectedVersionNo = firstInteger(
                proposal.get("baseVersionNo"),
                deepGet(proposal, "targetArtifact", "versionNo"),
                proposal.get("currentVersionNo")
        );

        String expectedChecksum = firstNonBlank(
                textValue(proposal.get("baseChecksum")),
                textValue(deepGet(proposal, "targetArtifact", "checksum")),
                textValue(proposal.get("currentChecksum"))
        );

        boolean hasExpectedBaseline = expectedArtifactId != null
                || expectedVersionNo != null
                || !expectedChecksum.isBlank();

        if (!hasExpectedBaseline) {
            return;
        }

        List<String> mismatches = new ArrayList<>();

        if (expectedArtifactId != null && !Objects.equals(expectedArtifactId, currentArtifact.getId())) {
            mismatches.add("artifactId");
        }

        if (expectedVersionNo != null && !Objects.equals(expectedVersionNo, currentArtifact.getVersionNo())) {
            mismatches.add("versionNo");
        }

        String currentChecksum = firstNonBlank(currentArtifact.getChecksum());
        if (!expectedChecksum.isBlank()
                && !currentChecksum.isBlank()
                && !Objects.equals(expectedChecksum, currentChecksum)) {
            mismatches.add("checksum");
        }

        if (!mismatches.isEmpty()) {
            throw new IllegalArgumentException(
                    staleMessage
                            + " 预期基线：artifactId=" + firstNonBlank(expectedArtifactIdText, "未知")
                            + ", versionNo=" + (expectedVersionNo == null ? "未知" : expectedVersionNo)
                            + (expectedChecksum.isBlank() ? "" : ", checksum=" + expectedChecksum)
                            + "；当前版本：artifactId=" + currentArtifact.getId()
                            + ", versionNo=" + currentArtifact.getVersionNo()
                            + (currentChecksum.isBlank() ? "" : ", checksum=" + currentChecksum)
                            + "；不一致字段：" + String.join(", ", mismatches)
            );
        }
    }

    private ArtifactVersionEntity loadCurrentArtifact(
            UUID projectId,
            UUID moduleId,
            String artifactType
    ) {
        if (artifactType == null || artifactType.isBlank()) {
            throw new IllegalArgumentException("缺少目标资产类型，无法生成编辑提案。");
        }

        return moduleId == null
                ? artifactVersionRepository
                .findFirstByProjectIdAndModuleIdIsNullAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        artifactType
                )
                .orElseThrow(() -> new IllegalArgumentException("当前项目没有可编辑的 " + artifactType + " 资产。"))
                : artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        artifactType
                )
                .orElseThrow(() -> new IllegalArgumentException("当前节点没有可编辑的 " + artifactType + " 资产。"));
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

    private int nextVersionNo(
            UUID projectId,
            UUID moduleId,
            String artifactType
    ) {
        Integer maxVersionNo = moduleId == null
                ? artifactVersionRepository.findMaxProjectArtifactVersionNo(projectId, artifactType)
                : artifactVersionRepository.findMaxModuleArtifactVersionNo(projectId, moduleId, artifactType);

        return Math.max(0, maxVersionNo == null ? 0 : maxVersionNo) + 1;
    }

    private void appendAssistantEditMetadata(
            Map<String, Object> content,
            Map<String, Object> proposal,
            String confirmationNote,
            ArtifactVersionEntity previous
    ) {
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("proposalId", proposal.get("proposalId"));
        edit.put("source", "ASSISTANT_ASSET_EDIT_CONFIRMATION");
        edit.put("confirmedAt", LocalDateTime.now().toString());
        edit.put("confirmedBy", currentUserProvider.getCurrentUser().username());
        edit.put("confirmationNote", confirmationNote);
        edit.put("originalInstruction", proposal.get("originalInstruction"));
        edit.put("summary", proposal.get("summary"));
        edit.put("previousArtifactId", previous.getId().toString());
        edit.put("previousVersionNo", previous.getVersionNo());
        edit.put("previousChecksum", previous.getChecksum());
        edit.put("baseArtifactId", firstNonBlank(
                textValue(proposal.get("baseArtifactId")),
                textValue(deepGet(proposal, "targetArtifact", "artifactId")),
                textValue(proposal.get("targetArtifactId"))
        ));
        edit.put("baseVersionNo", firstInteger(
                proposal.get("baseVersionNo"),
                deepGet(proposal, "targetArtifact", "versionNo"),
                proposal.get("currentVersionNo")
        ));
        edit.put("baseChecksum", firstNonBlank(
                textValue(proposal.get("baseChecksum")),
                textValue(deepGet(proposal, "targetArtifact", "checksum")),
                textValue(proposal.get("currentChecksum"))
        ));

        List<Object> history = new ArrayList<>();
        Object existing = content.get("assistantEditHistory");
        if (existing instanceof List<?> existingList) {
            history.addAll(existingList);
        }
        history.add(edit);
        content.put("assistantEditHistory", history);
        content.put("lastAssistantEdit", edit);
    }

    private UUID resolveModuleId(
            UUID selectedNodeId,
            Map<String, Object> proposal
    ) {
        if (selectedNodeId != null) {
            return selectedNodeId;
        }

        return parseUuid(firstNonBlank(
                textValue(deepGet(proposal, "targetNode", "nodeId")),
                textValue(proposal.get("moduleId"))
        ));
    }

    private List<Map<String, Object>> buildRelatedArtifactContext(
            UUID projectId,
            UUID moduleId,
            String targetArtifactType
    ) {
        if (moduleId == null) {
            return List.of();
        }

        List<Map<String, Object>> related = new ArrayList<>();
        for (String artifactType : upstreamArtifactTypes(targetArtifactType)) {
            if (artifactType == null || artifactType.equals(targetArtifactType)) {
                continue;
            }

            artifactVersionRepository
                    .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                            projectId,
                            moduleId,
                            artifactType
                    )
                    .ifPresent(artifact -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("artifactType", artifact.getArtifactType());
                        item.put("artifactId", artifact.getId() == null ? null : artifact.getId().toString());
                        item.put("title", artifact.getTitle());
                        item.put("versionNo", artifact.getVersionNo());
                        item.put("status", artifact.getStatus());
                        item.put("contentMarkdownExcerpt", trimForSummary(firstNonBlank(
                                artifact.getContentMarkdown(),
                                safeJson(artifact.getContent())
                        ), 5000));
                        related.add(item);
                    });
        }

        return related;
    }

    private List<String> upstreamArtifactTypes(
            String targetArtifactType
    ) {
        if ("MODULE_PRD".equals(targetArtifactType)) {
            return List.of();
        }
        if ("BUSINESS_FLOW".equals(targetArtifactType)) {
            return List.of("MODULE_PRD");
        }
        if ("PAGE_SPEC".equals(targetArtifactType)) {
            return List.of("MODULE_PRD", "BUSINESS_FLOW");
        }
        if ("DATA_MODEL".equals(targetArtifactType)) {
            return List.of("MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC");
        }
        if ("API_CONTRACT".equals(targetArtifactType)) {
            return List.of("MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL");
        }
        if ("TEST_SPEC".equals(targetArtifactType)) {
            return List.of("MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL", "API_CONTRACT");
        }
        return List.of("MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL", "API_CONTRACT", "TEST_SPEC");
    }

    private List<String> defaultDownstreamArtifacts(
            String artifactType
    ) {
        if ("MODULE_PRD".equals(artifactType)) {
            return List.of("BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL", "API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE");
        }
        if ("BUSINESS_FLOW".equals(artifactType)) {
            return List.of("PAGE_SPEC", "DATA_MODEL", "API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE");
        }
        if ("PAGE_SPEC".equals(artifactType)) {
            return List.of("DATA_MODEL", "API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE");
        }
        if ("DATA_MODEL".equals(artifactType)) {
            return List.of("API_CONTRACT", "TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE");
        }
        if ("API_CONTRACT".equals(artifactType)) {
            return List.of("TEST_SPEC", "UI_CONTRACT", "DEV_HANDOFF_PACKAGE");
        }
        if ("TEST_SPEC".equals(artifactType)) {
            return List.of("UI_CONTRACT", "DEV_HANDOFF_PACKAGE");
        }
        return List.of("DEV_HANDOFF_PACKAGE");
    }

    private Map<String, Object> copyContent(
            Map<String, Object> content
    ) {
        if (content == null || content.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return objectMapper.convertValue(
                content,
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(
            Object value
    ) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<String> objectStringList(
            Object value
    ) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = textValue(item);
                if (!text.isBlank() && !result.contains(text.trim())) {
                    result.add(text.trim());
                }
            }
            return result;
        }
        String text = textValue(value);
        if (!text.isBlank()) {
            result.add(text.trim());
        }
        return result;
    }

    private List<Map<String, Object>> objectMapList(
            Object value
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Map<String, Object> map = toMap(item);
                if (!map.isEmpty()) {
                    result.add(map);
                }
            }
        }
        return result;
    }

    private List<Object> objectList(
            Object value
    ) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String safeJson(
            Object value
    ) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String sha256Hex(
            String value
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String normalizeArtifactType(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("PRD".equals(normalized)) return "MODULE_PRD";
        if ("FLOW".equals(normalized)) return "BUSINESS_FLOW";
        if ("PAGE".equals(normalized)) return "PAGE_SPEC";
        if ("API".equals(normalized)) return "API_CONTRACT";
        if ("DATA".equals(normalized)) return "DATA_MODEL";
        if ("TEST".equals(normalized)) return "TEST_SPEC";

        return normalized;
    }

    private Object deepGet(
            Map<String, Object> source,
            String... keys
    ) {
        Object current = source;

        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }

        return current;
    }

    private Integer firstInteger(
            Object... values
    ) {
        if (values == null) {
            return null;
        }

        for (Object value : values) {
            Integer parsed = integerValue(value);
            if (parsed != null) {
                return parsed;
            }
        }

        return null;
    }

    private Integer integerValue(
            Object value
    ) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        String text = textValue(value);
        if (text.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private UUID parseUuid(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<String> stringList(
            Object value
    ) {
        List<String> result = new ArrayList<>();

        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = textValue(item);
                if (!text.isBlank()) {
                    result.add(text.trim().toUpperCase(Locale.ROOT));
                }
            }
        } else {
            String text = textValue(value);
            if (!text.isBlank()) {
                result.add(text.trim().toUpperCase(Locale.ROOT));
            }
        }

        return deduplicate(result);
    }

    private List<String> deduplicate(
            List<String> items
    ) {
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.isBlank() && !result.contains(item)) {
                result.add(item);
            }
        }
        return result;
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

    private String normalize(
            String value
    ) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String textValue(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String trimForSummary(
            String value,
            int maxLength
    ) {
        String text = firstNonBlank(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength)) + "...";
    }
}
