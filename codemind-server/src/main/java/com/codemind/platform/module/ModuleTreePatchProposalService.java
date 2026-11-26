package com.codemind.platform.module;

import com.codemind.platform.agent.common.client.PythonAgentClient;
import com.codemind.platform.project.ProjectAccessService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
public class ModuleTreePatchProposalService {

    private static final com.fasterxml.jackson.databind.ObjectMapper A9_MODULE_TREE_PATCH_JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();


    private final ProjectModuleService projectModuleService;
    private final ProjectModuleRepository projectModuleRepository;
    private final ProjectAccessService projectAccessService;
    private final PythonAgentClient pythonAgentClient;

    public ModuleTreePatchProposalService(
            ProjectModuleService projectModuleService,
            ProjectModuleRepository projectModuleRepository,
            ProjectAccessService projectAccessService,
            PythonAgentClient pythonAgentClient
    ) {
        this.projectModuleService = projectModuleService;
        this.projectModuleRepository = projectModuleRepository;
        this.projectAccessService = projectAccessService;
        this.pythonAgentClient = pythonAgentClient;
    }

    public ModuleTreePatchProposalResponse propose(
            UUID workspaceId,
            String projectCode,
            ProposeModuleTreePatchRequest request
    ) {
        String instruction = normalizeText(
                request == null ? null : request.getInstruction()
        );

        ProjectAccessService.ProjectAccess project =
                projectAccessService.resolveAccessibleProject(
                        workspaceId,
                        projectCode
                );

        validateRequestedModuleTreeBaseline(
                request,
                project.projectId(),
                "模块树已经发生变化，请重新执行附件差异分析后再生成模块树变更提案。"
        );

        ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline =
                captureModuleTreeBaseline(project.projectId());

        UUID selectedNodeId = request == null ? null : request.getSelectedNodeId();
        UUID baseArtifactId = request == null ? null : request.getBaseArtifactId();
        Integer baseVersionNo = request == null ? null : request.getBaseVersionNo();
        String baseChecksum = request == null ? null : request.getBaseChecksum();
        List<String> selectedDiffItemIds = normalizeSelectedDiffItemIds(
                request == null ? List.of() : request.getSelectedDiffItemIds()
        );
        List<Map<String, Object>> selectedDiffItems = resolveSelectedDiffItems(
                request == null ? List.of() : request.getSelectedDiffItems(),
                selectedDiffItemIds
        );
        Map<String, Object> attachmentSummary = buildAttachmentSummaryWithSelection(
                request == null ? Map.of() : request.getAttachmentSummary(),
                selectedDiffItemIds,
                selectedDiffItems,
                baseArtifactId,
                baseVersionNo,
                baseChecksum
        );
        List<Map<String, Object>> attachmentContextsForAgent = selectedDiffItems.isEmpty()
                ? (request == null ? List.of() : request.getAttachmentContexts())
                : buildSelectedDiffItemContextsForAgent(selectedDiffItems);

        if (instruction.isBlank()) {
            throw new IllegalArgumentException("模块树变更说明不能为空。");
        }

        List<ProjectModuleService.ModuleTreeNode> moduleTree =
                projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                        workspaceId,
                        projectCode
                );

        // A9.10.14.4.3.15.4.15.6.3_STRICT_EXPLICIT_TARGET_MODULE_LOCK
        UUID a91563StrictTargetNodeId = a91563ResolveStrictExplicitTargetModuleId(
                instruction,
                moduleTree
        );
        if (a91563StrictTargetNodeId != null) {
            selectedNodeId = a91563StrictTargetNodeId;
        }

        try {
            Object rawResponse = pythonAgentClient.proposeModuleTreePatch(
                    UUID.randomUUID(),
                    projectCode,
                    selectedNodeId,
                    instruction,
                    moduleTree,
                    attachmentContextsForAgent,
                    attachmentSummary
            );

            ModuleTreePatchProposalResponse normalized =
                    normalizeAgentProposalResponse(
                            rawResponse,
                            instruction,
                            selectedNodeId,
                            moduleTree,
                            selectedDiffItemIds,
                            selectedDiffItems,
                            baseArtifactId,
                            baseVersionNo,
                            baseChecksum
                    );

            normalized = attachModuleTreeBaseline(
                    normalized,
                    moduleTreeBaseline
            );

            if (hasExecutableOperations(normalized)) {
                return normalized;
            }

            return appendNoOperationGuidance(normalized);
        } catch (Exception ex) {
            return attachModuleTreeBaseline(
                    buildRuleFallbackProposal(
                            instruction,
                            selectedNodeId,
                            moduleTree,
                            "Python ModuleTreePatchAgent 调用失败：" + ex.getMessage()
                    ),
                    moduleTreeBaseline
            );
        }
    }

    private void validateRequestedModuleTreeBaseline(
            ProposeModuleTreePatchRequest request,
            UUID projectId,
            String staleMessage
    ) {
        if (request == null || projectId == null) {
            return;
        }

        String expectedHash = firstNonBlank(request.getBaseModuleTreeHash());
        Integer expectedNodeCount = request.getBaseModuleTreeNodeCount();

        if (!ModuleTreeBaselineSupport.hasBaseline(expectedHash, expectedNodeCount)) {
            return;
        }

        ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot current =
                captureModuleTreeBaseline(projectId);

        List<String> mismatches = new ArrayList<>();

        if (!expectedHash.isBlank() && !Objects.equals(expectedHash, current.hash())) {
            mismatches.add("moduleTreeHash");
        }

        if (expectedNodeCount != null && !Objects.equals(expectedNodeCount, current.nodeCount())) {
            mismatches.add("moduleTreeNodeCount");
        }

        if (!mismatches.isEmpty()) {
            throw new IllegalArgumentException(
                    ModuleTreeBaselineSupport.buildMismatchMessage(
                            staleMessage,
                            expectedHash,
                            expectedNodeCount,
                            current
                    )
                            + "；不一致字段：" + String.join(", ", mismatches)
            );
        }
    }

    private ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot captureModuleTreeBaseline(
            UUID projectId
    ) {
        return ModuleTreeBaselineSupport.capture(
                projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(projectId)
        );
    }

    private List<Map<String, Object>> attachModuleTreeBaselineToOperations(
            Object operations,
            ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline
    ) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (!(operations instanceof List<?> list)) {
            return result;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }

            Map<String, Object> operation = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                operation.put(
                        String.valueOf(entry.getKey()),
                        entry.getValue()
                );
            }

            Map<String, Object> extra = toMap(operation.get("extra"));
            extra.put("baseModuleTreeHash", moduleTreeBaseline.hash());
            extra.put("baseModuleTreeNodeCount", moduleTreeBaseline.nodeCount());
            extra.put("treeBaselineValidationPolicy", "BLOCK_IF_MODULE_TREE_CHANGED_BEFORE_APPLY");
            operation.put("extra", extra);

            result.add(operation);
        }

        return result;
    }


    private ModuleTreePatchProposalResponse attachModuleTreeBaseline(
            ModuleTreePatchProposalResponse response,
            ModuleTreeBaselineSupport.ModuleTreeBaselineSnapshot moduleTreeBaseline
    ) {
        if (response == null || moduleTreeBaseline == null) {
            return response;
        }

        Map<String, Object> proposal = new LinkedHashMap<>(
                response.proposal() == null ? Map.of() : response.proposal()
        );
        proposal.put("baseModuleTreeHash", moduleTreeBaseline.hash());
        proposal.put("baseModuleTreeNodeCount", moduleTreeBaseline.nodeCount());
        proposal.put("baseModuleTreeSummary", moduleTreeBaseline.summary());
        proposal.put("treeBaselineValidationPolicy", "BLOCK_IF_MODULE_TREE_CHANGED_BEFORE_APPLY");
        proposal.putIfAbsent("requiresUserConfirmation", true);
        proposal.put("operations", attachModuleTreeBaselineToOperations(
                proposal.get("operations"),
                moduleTreeBaseline
        ));

        return new ModuleTreePatchProposalResponse(
                response.description(),
                response.source(),
                proposal,
                response.markdown(),
                response.requiresUserConfirmation()
        );
    }


    @SuppressWarnings("unchecked")
    private ModuleTreePatchProposalResponse normalizeAgentProposalResponse(
            Object rawResponse,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            List<String> selectedDiffItemIds,
            List<Map<String, Object>> selectedDiffItems,
            UUID baseArtifactId,
            Integer baseVersionNo,
            String baseChecksum
    ) {
        Map<String, Object> rawMap = toMap(rawResponse);
        Map<String, Object> content = toMap(rawMap.get("content"));

        if (content.isEmpty()) {
            content = rawMap;
        }

        Map<String, Object> proposal = toMap(content.get("proposal"));

        if (proposal.isEmpty()) {
            proposal = new LinkedHashMap<>();
            proposal.put(
                    "description",
                    firstNonBlank(
                            textValue(content.get("description")),
                            "根据用户说明生成模块树变更提案"
                    )
            );
            proposal.put(
                    "source",
                    firstNonBlank(
                            textValue(content.get("source")),
                            "LANGGRAPH_MODULE_TREE_PATCH_AGENT"
                    )
            );
            proposal.put(
                    "operations",
                    normalizeOperations(
                            content.get("operations"),
                            instruction,
                            selectedNodeId,
                            moduleTree
                    )
            );
            proposal.put("impact", normalizeImpact(content.get("impact")));
        } else {
            proposal = new LinkedHashMap<>(proposal);
            proposal.put(
                    "operations",
                    normalizeOperations(
                            proposal.get("operations"),
                            instruction,
                            selectedNodeId,
                            moduleTree
                    )
            );
            proposal.put(
                    "impact",
                    normalizeImpact(proposal.get("impact"))
            );
        }

        proposal = a9RecoverAgentJsonOperationsIfNeeded(
                proposal,
                content,
                instruction,
                selectedNodeId,
                moduleTree
        );

        proposal = a91562CompletePageOperationFromInstructionIfNeeded(
                proposal,
                instruction,
                selectedNodeId,
                moduleTree
        );

        proposal = a91563ForceExplicitPageNameInOperations(
                proposal,
                instruction,
                selectedNodeId,
                moduleTree
        );

        String description = firstNonBlank(
                textValue(content.get("description")),
                textValue(proposal.get("description")),
                "根据用户说明生成模块树变更提案"
        );

        String source = firstNonBlank(
                textValue(content.get("source")),
                textValue(proposal.get("source")),
                "LANGGRAPH_MODULE_TREE_PATCH_AGENT"
        );

        proposal.put("description", description);
        proposal.put("source", source);
        proposal.putIfAbsent("requiresUserConfirmation", true);
        proposal.putIfAbsent("agentMode", "LANGGRAPH_PROPOSAL_ONLY");
        if (selectedDiffItemIds != null && !selectedDiffItemIds.isEmpty()) {
            proposal.put("selectedDiffItemIds", selectedDiffItemIds);
            proposal.put("selectionPolicy", "ONLY_SELECTED_ATTACHMENT_DIFF_ITEMS");
        }
        if (selectedDiffItems != null && !selectedDiffItems.isEmpty()) {
            proposal.put("selectedDiffItems", selectedDiffItems);
            proposal.put("selectionSource", "ATTACHMENT_ARTIFACT_DIFF_CARD");
        }
        if (baseArtifactId != null) {
            proposal.put("baseArtifactId", baseArtifactId.toString());
        }
        if (baseVersionNo != null) {
            proposal.put("baseVersionNo", baseVersionNo);
        }
        if (baseChecksum != null && !baseChecksum.isBlank()) {
            proposal.put("baseChecksum", baseChecksum.trim());
        }
        if (baseArtifactId != null || baseVersionNo != null || (baseChecksum != null && !baseChecksum.isBlank())) {
            proposal.put("baselineValidationPolicy", "SOURCE_ASSET_BASELINE_RECORDED");
        }

        Map<String, Object> impact = normalizeImpact(proposal.get("impact"));
        proposal.put("impact", impact);

        List<String> validationNotes = asStringList(content.get("validationNotes"));
        validationNotes.addAll(asStringList(proposal.get("validationNotes")));
        validationNotes = dedupe(validationNotes);

        if (!validationNotes.isEmpty()) {
            proposal.put("validationNotes", validationNotes);
        }

        Object confidence = firstPresent(
                content.get("confidence"),
                proposal.get("confidence")
        );
        if (confidence != null) {
            proposal.put("confidence", confidence);
        }

        String markdown = firstNonBlank(
                textValue(rawMap.get("content_markdown")),
                textValue(content.get("markdown")),
                buildMarkdownSummary(
                        description,
                        operationsOf(proposal),
                        impact,
                        validationNotes
                )
        );

        return new ModuleTreePatchProposalResponse(
                description,
                source,
                proposal,
                markdown,
                true
        );
    }

    private Map<String, Object> buildAttachmentSummaryWithSelection(
            Map<String, Object> attachmentSummary,
            List<String> selectedDiffItemIds,
            List<Map<String, Object>> selectedDiffItems,
            UUID baseArtifactId,
            Integer baseVersionNo,
            String baseChecksum
    ) {
        Map<String, Object> result = new LinkedHashMap<>(
                attachmentSummary == null ? Map.of() : attachmentSummary
        );

        if (selectedDiffItemIds != null && !selectedDiffItemIds.isEmpty()) {
            result.put("selectedDiffItemIds", selectedDiffItemIds);
            result.put("selectionPolicy", "ONLY_SELECTED_ATTACHMENT_DIFF_ITEMS");
        }

        if (selectedDiffItems != null && !selectedDiffItems.isEmpty()) {
            result.put("selectedDiffItems", selectedDiffItems);
            result.put("selectedDiffItemCount", selectedDiffItems.size());
            result.put("selectionSource", "ATTACHMENT_ARTIFACT_DIFF_CARD");
        }
        if (baseArtifactId != null) {
            result.put("baseArtifactId", baseArtifactId.toString());
        }
        if (baseVersionNo != null) {
            result.put("baseVersionNo", baseVersionNo);
        }
        if (baseChecksum != null && !baseChecksum.isBlank()) {
            result.put("baseChecksum", baseChecksum.trim());
        }
        if (baseArtifactId != null || baseVersionNo != null || (baseChecksum != null && !baseChecksum.isBlank())) {
            result.put("baselineValidationPolicy", "SOURCE_ASSET_BASELINE_RECORDED");
        }

        return result;
    }

    private List<Map<String, Object>> buildSelectedDiffItemContextsForAgent(
            List<Map<String, Object>> selectedDiffItems
    ) {
        if (selectedDiffItems == null || selectedDiffItems.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> contexts = new ArrayList<>();
        int index = 1;

        for (Map<String, Object> diffItem : selectedDiffItems) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("source", "ATTACHMENT_ARTIFACT_DIFF_SELECTION");
            context.put("diffItemId", textValue(diffItem.get("id")));
            context.put("diffItemType", textValue(diffItem.get("type")));
            context.put("title", textValue(diffItem.get("title")));
            context.put("attachmentExcerpt", textValue(diffItem.get("attachmentExcerpt")));
            context.put("currentEvidence", textValue(diffItem.get("currentEvidence")));
            context.put("recommendation", textValue(diffItem.get("recommendation")));
            context.put("targetArtifactType", textValue(diffItem.get("targetArtifactType")));
            context.put("parseStatus", "SELECTED_DIFF_ITEM");
            context.put(
                    "parsedTextExcerpt",
                    buildSelectedDiffItemText(diffItem, index)
            );
            contexts.add(context);
            index++;
        }

        return contexts;
    }

    private String buildSelectedDiffItemText(
            Map<String, Object> diffItem,
            int index
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("已选择差异 #" + index);
        lines.add("ID：" + textValue(diffItem.get("id")));
        lines.add("类型：" + textValue(diffItem.get("type")));
        lines.add("标题：" + textValue(diffItem.get("title")));

        String attachmentExcerpt = textValue(diffItem.get("attachmentExcerpt"));
        if (!attachmentExcerpt.isBlank()) {
            lines.add("附件依据：" + attachmentExcerpt);
        }

        String currentEvidence = textValue(diffItem.get("currentEvidence"));
        if (!currentEvidence.isBlank()) {
            lines.add("当前资产依据：" + currentEvidence);
        }

        String recommendation = textValue(diffItem.get("recommendation"));
        if (!recommendation.isBlank()) {
            lines.add("建议：" + recommendation);
        }

        return String.join("\n", lines);
    }

    private List<Map<String, Object>> resolveSelectedDiffItems(
            List<Map<String, Object>> selectedDiffItems,
            List<String> selectedDiffItemIds
    ) {
        if (selectedDiffItems == null || selectedDiffItems.isEmpty()) {
            return List.of();
        }

        Set<String> selectedIds = new LinkedHashSet<>(
                selectedDiffItemIds == null ? List.of() : selectedDiffItemIds
        );
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Map<String, Object> item : selectedDiffItems) {
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

    private ModuleTreePatchProposalResponse appendNoOperationGuidance(
            ModuleTreePatchProposalResponse response
    ) {
        Map<String, Object> proposal = new LinkedHashMap<>(response.proposal());
        Map<String, Object> impact = normalizeImpact(proposal.get("impact"));

        List<String> riskNotes = asStringList(impact.get("riskNotes"));
        riskNotes.add("当前提案没有可执行模块树操作，不会修改模块树；请补充说明要新增、移动、重命名还是删除哪个节点。");
        impact.put("riskNotes", dedupe(riskNotes));

        proposal.put("impact", impact);

        return new ModuleTreePatchProposalResponse(
                response.description(),
                response.source(),
                proposal,
                response.markdown(),
                response.requiresUserConfirmation()
        );
    }


    // A9.10.14.4.3.15.4.14.10_PATCH_AGENT_JSON_SALVAGE
    private Map<String, Object> a9RecoverAgentJsonOperationsIfNeeded(
            Map<String, Object> proposal,
            Map<String, Object> content,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        Map<String, Object> result = new LinkedHashMap<>(proposal == null ? Map.of() : proposal);

        if (a9HasOperationPayload(result.get("operations"))) {
            result.put("impact", a9SanitizeModuleTreePatchImpact(result.get("impact")));
            if (content != null && a9ContainsRawJsonDump(content.get("markdown"))) {
                content.remove("markdown");
            }
            return result;
        }

        Map<String, Object> recovered = a9ExtractAgentJsonMapFromTextSources(result, content);

        if (!recovered.isEmpty() && a9HasOperationPayload(recovered.get("operations"))) {
            List<Map<String, Object>> recoveredOperations = normalizeOperations(
                    recovered.get("operations"),
                    instruction,
                    selectedNodeId,
                    moduleTree
            );

            if (!recoveredOperations.isEmpty()) {
                result.put("operations", recoveredOperations);
                result.put(
                        "description",
                        firstNonBlank(
                                textValue(result.get("description")),
                                textValue(recovered.get("description")),
                                "根据用户说明生成模块树变更提案"
                        )
                );
                result.put(
                        "source",
                        firstNonBlank(
                                textValue(recovered.get("source")),
                                textValue(result.get("source")),
                                "LANGGRAPH_MODULE_TREE_PATCH_AGENT_JSON_SALVAGED"
                        )
                );
                result.put(
                        "impact",
                        a9SanitizeModuleTreePatchImpact(
                                recovered.containsKey("impact") ? recovered.get("impact") : result.get("impact")
                        )
                );
                result.put("agentMode", "JSON_SALVAGED_FROM_AGENT_FALLBACK");
                result.put("jsonSalvageApplied", true);

                if (content != null) {
                    content.remove("markdown");
                    content.remove("content_markdown");
                    content.remove("contentMarkdown");
                }

                return result;
            }
        }

        result.put("impact", a9SanitizeModuleTreePatchImpact(result.get("impact")));
        if (content != null && a9ContainsRawJsonDump(content.get("markdown"))) {
            content.remove("markdown");
        }
        return result;
    }

    private boolean a9HasOperationPayload(Object value) {
        return value instanceof java.util.Collection<?> collection && !collection.isEmpty();
    }

    private Map<String, Object> a9ExtractAgentJsonMapFromTextSources(
            Map<String, Object> proposal,
            Map<String, Object> content
    ) {
        List<String> candidates = new ArrayList<>();

        a9CollectCandidateText(candidates, content);
        a9CollectCandidateText(candidates, proposal);

        for (String candidate : candidates) {
            Map<String, Object> parsed = a9ParseFirstJsonObject(candidate);

            if (parsed.isEmpty()) {
                continue;
            }

            Map<String, Object> nestedProposal = a9ToStringObjectMap(parsed.get("proposal"));
            if (!nestedProposal.isEmpty() && a9HasOperationPayload(nestedProposal.get("operations"))) {
                return nestedProposal;
            }

            if (a9HasOperationPayload(parsed.get("operations"))) {
                return parsed;
            }
        }

        return Map.of();
    }

    private void a9CollectCandidateText(
            List<String> candidates,
            Object value
    ) {
        if (value == null) {
            return;
        }

        if (value instanceof String text) {
            if (a9ContainsRawJsonDump(text) || text.contains("\"operations\"")) {
                candidates.add(text);
            }
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                a9CollectCandidateText(candidates, item);
            }
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                a9CollectCandidateText(candidates, item);
            }
        }
    }

    private Map<String, Object> a9ParseFirstJsonObject(String rawText) {
        String json = a9ExtractFirstJsonObject(rawText);

        if (json.isBlank()) {
            return Map.of();
        }

        String sanitizedJson = json
                .replace("\\_", "_")
                .replace("\\:", ":");

        try {
            return A9_MODULE_TREE_PATCH_JSON_MAPPER.readValue(
                    sanitizedJson,
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String a9ExtractFirstJsonObject(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String source = rawText.trim();
        String marker = "LLM output is not valid JSON:";
        int markerIndex = source.indexOf(marker);
        int searchFrom = markerIndex >= 0 ? markerIndex + marker.length() : 0;
        int start = source.indexOf('{', searchFrom);

        if (start < 0) {
            return "";
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = start; index < source.length(); index++) {
            char ch = source.charAt(index);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (ch == '\\') {
                escaped = true;
                continue;
            }

            if (ch == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;

                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }

        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> a9ToStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        return result;
    }

    private Map<String, Object> a9SanitizeModuleTreePatchImpact(Object rawImpact) {
        Map<String, Object> impact = new LinkedHashMap<>(normalizeImpact(rawImpact));
        Object rawRiskNotes = impact.get("riskNotes");
        List<String> riskNotes = new ArrayList<>();
        boolean replacedRawJsonDump = false;

        if (rawRiskNotes instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String note = String.valueOf(item == null ? "" : item).trim();

                if (note.isBlank()) {
                    continue;
                }

                if (a9ContainsRawJsonDump(note)) {
                    if (!replacedRawJsonDump) {
                        riskNotes.add("LLM 返回了非严格 JSON，系统已隐藏原始长文本并尝试恢复可执行模块树操作。");
                        replacedRawJsonDump = true;
                    }
                    continue;
                }

                riskNotes.add(a9TrimForUser(note, 180));
            }
        }

        if (riskNotes.isEmpty() && replacedRawJsonDump) {
            riskNotes.add("系统已从 Agent 兜底信息中恢复可执行模块树操作。");
        }

        impact.put("riskNotes", riskNotes);
        return impact;
    }

    private boolean a9ContainsRawJsonDump(Object value) {
        if (value == null) {
            return false;
        }

        String text = String.valueOf(value);

        return text.contains("LLM output is not valid JSON")
                || (text.contains("\"operations\"") && text.contains("\"ADD_NODE\"") && text.length() > 800);
    }

    private String a9TrimForUser(String value, int maxLength) {
        String text = value == null ? "" : value.trim();

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }



    // A9.10.14.4.3.15.4.15.6.2_HOTFIX_PROPOSAL_PAGE_COMPLETION
    private Map<String, Object> a91562CompletePageOperationFromInstructionIfNeeded(
            Map<String, Object> proposal,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        Map<String, Object> result = new LinkedHashMap<>(proposal == null ? Map.of() : proposal);

        List<Map<String, Object>> operations = normalizeOperations(
                result.get("operations"),
                instruction,
                selectedNodeId,
                moduleTree
        );

        if (operations.isEmpty()) {
            result.put("operations", operations);
            return result;
        }

        boolean hasFeature = false;
        boolean hasPage = false;
        Map<String, Object> firstFeature = null;

        for (Map<String, Object> operation : operations) {
            if (operation == null || operation.isEmpty()) {
                continue;
            }

            if (!"ADD_NODE".equals(textValue(operation.get("op")).toUpperCase())) {
                continue;
            }

            String type = normalizeModuleTypeAlias(
                    textValue(operation.get("moduleType")),
                    textValue(operation.get("moduleName"))
            );

            if ("FEATURE".equals(type)) {
                hasFeature = true;
                if (firstFeature == null) {
                    firstFeature = operation;
                }
            }

            if ("PAGE".equals(type)) {
                hasPage = true;
                String normalizedPageName = a91562NormalizePatchPageName(
                        textValue(operation.get("moduleName"))
                );
                if (!normalizedPageName.isBlank()) {
                    operation.put("moduleName", normalizedPageName);
                    operation.put("moduleKey", generateModuleKey(normalizedPageName, "PAGE"));
                }
            }
        }

        String pageName = a91562InferPageNameFromInstruction(instruction);

        if (!hasFeature || hasPage || pageName.isBlank() || firstFeature == null) {
            result.put("operations", operations);
            return result;
        }

        Map<String, Object> pageOperation = new LinkedHashMap<>();
        pageOperation.put("op", "ADD_NODE");

        // 提案阶段还拿不到“本次新建 FEATURE”的真实 id。
        // 这里暂时使用 FEATURE 的 parentId，应用阶段已有 runtime guard 会把 PAGE 转挂到刚创建的 FEATURE 下。
        pageOperation.put("parentId", firstFeature.get("parentId"));
        pageOperation.put("moduleName", pageName);
        pageOperation.put("moduleKey", generateModuleKey(pageName, "PAGE"));
        pageOperation.put("moduleType", "PAGE");

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", "JAVA_PROPOSAL_PAGE_COMPLETION_FROM_INSTRUCTION");
        extra.put("sourceInstruction", instruction);
        extra.put("originalInstruction", instruction);
        extra.put("derivedFromFeatureName", textValue(firstFeature.get("moduleName")));
        extra.put("targetResolvePolicy", "FEATURE_THEN_PAGE_IN_SAME_PROPOSAL");
        extra.put("proposalCompletionVersion", "A9.10.14.4.3.15.4.15.6.2");
        extra.put("confirmationStatus", "PENDING_CONFIRMATION");
        extra.put("sourceType", "AI_COMPLETED");
        pageOperation.put("extra", extra);

        operations.add(pageOperation);

        result.put("operations", operations);
        result.put("autoCompletedPageOperation", true);
        result.put("autoCompletedPageOperationName", pageName);

        List<String> validationNotes = asStringList(result.get("validationNotes"));
        validationNotes.add("已根据用户说明中的对应页面/弹窗，自动补齐 PAGE 操作：" + pageName);
        result.put("validationNotes", dedupe(validationNotes));

        return result;
    }

    private String a91562InferPageNameFromInstruction(String instruction) {
        String text = normalizeText(instruction);
        if (text.isBlank()) {
            return "";
        }

        List<String> patterns = List.of(
                "对应的[“\\\"']?([^，。；;\\\"'”]{2,50}?(?:弹窗|弹框|抽屉|窗口|页面|页))[”\\\"']?",
                "并(?:增加|新增|添加|创建|新建)对应的[“\\\"']?([^，。；;\\\"'”]{2,50}?(?:弹窗|弹框|抽屉|窗口|页面|页))[”\\\"']?",
                "(?:增加|新增|添加|创建|新建)(?:一个|个)?[“\\\"']?([^，。；;\\\"'”]{2,50}?(?:弹窗|弹框|抽屉|窗口|页面|页))[”\\\"']?"
        );

        for (String pattern : patterns) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile(pattern).matcher(text);

            if (matcher.find()) {
                String name = a91562NormalizePatchPageName(matcher.group(1));
                if (!name.isBlank()) {
                    return name;
                }
            }
        }

        return "";
    }

    private String a91562NormalizePatchPageName(String rawName) {
        String name = normalizeText(rawName)
                .replaceAll("\\s+", "")
                .replace("“", "")
                .replace("”", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();

        if (name.isBlank()) {
            return "";
        }

        name = name
                .replace("弹窗页面", "弹窗")
                .replace("弹窗页", "弹窗")
                .replace("弹框页面", "弹框")
                .replace("弹框页", "弹框")
                .replace("抽屉页面", "抽屉")
                .replace("抽屉页", "抽屉")
                .replace("窗口页面", "窗口")
                .replace("窗口页", "窗口");

        if (name.endsWith("弹窗")
                || name.endsWith("弹框")
                || name.endsWith("抽屉")
                || name.endsWith("窗口")
                || name.endsWith("页面")
                || name.endsWith("页")) {
            return name;
        }

        return name + "页";
    }



    // A9.10.14.4.3.15.4.15.6.3_STRICT_EXPLICIT_TARGET_MODULE_LOCK
    private UUID a91563ResolveStrictExplicitTargetModuleId(
            String instruction,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        ProjectModuleService.ModuleTreeNode node =
                a91563ResolveStrictMentionedTargetModule(
                        instruction,
                        "FEATURE",
                        a91563FlattenNodes(moduleTree)
                );

        return node == null ? null : node.getId();
    }

    private ProjectModuleService.ModuleTreeNode a91563ResolveStrictMentionedTargetModule(
            String instruction,
            String childType,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        String source = normalizeText(instruction).replaceAll("\\s+", "");
        if (source.isBlank() || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        String type = normalizeModuleTypeAlias(childType, "");
        ProjectModuleService.ModuleTreeNode best = null;
        int bestScore = 0;

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            if (node == null || node.getId() == null) {
                continue;
            }

            String nodeType = normalizeModuleTypeAlias(node.getModuleType(), node.getModuleName());
            if (!"MODULE".equals(nodeType)) {
                continue;
            }

            String nodeName = normalizeText(node.getModuleName()).replaceAll("\\s+", "");
            if (nodeName.isBlank()) {
                continue;
            }

            // 必须是用户原文中精确出现的模块名；不要用“订单/销售”这种模糊相似度。
            if (!source.contains(nodeName)) {
                continue;
            }

            if (!a91563CanAcceptStrictChild(node, type)) {
                continue;
            }

            int score = nodeName.length() * 100;
            if (a91562IsFinalRequirementModule(node)) {
                score += 10000;
            }
            if (source.contains("在" + nodeName)
                    || source.contains("给" + nodeName)
                    || source.contains(nodeName + "的")
                    || source.contains(nodeName + "模块")) {
                score += 5000;
            }

            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }

        return best;
    }

    private boolean a91563CanAcceptStrictChild(
            ProjectModuleService.ModuleTreeNode parent,
            String childType
    ) {
        if (parent == null || childType == null || childType.isBlank()) {
            return false;
        }

        if (a91562IsFinalRequirementModule(parent)) {
            return "FEATURE".equals(childType) || "PAGE".equals(childType);
        }

        return "MODULE".equals(childType) || "FEATURE".equals(childType) || "PAGE".equals(childType);
    }

    private List<ProjectModuleService.ModuleTreeNode> a91563FlattenNodes(
            List<ProjectModuleService.ModuleTreeNode> nodes
    ) {
        List<ProjectModuleService.ModuleTreeNode> result = new ArrayList<>();
        flatten(nodes, result);
        return result;
    }

    private Map<String, Object> a91563ForceExplicitPageNameInOperations(
            Map<String, Object> proposal,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        Map<String, Object> result = new LinkedHashMap<>(proposal == null ? Map.of() : proposal);

        String explicitPageName = a91563InferExplicitPageName(instruction);
        if (explicitPageName.isBlank()) {
            return result;
        }

        List<Map<String, Object>> operations = normalizeOperations(
                result.get("operations"),
                instruction,
                selectedNodeId,
                moduleTree
        );

        if (operations.isEmpty()) {
            result.put("operations", operations);
            return result;
        }

        boolean touchedPage = false;
        Map<String, Object> firstFeature = null;

        for (Map<String, Object> operation : operations) {
            if (operation == null || operation.isEmpty()) {
                continue;
            }

            if (!"ADD_NODE".equals(textValue(operation.get("op")).toUpperCase())) {
                continue;
            }

            String type = normalizeModuleTypeAlias(
                    textValue(operation.get("moduleType")),
                    textValue(operation.get("moduleName"))
            );

            if ("FEATURE".equals(type) && firstFeature == null) {
                firstFeature = operation;
            }

            if ("PAGE".equals(type)) {
                String oldName = textValue(operation.get("moduleName"));
                operation.put("moduleName", explicitPageName);
                operation.put("moduleKey", generateModuleKey(explicitPageName, "PAGE"));

                Map<String, Object> extra = toMap(operation.get("extra"));
                extra.put("pageNameCorrectedByExplicitInstruction", true);
                extra.put("oldPageName", oldName);
                extra.put("sourceInstruction", instruction);
                extra.put("targetResolveVersion", "A9.10.14.4.3.15.4.15.6.3");
                operation.put("extra", extra);

                touchedPage = true;
            }
        }

        if (!touchedPage && firstFeature != null) {
            Map<String, Object> pageOperation = new LinkedHashMap<>();
            pageOperation.put("op", "ADD_NODE");
            pageOperation.put("parentId", firstFeature.get("parentId"));
            pageOperation.put("moduleName", explicitPageName);
            pageOperation.put("moduleKey", generateModuleKey(explicitPageName, "PAGE"));
            pageOperation.put("moduleType", "PAGE");

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("source", "JAVA_STRICT_EXPLICIT_PAGE_COMPLETION");
            extra.put("sourceInstruction", instruction);
            extra.put("originalInstruction", instruction);
            extra.put("derivedFromFeatureName", textValue(firstFeature.get("moduleName")));
            extra.put("targetResolveVersion", "A9.10.14.4.3.15.4.15.6.3");
            extra.put("sourceType", "AI_COMPLETED");
            extra.put("confirmationStatus", "PENDING_CONFIRMATION");
            pageOperation.put("extra", extra);

            operations.add(pageOperation);
        }

        result.put("operations", operations);
        result.put("strictExplicitPageName", explicitPageName);
        return result;
    }

    private String a91563InferExplicitPageName(String instruction) {
        String source = normalizeText(instruction);
        if (source.isBlank()) {
            return "";
        }

        List<String> patterns = List.of(
                "对应的[“\\\"']?([^，。；;\\\"'”]{2,60}?(?:弹窗|弹框|抽屉|窗口|页面|页))[”\\\"']?",
                "并(?:增加|新增|添加|创建|新建)对应的[“\\\"']?([^，。；;\\\"'”]{2,60}?(?:弹窗|弹框|抽屉|窗口|页面|页))[”\\\"']?",
                "(?:增加|新增|添加|创建|新建)(?:一个|个)?[“\\\"']?([^，。；;\\\"'”]{2,60}?(?:弹窗|弹框|抽屉|窗口|页面|页))[”\\\"']?"
        );

        for (String pattern : patterns) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile(pattern).matcher(source);

            if (matcher.find()) {
                String name = normalizeText(matcher.group(1))
                        .replaceAll("\\s+", "")
                        .replace("弹窗页面", "弹窗")
                        .replace("弹窗页", "弹窗")
                        .replace("弹框页面", "弹框")
                        .replace("弹框页", "弹框")
                        .replace("抽屉页面", "抽屉")
                        .replace("抽屉页", "抽屉")
                        .replace("窗口页面", "窗口")
                        .replace("窗口页", "窗口")
                        .trim();

                if (!name.isBlank()) {
                    return name;
                }
            }
        }

        return "";
    }


    private boolean hasExecutableOperations(
            ModuleTreePatchProposalResponse response
    ) {
        return !operationsOf(response.proposal()).isEmpty();
    }

    private ModuleTreePatchProposalResponse buildRuleFallbackProposal(
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            String reason
    ) {
        Map<String, Object> operation = buildAddNodeFallbackOperation(
                instruction,
                selectedNodeId,
                moduleTree
        );

        List<Map<String, Object>> operations = new ArrayList<>();
        List<String> validationNotes = new ArrayList<>();

        if (operation.isEmpty()) {
            validationNotes.add("Java 兜底未识别到稳定的模块树操作。");
        } else {
            operations.add(operation);
            validationNotes.add("Java 兜底识别为新增节点提案，请确认父节点、节点类型和 moduleKey。");
        }

        List<String> riskNotes = new ArrayList<>();
        riskNotes.add(reason);
        riskNotes.add("这是兜底提案，不会自动应用，必须人工确认后才会修改模块树。");
        if (operation.isEmpty()) {
            riskNotes.add("建议继续补充说明，例如：给用户中心/登录注册子模块增加密码找回页。");
        }

        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("summary", operations.isEmpty()
                ? "没有识别到可执行模块树操作。"
                : "新增或调整模块树节点后，相关需求资产需要检查。"
        );
        impact.put("affectedNodeIds", selectedNodeId == null
                ? List.of()
                : List.of(selectedNodeId.toString())
        );
        impact.put("affectedArtifacts", inferAffectedArtifacts(operations));
        impact.put("riskNotes", riskNotes);

        String description = operations.isEmpty()
                ? "未生成可执行模块树变更提案"
                : "新增模块树节点：" + operation.getOrDefault("moduleName", "新节点");

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("description", description);
        proposal.put("source", "JAVA_RULE_FALLBACK_AFTER_MODULE_TREE_PATCH_AGENT_FAILURE");
        proposal.put("operations", operations);
        proposal.put("impact", impact);
        proposal.put("validationNotes", validationNotes);
        proposal.put("confidence", operations.isEmpty() ? 0.15 : 0.35);
        proposal.put("agentMode", "JAVA_RULE_FALLBACK_PROPOSAL_ONLY");

        String markdown = buildMarkdownSummary(
                description,
                operations,
                impact,
                validationNotes
        );

        return new ModuleTreePatchProposalResponse(
                description,
                "JAVA_RULE_FALLBACK_AFTER_MODULE_TREE_PATCH_AGENT_FAILURE",
                proposal,
                markdown,
                true
        );
    }

    private Map<String, Object> buildAddNodeFallbackOperation(
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        String text = normalizeText(instruction);

        if (!containsAny(text, List.of("新增", "增加", "添加", "创建", "新建", "加一个", "加个", "补一个", "补充"))) {
            return Map.of();
        }

        String moduleType = inferModuleType(text);
        String moduleName = inferModuleName(text, moduleType);

        if (moduleName.isBlank()) {
            return Map.of();
        }

        ProjectModuleService.ModuleTreeNode parentNode =
                resolveCompatibleParentForAdd(
                        text,
                        selectedNodeId,
                        moduleTree,
                        moduleName,
                        moduleType
                );

        UUID parentId = parentNode == null ? null : parentNode.getId();

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", "ADD_NODE");
        operation.put("parentId", parentId == null ? null : parentId.toString());
        operation.put("moduleName", moduleName);
        operation.put("moduleKey", generateModuleKey(moduleName, moduleType));
        operation.put("moduleType", moduleType);
        operation.put("extra", Map.of(
                "source", "JavaModuleTreePatchFallback",
                "originalInstruction", instruction
        ));
        return operation;
    }

    private ProjectModuleService.ModuleTreeNode findMentionedNode(
            String instruction,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            String targetModuleName
    ) {
        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flatten(moduleTree, flatNodes);

        String normalizedInstruction = normalizeForMatch(instruction);
        String normalizedTarget = normalizeForMatch(targetModuleName);

        ProjectModuleService.ModuleTreeNode best = null;
        int bestScore = 0;

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            String name = normalizeForMatch(node.getModuleName());
            String key = normalizeForMatch(node.getModuleKey());

            if (name.isBlank() || name.equals(normalizedTarget)) {
                continue;
            }

            int score = 0;

            if (normalizedInstruction.contains(name)) {
                score = Math.max(score, name.length());
            }

            if (!key.isBlank() && normalizedInstruction.contains(key)) {
                score = Math.max(score, key.length());
            }

            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }

        return best;
    }

    private void flatten(
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
            flatten(node.getChildren(), result);
        }
    }

    private String inferModuleType(
            String instruction
    ) {
        if (containsAny(instruction, List.of("功能点"))) {
            return "FEATURE";
        }

        if (containsAny(instruction, List.of("功能"))) {
            return "FEATURE";
        }

        if (containsAny(instruction, List.of("页面", "页", "路由"))) {
            return "PAGE";
        }

        if (containsAny(instruction, List.of("子模块"))) {
            return "SUBMODULE";
        }

        if (containsAny(instruction, List.of("模块"))) {
            return "MODULE";
        }

        return "PAGE";
    }

    private String inferModuleName(
            String instruction,
            String moduleType
    ) {
        String kindRegex = switch (moduleType) {
            case "FEATURE" -> "(功能点|功能)";
            case "SUBMODULE" -> "(子模块)";
            case "MODULE" -> "(模块)";
            default -> "(页面|页|路由)";
        };

        String regex = "(新增|增加|添加|创建|新建|加一个|加个|补一个|补充)(一个|个)?(.{2,40}?)" + kindRegex;
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(regex).matcher(instruction);

        if (matcher.find()) {
            String rawName = normalizeText(matcher.group(3));
            return appendNameSuffix(rawName, moduleType);
        }

        String giveRegex = "给.{1,40}?(新增|增加|添加|创建|新建|加一个|加个)(一个|个)?(.{2,40}?)" + kindRegex;
        java.util.regex.Matcher giveMatcher =
                java.util.regex.Pattern.compile(giveRegex).matcher(instruction);

        if (giveMatcher.find()) {
            String rawName = normalizeText(giveMatcher.group(3));
            return appendNameSuffix(rawName, moduleType);
        }

        return "";
    }

    private String appendNameSuffix(
            String rawName,
            String moduleType
    ) {
        String name = rawName
                .replaceAll("[，。,；;].*$", "")
                .replaceAll("\\s+", "")
                .trim();

        if (name.isBlank()) {
            return "";
        }

        if ("PAGE".equals(moduleType)) {
            name = a91562NormalizePatchNodeName(name, "PAGE");
            if (name.endsWith("弹窗")
                    || name.endsWith("弹框")
                    || name.endsWith("抽屉")
                    || name.endsWith("窗口")
                    || name.endsWith("页")
                    || name.endsWith("页面")) {
                return name;
            }
            return name + "页";
        }

        if ("FEATURE".equals(moduleType) && !name.endsWith("功能") && !name.endsWith("功能点")) {
            return name + "功能";
        }

        if ("SUBMODULE".equals(moduleType) && !name.endsWith("子模块")) {
            return name + "子模块";
        }

        if ("MODULE".equals(moduleType) && !name.endsWith("模块")) {
            return name + "模块";
        }

        return name;
    }

    private List<Map<String, Object>> normalizeOperations(
            Object value
    ) {
        return normalizeOperations(
                value,
                "",
                null,
                List.of()
        );
    }

    private List<Map<String, Object>> normalizeOperations(
            Object value,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (!(value instanceof List<?> source)) {
            return result;
        }

        for (Object item : source) {
            Map<String, Object> raw = toMap(item);

            if (raw.isEmpty()) {
                continue;
            }

            String op = textValue(raw.get("op")).toUpperCase();

            if (!List.of("ADD_NODE", "UPDATE_NODE", "MOVE_NODE", "DELETE_NODE").contains(op)) {
                continue;
            }

            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("op", op);

            copyIfPresent(raw, operation, "nodeId");
            copyIfPresent(raw, operation, "parentId");
            copyIfPresent(raw, operation, "newParentId");
            copyIfPresent(raw, operation, "moduleName");
            copyIfPresent(raw, operation, "moduleKey");
            copyIfPresent(raw, operation, "moduleType");
            copyIfPresent(raw, operation, "sortOrder");

            Object extra = raw.get("extra");
            if (extra instanceof Map<?, ?>) {
                operation.put("extra", toMap(extra));
            }

            if ("ADD_NODE".equals(op)) {
                String a91562Type = normalizeModuleTypeAlias(
                        textValue(operation.get("moduleType")),
                        textValue(operation.get("moduleName"))
                );
                if (!a91562Type.isBlank()) {
                    operation.put("moduleType", a91562Type);
                }

                String a91562Name = a91562NormalizePatchNodeName(
                        textValue(operation.get("moduleName")),
                        a91562Type
                );
                if (!a91562Name.isBlank()) {
                    operation.put("moduleName", a91562Name);
                }

                Map<String, Object> a91562Extra = toMap(operation.get("extra"));
                if (!normalizeText(instruction).isBlank()) {
                    a91562Extra.put("sourceInstruction", instruction);
                    a91562Extra.put("originalInstruction", instruction);
                }
                a91562Extra.put("targetResolvePolicy", "EXPLICIT_MENTIONED_MODULE_OVERRIDES_SELECTED_NODE");
                a91562Extra.put("targetResolveVersion", "A9.10.14.4.3.15.4.15.6.2");
                operation.put("extra", a91562Extra);

                normalizeAddNodeParent(
                        operation,
                        instruction,
                        selectedNodeId,
                        moduleTree
                );

                if (isDuplicateAddNodeOperation(operation, moduleTree)) {
                    continue;
                }
            }

            result.add(operation);
        }

        return result;
    }

    private boolean isDuplicateAddNodeOperation(
            Map<String, Object> operation,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        String moduleName = textValue(operation.get("moduleName"));
        String moduleType = textValue(operation.get("moduleType"));

        if (moduleName.isBlank()) {
            return false;
        }

        String expectedName = normalizeForMatch(moduleName);
        String expectedType = normalizeForMatch(moduleType);
        String expectedParentId = textValue(operation.get("parentId"));

        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flatten(moduleTree, flatNodes);

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            String nodeName = normalizeForMatch(node.getModuleName());
            String nodeType = normalizeForMatch(node.getModuleType());
            String nodeParentId = node.getParentId() == null
                    ? ""
                    : node.getParentId().toString();

            if (nodeName.isBlank()) {
                continue;
            }

            boolean sameName =
                    Objects.equals(expectedName, nodeName) ||
                            nodeName.contains(expectedName) ||
                            expectedName.contains(nodeName);

            boolean compatibleType =
                    expectedType.isBlank() ||
                            nodeType.isBlank() ||
                            Objects.equals(expectedType, nodeType);

            boolean compatibleParent =
                    expectedParentId.isBlank() ||
                            nodeParentId.isBlank() ||
                            Objects.equals(expectedParentId, nodeParentId);

            if (sameName && compatibleType && compatibleParent) {
                return true;
            }
        }

        return false;
    }

    private void normalizeAddNodeParent(
            Map<String, Object> operation,
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree
    ) {
        String moduleName = textValue(operation.get("moduleName"));
        String moduleType = normalizeModuleTypeAlias(
                textValue(operation.get("moduleType")),
                moduleName
        );

        if (!moduleType.isBlank()) {
            operation.put("moduleType", moduleType);
        }

        if (moduleName.isBlank() || moduleType.isBlank()) {
            return;
        }

        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flatten(moduleTree, flatNodes);

        ProjectModuleService.ModuleTreeNode a91563StrictMentionedParent =
                a91563ResolveStrictMentionedTargetModule(
                        instruction,
                        moduleType,
                        flatNodes
                );

        if (a91563StrictMentionedParent != null && a91563StrictMentionedParent.getId() != null) {
            operation.put("parentId", a91563StrictMentionedParent.getId().toString());

            Map<String, Object> extra = toMap(operation.get("extra"));
            extra.put("targetModuleId", a91563StrictMentionedParent.getId().toString());
            extra.put("targetModuleName", a91563StrictMentionedParent.getModuleName());
            extra.put("targetModuleType", a91563StrictMentionedParent.getModuleType());
            extra.put("targetResolvedBy", "STRICT_EXPLICIT_INSTRUCTION_MODULE_NAME");
            extra.put("targetResolveVersion", "A9.10.14.4.3.15.4.15.6.3");
            operation.put("extra", extra);
            return;
        }

        String originalParentId = textValue(operation.get("parentId"));
        ProjectModuleService.ModuleTreeNode originalParent =
                findNodeById(
                        flatNodes,
                        originalParentId
                );

        ProjectModuleService.ModuleTreeNode resolvedParent =
                a91562ResolveExplicitMentionedParent(
                        instruction,
                        moduleName,
                        moduleType,
                        flatNodes
                );

        if (resolvedParent != null) {
            Map<String, Object> extra = toMap(operation.get("extra"));
            extra.put("targetModuleId", resolvedParent.getId() == null ? "" : resolvedParent.getId().toString());
            extra.put("targetModuleName", resolvedParent.getModuleName());
            extra.put("targetModuleType", resolvedParent.getModuleType());
            extra.put("targetResolvedBy", "EXPLICIT_INSTRUCTION_MODULE_NAME");
            extra.put("targetResolveVersion", "A9.10.14.4.3.15.4.15.6.2");
            operation.put("extra", extra);
        } else if (originalParent != null) {
            resolvedParent = resolveCompatibleAncestorOrSelf(
                    originalParent,
                    flatNodes,
                    moduleType
            );
        }

        if (resolvedParent == null) {
            resolvedParent = resolveCompatibleParentForAdd(
                    instruction,
                    selectedNodeId,
                    moduleTree,
                    moduleName,
                    moduleType
            );
        }

        if (resolvedParent != null && resolvedParent.getId() != null) {
            operation.put("parentId", resolvedParent.getId().toString());

            if (!originalParentId.isBlank()
                    && !Objects.equals(originalParentId, resolvedParent.getId().toString())) {
                Map<String, Object> extra = toMap(operation.get("extra"));
                extra.put("javaResolvedParentId", resolvedParent.getId().toString());
                extra.put("javaResolvedParentName", resolvedParent.getModuleName());
                extra.put("javaResolvedParentType", resolvedParent.getModuleType());
                extra.put("originalParentId", originalParentId);
                operation.put("extra", extra);
            }

            return;
        }

        if (isAllowedRootChildType(moduleType)) {
            operation.remove("parentId");
        }
    }


    // A9.10.14.4.3.15.4.15.6.2_TARGET_MODULE_RESOLVE_AND_PAGE_NAME_QUALITY
    private ProjectModuleService.ModuleTreeNode a91562ResolveExplicitMentionedParent(
            String instruction,
            String childName,
            String childType,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        String normalizedInstruction = normalizeForMatch(instruction);
        String normalizedChildName = normalizeForMatch(childName);
        String normalizedChildType = normalizeModuleTypeAlias(childType, childName);

        if (normalizedInstruction.isBlank() || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        ProjectModuleService.ModuleTreeNode best = null;
        int bestScore = 0;

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            if (node == null || node.getId() == null) {
                continue;
            }

            String nodeName = normalizeForMatch(node.getModuleName());
            String nodeKey = normalizeForMatch(node.getModuleKey());
            String nodeType = normalizeModuleTypeAlias(node.getModuleType(), node.getModuleName());

            if (nodeName.isBlank()) {
                continue;
            }

            if (nodeName.equals(normalizedChildName)) {
                continue;
            }

            int score = 0;
            if (normalizedInstruction.contains(nodeName)) {
                score = Math.max(score, nodeName.length() * 10);
            }
            if (!nodeKey.isBlank() && normalizedInstruction.contains(nodeKey)) {
                score = Math.max(score, nodeKey.length() * 8);
            }

            if (score <= 0) {
                continue;
            }

            if (!a91562CanAcceptChild(node, normalizedChildType)) {
                continue;
            }

            if ("MODULE".equals(nodeType)) {
                score += 30;
            }
            if (a91562IsFinalRequirementModule(node)) {
                score += 50;
            }

            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }

        return best;
    }

    private boolean a91562CanAcceptChild(
            ProjectModuleService.ModuleTreeNode parent,
            String childType
    ) {
        if (parent == null || childType == null || childType.isBlank()) {
            return false;
        }

        String parentType = normalizeModuleTypeAlias(parent.getModuleType(), parent.getModuleName());

        if ("FEATURE".equals(parentType)) {
            return "PAGE".equals(childType);
        }

        if ("PAGE".equals(parentType)) {
            return false;
        }

        if (!"MODULE".equals(parentType)) {
            return false;
        }

        if (a91562IsFinalRequirementModule(parent)) {
            return "FEATURE".equals(childType);
        }

        return "MODULE".equals(childType)
                || "FEATURE".equals(childType);
    }

    private boolean a91562IsFinalRequirementModule(
            ProjectModuleService.ModuleTreeNode node
    ) {
        if (node == null) {
            return false;
        }

        Map<String, Object> extra = node.getExtra() == null ? Map.of() : node.getExtra();

        if (Boolean.TRUE.equals(extra.get("hasModulePrd"))
                || Boolean.TRUE.equals(extra.get("finalRequirementModule"))) {
            return true;
        }

        Object mode = extra.get("requirementMode");
        if (mode == null) {
            mode = extra.get("requirement_mode");
        }

        return mode != null && "REQUIREMENT_MODULE".equalsIgnoreCase(String.valueOf(mode));
    }

    private String a91562NormalizePatchNodeName(
            String rawName,
            String moduleType
    ) {
        String name = normalizeText(rawName)
                .replaceAll("\\s+", "")
                .trim();

        if (name.isBlank()) {
            return "";
        }

        String type = moduleType == null ? "" : moduleType.trim().toUpperCase();

        if ("PAGE".equals(type)) {
            name = name
                    .replace("弹窗页面", "弹窗")
                    .replace("弹窗页", "弹窗")
                    .replace("弹框页面", "弹框")
                    .replace("弹框页", "弹框")
                    .replace("抽屉页面", "抽屉")
                    .replace("抽屉页", "抽屉")
                    .replace("窗口页面", "窗口")
                    .replace("窗口页", "窗口");

            if (name.endsWith("弹窗")
                    || name.endsWith("弹框")
                    || name.endsWith("抽屉")
                    || name.endsWith("窗口")) {
                return name;
            }
        }

        if ("FEATURE".equals(type)) {
            return name
                    .replace("功能点功能", "功能点")
                    .replace("功能功能", "功能")
                    .replace("业务能力功能", "业务能力");
        }

        return name;
    }


    private ProjectModuleService.ModuleTreeNode resolveCompatibleParentForAdd(
            String instruction,
            UUID selectedNodeId,
            List<ProjectModuleService.ModuleTreeNode> moduleTree,
            String targetModuleName,
            String childModuleType
    ) {
        List<ProjectModuleService.ModuleTreeNode> flatNodes = new ArrayList<>();
        flatten(moduleTree, flatNodes);

        ProjectModuleService.ModuleTreeNode explicitlyMentioned =
                findMentionedNode(
                        instruction,
                        moduleTree,
                        targetModuleName
                );

        ProjectModuleService.ModuleTreeNode compatibleMentioned =
                resolveCompatibleAncestorOrSelf(
                        explicitlyMentioned,
                        flatNodes,
                        childModuleType
                );

        if (compatibleMentioned != null) {
            return compatibleMentioned;
        }

        ProjectModuleService.ModuleTreeNode semanticParent =
                findSemanticParentForAdd(
                        instruction,
                        targetModuleName,
                        childModuleType,
                        flatNodes
                );

        if (semanticParent != null) {
            return semanticParent;
        }

        ProjectModuleService.ModuleTreeNode selectedNode =
                findNodeById(
                        flatNodes,
                        selectedNodeId == null ? null : selectedNodeId.toString()
                );

        ProjectModuleService.ModuleTreeNode compatibleSelected =
                resolveCompatibleAncestorOrSelf(
                        selectedNode,
                        flatNodes,
                        childModuleType
                );

        if (compatibleSelected != null) {
            return compatibleSelected;
        }

        return findFirstCompatibleParent(
                flatNodes,
                childModuleType
        );
    }

    private ProjectModuleService.ModuleTreeNode findSemanticParentForAdd(
            String instruction,
            String targetModuleName,
            String childModuleType,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        String normalizedInstruction = normalizeForMatch(instruction);
        String normalizedTarget = normalizeForMatch(targetModuleName);

        List<String> preferredNames = new ArrayList<>();

        if (normalizedInstruction.contains("用户中心") || normalizedInstruction.contains("用户")) {
            preferredNames.add("登录注册");
            preferredNames.add("用户中心");
            preferredNames.add("账号中心");
            preferredNames.add("用户管理");
        }

        if (normalizedTarget.contains("密码找回")
                || normalizedTarget.contains("找回密码")
                || normalizedTarget.contains("忘记密码")) {
            preferredNames.add("登录注册");
            preferredNames.add("账号中心");
            preferredNames.add("用户中心");
            preferredNames.add("用户管理");
            preferredNames.add("登录");
        }

        if (normalizedInstruction.contains("注册")) {
            preferredNames.add("登录注册");
            preferredNames.add("注册页");
            preferredNames.add("用户中心");
        }

        if (normalizedInstruction.contains("登录")) {
            preferredNames.add("登录注册");
            preferredNames.add("登录页");
            preferredNames.add("用户中心");
        }

        for (String preferredName : preferredNames) {
            ProjectModuleService.ModuleTreeNode matched =
                    findNodeByExactOrContainsName(
                            preferredName,
                            flatNodes
                    );

            ProjectModuleService.ModuleTreeNode compatible =
                    resolveCompatibleAncestorOrSelf(
                            matched,
                            flatNodes,
                            childModuleType
                    );

            if (compatible != null) {
                return compatible;
            }
        }

        return null;
    }

    private ProjectModuleService.ModuleTreeNode findNodeByExactOrContainsName(
            String expectedName,
            List<ProjectModuleService.ModuleTreeNode> flatNodes
    ) {
        String normalizedExpected = normalizeForMatch(expectedName);

        if (normalizedExpected.isBlank() || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        return flatNodes.stream()
                .filter(Objects::nonNull)
                .map(node -> Map.entry(
                        semanticMatchScore(
                                normalizedExpected,
                                node
                        ),
                        node
                ))
                .filter(entry -> entry.getKey() > 0)
                .max((left, right) -> Integer.compare(left.getKey(), right.getKey()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private int semanticMatchScore(
            String normalizedExpected,
            ProjectModuleService.ModuleTreeNode node
    ) {
        int score = 0;

        for (String value : List.of(
                node.getModuleName(),
                node.getModuleKey(),
                node.getModulePath()
        )) {
            String normalizedValue = normalizeForMatch(value);

            if (normalizedValue.isBlank()) {
                continue;
            }

            if (normalizedValue.equals(normalizedExpected)) {
                score = Math.max(score, 1000 + normalizedValue.length());
            } else if (normalizedValue.contains(normalizedExpected)
                    || normalizedExpected.contains(normalizedValue)) {
                score = Math.max(score, normalizedValue.length());
            }
        }

        return score;
    }

    private ProjectModuleService.ModuleTreeNode resolveCompatibleAncestorOrSelf(
            ProjectModuleService.ModuleTreeNode node,
            List<ProjectModuleService.ModuleTreeNode> flatNodes,
            String childModuleType
    ) {
        ProjectModuleService.ModuleTreeNode current = node;

        while (current != null) {
            if (isAllowedChildType(
                    current,
                    childModuleType
            )) {
                return current;
            }

            current = findNodeById(
                    flatNodes,
                    current.getParentId() == null ? null : current.getParentId().toString()
            );
        }

        return null;
    }

    private ProjectModuleService.ModuleTreeNode findFirstCompatibleParent(
            List<ProjectModuleService.ModuleTreeNode> flatNodes,
            String childModuleType
    ) {
        if (flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        return flatNodes.stream()
                .filter(node -> isAllowedChildType(
                        node,
                        childModuleType
                ))
                .min(Comparator.comparingInt(node -> node.getLevelNo() == null ? Integer.MAX_VALUE : node.getLevelNo()))
                .orElse(null);
    }

    private ProjectModuleService.ModuleTreeNode findNodeById(
            List<ProjectModuleService.ModuleTreeNode> flatNodes,
            String nodeId
    ) {
        if (nodeId == null || nodeId.isBlank() || flatNodes == null || flatNodes.isEmpty()) {
            return null;
        }

        for (ProjectModuleService.ModuleTreeNode node : flatNodes) {
            if (node != null
                    && node.getId() != null
                    && nodeId.equals(node.getId().toString())) {
                return node;
            }
        }

        return null;
    }

    private boolean isAllowedChildType(
            ProjectModuleService.ModuleTreeNode parent,
            String childModuleType
    ) {
        if (parent == null) {
            return isAllowedRootChildType(childModuleType);
        }

        String parentType = normalizeModuleTypeAlias(
                parent.getModuleType(),
                parent.getModuleName()
        );
        String childType = normalizeModuleTypeAlias(
                childModuleType,
                ""
        );

        return switch (parentType) {
            case "DOMAIN" -> List.of("MODULE", "FEATURE", "PAGE").contains(childType);
            case "MODULE" -> List.of("MODULE", "FEATURE", "PAGE").contains(childType);
            case "SUBMODULE" -> List.of("MODULE", "FEATURE", "PAGE").contains(childType);
            case "FEATURE" -> "PAGE".equals(childType);
            default -> false;
        };
    }

    private boolean isAllowedRootChildType(
            String childModuleType
    ) {
        String childType = normalizeModuleTypeAlias(
                childModuleType,
                ""
        );

        return "MODULE".equals(childType);
    }

    private String normalizeModuleTypeAlias(
            String rawType,
            String moduleName
    ) {
        String value = rawType == null ? "" : rawType.trim().toUpperCase();

        if (value.isBlank()) {
            String name = normalizeText(moduleName);

            if (name.endsWith("页") || name.endsWith("页面")) {
                return "PAGE";
            }

            if (name.endsWith("功能") || name.endsWith("功能点")) {
                return "FEATURE";
            }

            if (name.endsWith("子模块")) {
                return "SUBMODULE";
            }

            if (name.endsWith("模块")) {
                return "MODULE";
            }

            return "";
        }

        return switch (value) {
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> "DOMAIN";
            case "MODULE", "BUSINESS_MODULE", "FEATURE_MODULE", "INTEGRATION_MODULE", "INFRA_MODULE" -> "MODULE";
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> "SUBMODULE";
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION", "FUNCTION_POINT" -> "FEATURE";
            default -> value;
        };
    }


    private Map<String, Object> normalizeImpact(
            Object value
    ) {
        Map<String, Object> raw = toMap(value);
        Map<String, Object> impact = new LinkedHashMap<>();

        impact.put(
                "summary",
                firstNonBlank(
                        textValue(raw.get("summary")),
                        "模块树变更可能影响相关需求资产。"
                )
        );
        impact.put("affectedNodeIds", asStringList(raw.get("affectedNodeIds")));
        impact.put("affectedArtifacts", normalizeArtifactTypes(raw.get("affectedArtifacts")));
        impact.put("riskNotes", asStringList(raw.get("riskNotes")));
        return impact;
    }

    private List<String> inferAffectedArtifacts(
            List<Map<String, Object>> operations
    ) {
        List<String> result = new ArrayList<>();

        if (operations == null || operations.isEmpty()) {
            return result;
        }

        addUnique(result, "MODULE_PRD");

        for (Map<String, Object> operation : operations) {
            String moduleType = textValue(operation.get("moduleType")).toUpperCase();
            String op = textValue(operation.get("op")).toUpperCase();

            if ("PAGE".equals(moduleType)) {
                addUnique(result, "PAGE_SPEC");
                addUnique(result, "UI_CONTRACT");
                addUnique(result, "TEST_SPEC");
                addUnique(result, "DEV_HANDOFF_PACKAGE");
            } else if ("FEATURE".equals(moduleType)) {
                addUnique(result, "PAGE_SPEC");
                addUnique(result, "TEST_SPEC");
                addUnique(result, "UI_CONTRACT");
            } else {
                addUnique(result, "BUSINESS_FLOW");
                addUnique(result, "PAGE_SPEC");
                addUnique(result, "DATA_MODEL");
                addUnique(result, "API_CONTRACT");
                addUnique(result, "TEST_SPEC");
            }

            if ("MOVE_NODE".equals(op) || "DELETE_NODE".equals(op)) {
                addUnique(result, "BUSINESS_FLOW");
                addUnique(result, "DEV_HANDOFF_PACKAGE");
            }
        }

        return result;
    }

    private List<String> normalizeArtifactTypes(
            Object value
    ) {
        List<String> result = new ArrayList<>();

        for (String item : asStringList(value)) {
            String artifactType = item.trim().toUpperCase();

            if (List.of(
                    "MODULE_PRD",
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC",
                    "UI_CONTRACT",
                    "DEV_HANDOFF_PACKAGE"
            ).contains(artifactType)) {
                addUnique(result, artifactType);
            }
        }

        return result;
    }

    private String buildMarkdownSummary(
            String description,
            List<Map<String, Object>> operations,
            Map<String, Object> impact,
            List<String> validationNotes
    ) {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# 模块树变更提案\n\n");
        markdown.append("摘要：").append(description).append("\n\n");
        markdown.append("## 操作列表\n");

        if (operations == null || operations.isEmpty()) {
            markdown.append("- 无可执行操作。\n");
        } else {
            for (Map<String, Object> operation : operations) {
                markdown.append("- ")
                        .append(operation.getOrDefault("op", "-"))
                        .append("：")
                        .append(operation.getOrDefault("moduleName", operation.getOrDefault("nodeId", "-")))
                        .append("\n");
            }
        }

        markdown.append("\n## 影响范围\n");
        markdown.append("- ").append(textValue(impact.get("summary"))).append("\n");

        List<String> affectedArtifacts = asStringList(impact.get("affectedArtifacts"));

        if (!affectedArtifacts.isEmpty()) {
            markdown.append("\n### 可能影响资产\n");
            for (String artifact : affectedArtifacts) {
                markdown.append("- ").append(artifact).append("\n");
            }
        }

        List<String> riskNotes = asStringList(impact.get("riskNotes"));

        if (!riskNotes.isEmpty()) {
            markdown.append("\n## 风险提示\n");
            for (String note : riskNotes) {
                markdown.append("- ").append(note).append("\n");
            }
        }

        if (validationNotes != null && !validationNotes.isEmpty()) {
            markdown.append("\n## 规范化提示\n");
            for (String note : validationNotes) {
                markdown.append("- ").append(note).append("\n");
            }
        }

        markdown.append("\n> 该结果只是提案，必须由用户确认后才会应用到模块树。\n");
        return markdown.toString();
    }

    private List<Map<String, Object>> operationsOf(
            Map<String, Object> proposal
    ) {
        return normalizeOperations(proposal == null ? null : proposal.get("operations"));
    }

    private void copyIfPresent(
            Map<String, Object> source,
            Map<String, Object> target,
            String key
    ) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Object firstPresent(
            Object... values
    ) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(
            Object value
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!(value instanceof Map<?, ?> source)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        return result;
    }

    private List<String> asStringList(
            Object value
    ) {
        List<String> result = new ArrayList<>();

        if (value instanceof List<?> source) {
            for (Object item : source) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    addUnique(result, String.valueOf(item).trim());
                }
            }
        } else if (value != null && !String.valueOf(value).isBlank()) {
            addUnique(result, String.valueOf(value).trim());
        }

        return result;
    }

    private List<String> dedupe(
            List<String> source
    ) {
        List<String> result = new ArrayList<>();

        if (source == null) {
            return result;
        }

        for (String item : source) {
            addUnique(result, item);
        }

        return result;
    }

    private void addUnique(
            List<String> target,
            String value
    ) {
        if (value != null && !value.isBlank() && !target.contains(value)) {
            target.add(value);
        }
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

    private String normalizeText(
            String text
    ) {
        return text == null ? "" : text.trim();
    }

    private String normalizeForMatch(
            String text
    ) {
        return normalizeText(text)
                .replaceAll("\\s+", "")
                .toLowerCase();
    }

    private String textValue(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(
            String... values
    ) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String generateModuleKey(
            String moduleName,
            String moduleType
    ) {
        String text = moduleName
                .toLowerCase()
                .replace("用户中心", "user_center")
                .replace("用户", "user")
                .replace("密码找回", "password_recovery")
                .replace("找回密码", "password_recovery")
                .replace("登录注册", "auth")
                .replace("登录", "login")
                .replace("注册", "register")
                .replace("验证码", "verification_code")
                .replace("订单", "order")
                .replace("支付", "payment")
                .replace("退款", "refund")
                .replace("库存", "inventory")
                .replace("后台", "admin")
                .replace("管理", "management")
                .replace("列表", "list")
                .replace("详情", "detail")
                .replace("编辑", "edit")
                .replace("页面", "page")
                .replace("页", "page")
                .replace("功能点", "feature")
                .replace("功能", "feature")
                .replace("子模块", "submodule")
                .replace("模块", "module");

        text = text.replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (text.isBlank()) {
            text = "node_" + Math.abs(moduleName.hashCode());
        }

        String suffix = switch (moduleType) {
            case "PAGE" -> "page";
            case "FEATURE" -> "feature";
            case "MODULE" -> "module";
            case "SUBMODULE" -> "submodule";
            case "DOMAIN" -> "domain";
            default -> "";
        };

        if (!suffix.isBlank() && !text.endsWith("_" + suffix) && !text.equals(suffix)) {
            text = text + "_" + suffix;
        }

        return text.length() > 80 ? text.substring(0, 80) : text;
    }
}

// A9.10.14.4.3.15.6.24L0A_CONTINUE_AGENT_BACKEND: backend policy allows MODULE -> PAGE; FEATURE remains legacy capability metadata.
