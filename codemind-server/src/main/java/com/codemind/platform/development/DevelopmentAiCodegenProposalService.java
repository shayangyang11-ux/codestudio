package com.codemind.platform.development;

import com.codemind.platform.agent.common.client.PythonAgentClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DevelopmentAiCodegenProposalService {

    private static final int MAX_AI_FILES = 100;
    private static final int MAX_FILE_CONTENT_CHARS = 220_000;
    private static final int MAX_TOTAL_CONTENT_CHARS = 1_800_000;
    private static final Set<String> ALLOWED_ACTIONS = Set.of("CREATE_OR_UPDATE", "CREATE_OR_KEEP", "MERGE");
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("CONFIG", "DOC", "FRONTEND", "BACKEND", "DATABASE", "TEST", "UNKNOWN");

    private final PythonAgentClient pythonAgentClient;

    public DevelopmentAiCodegenProposalService(PythonAgentClient pythonAgentClient) {
        this.pythonAgentClient = pythonAgentClient;
    }

    public AiCodegenResult refineNodejsCodeContribution(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentPlanResponse plan,
            DevelopmentHandoffContextResponse handoffContext,
            String generationRoot,
            String nodeType,
            String nodeName,
            String previewRoute,
            List<DevelopmentCodeFileResponse> templateFiles,
            String remarks
    ) {
        List<String> warnings = new ArrayList<>();
        List<DevelopmentCodeFileResponse> safeTemplateFiles = sanitizeTemplateFiles(generationRoot, templateFiles, warnings);

        if (safeTemplateFiles.isEmpty()) {
            warnings.add("AI_CODEGEN_SKIPPED_EMPTY_TEMPLATE_FILES");
            return new AiCodegenResult(false, "DETERMINISTIC_TEMPLATE", null, null, warnings, List.of(), "模板代码文件为空，未调用 Development LLM。", Map.of());
        }

        Map<String, Object> payload = buildPayload(
                workspaceId,
                projectCode,
                moduleId,
                plan,
                handoffContext,
                generationRoot,
                nodeType,
                nodeName,
                previewRoute,
                safeTemplateFiles,
                remarks
        );

        try {
            Map<String, Object> response = pythonAgentClient.generateDevelopmentNodejsCodeProposal(payload);
            String status = stringValue(response.get("status"));
            String generatorMode = stringValue(response.get("generator_mode"));
            String provider = stringValue(response.get("provider"));
            String model = stringValue(response.get("model"));
            String summary = stringValue(response.get("summary"));
            warnings.addAll(stringList(response.get("warnings")));

            List<DevelopmentCodeFileResponse> aiFiles = sanitizeAiFiles(generationRoot, response.get("files"), warnings);
            List<DevelopmentCodeFileResponse> merged = mergeWithTemplate(generationRoot, safeTemplateFiles, aiFiles, warnings);
            boolean aiGenerated = "AI_GENERATED".equalsIgnoreCase(status) && !aiFiles.isEmpty();

            if (aiGenerated) {
                warnings.add("Development Agent 代码提案已通过 Python Dev Codegen Agent 生成，模型路由任务：development.codegen。");
                if (!provider.isBlank() || !model.isBlank()) {
                    warnings.add("Development Agent codegen model: " + safeText(provider, "unknown") + "/" + safeText(model, "unknown"));
                }
            } else {
                throw new ResponseStatusException(
                        strictGateHttpStatus(generatorMode, warnings),
                        buildAgentFailureMessage(
                                status,
                                generatorMode,
                                provider,
                                model,
                                summary,
                                warnings,
                                mapValue(response.get("usage")),
                                mapValue(response.get("raw"))
                        )
                );
            }

            return new AiCodegenResult(
                    aiGenerated,
                    safeText(generatorMode, aiGenerated ? "LLM_CODEGEN_WITH_TEMPLATE_GUARDRAILS" : "DETERMINISTIC_TEMPLATE_FALLBACK"),
                    provider.isBlank() ? null : provider,
                    model.isBlank() ? null : model,
                    dedupe(warnings),
                    merged,
                    safeText(summary, aiGenerated ? "已通过 Development LLM 生成 Node.js 代码贡献提案。" : "已使用确定性模板生成 Node.js 代码贡献提案。"),
                    mapValue(response.get("usage"))
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "DEV_CODEGEN_AGENT_UNAVAILABLE: Python Dev Codegen Agent 端点不可用，代码生成必须由 Agent 完成，未写入模板假代码。原因：" + safeException(exception),
                    exception
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "DEV_CODEGEN_AGENT_FAILED: Python Dev Codegen Agent 生成失败，代码生成必须由 Agent 完成，未写入模板假代码。原因：" + safeException(exception),
                    exception
            );
        }
    }



    public AiCodegenResult repairNodejsCodeContribution(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentPlanResponse plan,
            DevelopmentHandoffContextResponse handoffContext,
            String generationRoot,
            String nodeType,
            String nodeName,
            List<DevelopmentCodebaseAcceptanceItemResponse> failedItems,
            List<String> logTail,
            List<DevelopmentCodeFileResponse> currentFiles,
            String remarks
    ) {
        List<String> warnings = new ArrayList<>();
        List<DevelopmentCodeFileResponse> safeCurrentFiles = sanitizeTemplateFiles(generationRoot, currentFiles, warnings);

        if (safeCurrentFiles.isEmpty()) {
            warnings.add("AI_REPAIR_SKIPPED_EMPTY_CURRENT_FILES");
            return new AiCodegenResult(false, "REPAIR_CONTEXT_EMPTY", null, null, warnings, List.of(), "没有可用于修复的当前代码文件。", Map.of());
        }

        Map<String, Object> payload = buildRepairPayload(
                workspaceId,
                projectCode,
                moduleId,
                plan,
                handoffContext,
                generationRoot,
                nodeType,
                nodeName,
                failedItems,
                logTail,
                safeCurrentFiles,
                remarks
        );

        try {
            Map<String, Object> response = pythonAgentClient.generateDevelopmentNodejsRepairProposal(payload);
            String status = stringValue(response.get("status"));
            String generatorMode = stringValue(response.get("generator_mode"));
            String provider = stringValue(response.get("provider"));
            String model = stringValue(response.get("model"));
            String summary = stringValue(response.get("summary"));
            warnings.addAll(stringList(response.get("warnings")));

            List<DevelopmentCodeFileResponse> repairFiles = sanitizeAiFiles(generationRoot, response.get("files"), warnings);
            boolean aiGenerated = status.toUpperCase(Locale.ROOT).contains("AI_REPAIR") && !repairFiles.isEmpty();

            if (aiGenerated) {
                warnings.add("Development Agent 已基于验收失败日志生成修复提案，模型路由任务：development.codegen。");
                if (!provider.isBlank() || !model.isBlank()) {
                    warnings.add("Development Agent repair model: " + safeText(provider, "unknown") + "/" + safeText(model, "unknown"));
                }
            } else {
                warnings.add("Development Agent 修复模型未返回可写入文件。status=" + safeText(status, "UNKNOWN"));
            }

            return new AiCodegenResult(
                    aiGenerated,
                    safeText(generatorMode, aiGenerated ? "LLM_ACCEPTANCE_REPAIR_WITH_GUARDRAILS" : "REPAIR_EMPTY"),
                    provider.isBlank() ? null : provider,
                    model.isBlank() ? null : model,
                    dedupe(warnings),
                    repairFiles,
                    safeText(summary, aiGenerated ? "已生成验收失败修复提案。" : "未生成可用修复文件。"),
                    mapValue(response.get("usage"))
            );
        } catch (RestClientException exception) {
            warnings.add("Development Agent Python repair endpoint unavailable: " + safeException(exception));
            return new AiCodegenResult(false, "REPAIR_ENDPOINT_UNAVAILABLE", null, null, dedupe(warnings), List.of(), "修复模型接口不可用，未生成修复文件。", Map.of());
        } catch (Exception exception) {
            warnings.add("Development Agent AI repair failed: " + safeException(exception));
            return new AiCodegenResult(false, "REPAIR_FAILED", null, null, dedupe(warnings), List.of(), "自动修复失败，未生成修复文件。", Map.of());
        }
    }

    private Map<String, Object> buildRepairPayload(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentPlanResponse plan,
            DevelopmentHandoffContextResponse handoffContext,
            String generationRoot,
            String nodeType,
            String nodeName,
            List<DevelopmentCodebaseAcceptanceItemResponse> failedItems,
            List<String> logTail,
            List<DevelopmentCodeFileResponse> currentFiles,
            String remarks
    ) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeId", moduleId == null ? null : moduleId.toString());
        node.put("nodeType", nodeType);
        node.put("nodeName", nodeName);
        node.put("nodeKey", plan.moduleKey());
        node.put("moduleType", plan.moduleType());
        node.put("sourceArtifactTypes", plan.sourceArtifactTypes());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", UUID.randomUUID().toString());
        payload.put("workspace_id", workspaceId == null ? null : workspaceId.toString());
        payload.put("project_id", plan.projectId() == null ? null : plan.projectId().toString());
        payload.put("project_code", projectCode);
        payload.put("module_id", moduleId == null ? null : moduleId.toString());
        payload.put("target_stack", plan.targetStack());
        payload.put("generation_root", generationRoot);
        payload.put("node", node);
        payload.put("handoff_context", handoffContext == null ? Map.of() : handoffContext);
        payload.put("acceptance_summary", failedItems == null || failedItems.isEmpty() ? "未提供失败项。" : "验收失败项 " + failedItems.size() + " 个。");
        payload.put("failed_items", failedItems == null ? List.of() : failedItems.stream().map(this::acceptanceItemToPayload).toList());
        payload.put("log_tail", logTail == null ? List.of() : logTail);
        payload.put("current_files", currentFiles.stream().map(this::fileToPayload).toList());
        payload.put("remarks", remarks);
        return payload;
    }

    private Map<String, Object> acceptanceItemToPayload(DevelopmentCodebaseAcceptanceItemResponse item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (item == null) {
            return payload;
        }
        payload.put("id", item.id());
        payload.put("phase", item.phase());
        payload.put("title", item.title());
        payload.put("status", item.status());
        payload.put("required", item.required());
        payload.put("passed", item.passed());
        payload.put("command", item.command());
        payload.put("exitCode", item.exitCode());
        payload.put("summary", item.summary());
        payload.put("details", item.details());
        payload.put("logTail", item.logTail());
        return payload;
    }


    private HttpStatus strictGateHttpStatus(
            String generatorMode,
            List<String> warnings
    ) {
        if ("A9_10_13_2_STRICT_PAGE_UI_CONTRACT_GATE".equals(safeText(generatorMode, ""))
                || "A9_10_13_3_MODULE_PROTOTYPE_SNAPSHOT_GATE".equals(safeText(generatorMode, ""))
                || containsWarning(warnings, "A9.10.13.2_STRICT_PAGE_UI_CONTRACT_GATE_BLOCKED")
                || containsWarning(warnings, "A9.10.13.3_MODULE_PROTOTYPE_SNAPSHOT_GATE_BLOCKED")
                || containsWarning(warnings, "MISSING_CONFIRMED_PAGE_UI_CONTRACT")
                || containsWarning(warnings, "MISSING_MODULE_PROTOTYPE_PAGE_CONTRACT")) {
            return HttpStatus.PRECONDITION_FAILED;
        }

        return HttpStatus.BAD_GATEWAY;
    }

    private boolean containsWarning(
            List<String> warnings,
            String token
    ) {
        if (warnings == null || token == null || token.isBlank()) {
            return false;
        }

        for (String warning : warnings) {
            if (warning != null && warning.contains(token)) {
                return true;
            }
        }

        return false;
    }

    private String buildAgentFailureMessage(
            String status,
            String generatorMode,
            String provider,
            String model,
            String summary,
            List<String> warnings,
            Map<String, Object> usage,
            Map<String, Object> raw
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("DEV_CODEGEN_AGENT_FAILED: Development Codegen Agent 未返回可用业务代码文件，系统不会用 Java 确定性模板冒充最终业务代码");
        parts.add("status=" + safeText(status, "UNKNOWN"));

        if (generatorMode != null && !generatorMode.isBlank()) {
            parts.add("generatorMode=" + generatorMode);
        }

        if (provider != null && !provider.isBlank()) {
            parts.add("provider=" + provider);
        }

        if (model != null && !model.isBlank()) {
            parts.add("model=" + model);
        }

        if (summary != null && !summary.isBlank()) {
            parts.add("summary=" + summary);
        }

        if (raw != null && !raw.isEmpty()) {
            Object rawError = raw.get("error");
            Object rawMessage = raw.get("message");

            if (rawError != null && !String.valueOf(rawError).isBlank()) {
                parts.add("raw.error=" + rawError);
            }

            if (rawMessage != null && !String.valueOf(rawMessage).isBlank()) {
                parts.add("raw.message=" + rawMessage);
            }
        }

        if (usage != null && !usage.isEmpty()) {
            Object usageProvider = usage.get("provider");
            Object usageModel = usage.get("model");
            Object success = usage.get("success");

            if (usageProvider != null) {
                parts.add("usage.provider=" + usageProvider);
            }
            if (usageModel != null) {
                parts.add("usage.model=" + usageModel);
            }
            if (success != null) {
                parts.add("usage.success=" + success);
            }
        }

        if (warnings != null && !warnings.isEmpty()) {
            int limit = Math.min(warnings.size(), 8);
            for (int index = 0; index < limit; index++) {
                String warning = warnings.get(index);
                if (warning != null && !warning.isBlank()) {
                    parts.add("warning[" + index + "]=" + warning);
                }
            }
        }

        return truncateForHttp(String.join("；", parts), 1800);
    }

    private AiCodegenResult fallback(List<DevelopmentCodeFileResponse> safeTemplateFiles, List<String> warnings) {
        return new AiCodegenResult(
                false,
                "PROJECT_SCAFFOLD_ONLY_NOT_BUSINESS_CODE",
                null,
                null,
                dedupe(warnings),
                safeTemplateFiles,
                "仅生成工程脚手架，不代表业务代码已由 Development Codegen Agent 完成。",
                Map.of()
        );
    }

    private Map<String, Object> buildPayload(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentPlanResponse plan,
            DevelopmentHandoffContextResponse handoffContext,
            String generationRoot,
            String nodeType,
            String nodeName,
            String previewRoute,
            List<DevelopmentCodeFileResponse> files,
            String remarks
    ) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeId", moduleId == null ? null : moduleId.toString());
        node.put("nodeType", nodeType);
        node.put("nodeName", nodeName);
        node.put("nodeKey", plan.moduleKey());
        node.put("moduleType", plan.moduleType());
        node.put("previewRoute", previewRoute);
        node.put("sourceArtifactTypes", plan.sourceArtifactTypes());

        Map<String, Object> developmentBuildContext =
                handoffContext == null
                        || handoffContext.developmentBuildContext() == null
                        ? Map.of()
                        : handoffContext.developmentBuildContext();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", UUID.randomUUID().toString());
        payload.put("workspace_id", workspaceId == null ? null : workspaceId.toString());
        payload.put("project_id", plan.projectId() == null ? null : plan.projectId().toString());
        payload.put("project_code", projectCode);
        payload.put("module_id", moduleId == null ? null : moduleId.toString());
        payload.put("target_stack", plan.targetStack());
        payload.put("generation_root", generationRoot);
        payload.put("node", node);
        payload.put("handoff_context", handoffContext == null ? Map.of() : handoffContext);
        payload.put("development_build_context", developmentBuildContext);
        payload.put("deterministic_files", files.stream().map(this::fileToPayload).toList());
        payload.put("remarks", remarks);
        return payload;
    }

    private Map<String, Object> fileToPayload(DevelopmentCodeFileResponse file) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file_path", file.filePath());
        payload.put("file_type", file.fileType());
        payload.put("action", file.action());
        payload.put("source", file.source());
        payload.put("description", file.description());
        payload.put("language", file.language());
        payload.put("content", file.content());
        payload.put("priority", file.priority());
        return payload;
    }

    private List<DevelopmentCodeFileResponse> sanitizeTemplateFiles(
            String generationRoot,
            List<DevelopmentCodeFileResponse> sourceFiles,
            List<String> warnings
    ) {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return List.of();
        }

        List<DevelopmentCodeFileResponse> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int totalChars = 0;

        for (DevelopmentCodeFileResponse file : sourceFiles) {
            if (file == null) {
                continue;
            }

            String path = normalizePath(file.filePath());
            if (!isSafeFilePath(generationRoot, path)) {
                warnings.add("TEMPLATE_CODEGEN_SKIPPED_UNSAFE_PATH:" + file.filePath());
                continue;
            }
            if (!seen.add(path)) {
                continue;
            }

            String content = file.content() == null ? "" : file.content();
            totalChars += content.length();
            if (totalChars > MAX_TOTAL_CONTENT_CHARS) {
                warnings.add("TEMPLATE_CODEGEN_TOTAL_SIZE_LIMIT_REACHED");
                break;
            }

            result.add(new DevelopmentCodeFileResponse(
                    path,
                    safeChoice(file.fileType(), ALLOWED_FILE_TYPES, "UNKNOWN"),
                    safeChoice(file.action(), ALLOWED_ACTIONS, "CREATE_OR_UPDATE"),
                    safeText(file.source(), "DEV_HANDOFF_PACKAGE"),
                    safeText(file.description(), ""),
                    safeText(file.language(), "text"),
                    content,
                    file.priority()
            ));
        }

        return sortFiles(result);
    }

    private List<DevelopmentCodeFileResponse> sanitizeAiFiles(
            String generationRoot,
            Object rawFiles,
            List<String> warnings
    ) {
        if (!(rawFiles instanceof List<?> list)) {
            warnings.add("AI_CODEGEN_RESPONSE_FILES_NOT_LIST");
            return List.of();
        }

        List<DevelopmentCodeFileResponse> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int totalChars = 0;

        for (Object item : list) {
            if (result.size() >= MAX_AI_FILES) {
                warnings.add("AI_CODEGEN_FILE_LIMIT_REACHED");
                break;
            }

            Map<String, Object> map = mapValue(item);
            if (map.isEmpty()) {
                warnings.add("AI_CODEGEN_SKIPPED_NON_OBJECT_FILE");
                continue;
            }

            String path = normalizePath(firstString(map, "file_path", "filePath"));
            if (!isSafeFilePath(generationRoot, path)) {
                warnings.add("AI_CODEGEN_SKIPPED_UNSAFE_PATH:" + firstString(map, "file_path", "filePath"));
                continue;
            }
            if (path.endsWith("/codemind.generated.json")) {
                warnings.add("AI_CODEGEN_SKIPPED_MANIFEST_FILE");
                continue;
            }
            if (!seen.add(path)) {
                warnings.add("AI_CODEGEN_SKIPPED_DUPLICATE_FILE:" + path);
                continue;
            }

            String content = firstString(map, "content");
            if (content.length() > MAX_FILE_CONTENT_CHARS) {
                warnings.add("AI_CODEGEN_TRUNCATED_LARGE_FILE:" + path);
                content = content.substring(0, MAX_FILE_CONTENT_CHARS);
            }

            totalChars += content.length();
            if (totalChars > MAX_TOTAL_CONTENT_CHARS) {
                warnings.add("AI_CODEGEN_TOTAL_SIZE_LIMIT_REACHED");
                break;
            }

            result.add(new DevelopmentCodeFileResponse(
                    path,
                    safeChoice(firstString(map, "file_type", "fileType"), ALLOWED_FILE_TYPES, "UNKNOWN"),
                    safeChoice(firstString(map, "action"), ALLOWED_ACTIONS, "CREATE_OR_UPDATE"),
                    safeText(firstString(map, "source"), "DEV_HANDOFF_PACKAGE"),
                    safeText(firstString(map, "description"), ""),
                    safeText(firstString(map, "language"), "text"),
                    content,
                    intValue(map.get("priority"), 100)
            ));
        }

        return sortFiles(result);
    }

    private List<DevelopmentCodeFileResponse> mergeWithTemplate(
            String generationRoot,
            List<DevelopmentCodeFileResponse> templateFiles,
            List<DevelopmentCodeFileResponse> aiFiles,
            List<String> warnings
    ) {
        Map<String, DevelopmentCodeFileResponse> byPath = new LinkedHashMap<>();
        for (DevelopmentCodeFileResponse file : templateFiles) {
            byPath.put(file.filePath(), file);
        }

        int aiUpdatedTemplateCount = 0;
        int aiAddedFileCount = 0;

        for (DevelopmentCodeFileResponse aiFile : aiFiles) {
            DevelopmentCodeFileResponse template = byPath.get(aiFile.filePath());
            if (template == null) {
                byPath.put(aiFile.filePath(), aiFile);
                aiAddedFileCount++;
                continue;
            }

            byPath.put(aiFile.filePath(), new DevelopmentCodeFileResponse(
                    template.filePath(),
                    safeText(aiFile.fileType(), template.fileType()),
                    "CREATE_OR_KEEP".equals(template.action()) ? template.action() : safeText(aiFile.action(), template.action()),
                    safeText(aiFile.source(), template.source()),
                    safeText(aiFile.description(), template.description()),
                    safeText(aiFile.language(), template.language()),
                    aiFile.content() == null ? template.content() : aiFile.content(),
                    template.priority()
            ));
            aiUpdatedTemplateCount++;
        }

        warnings.add("AI_CODEGEN_UPDATED_TEMPLATE_FILES=" + aiUpdatedTemplateCount);
        warnings.add("AI_CODEGEN_ADDED_EXTRA_FILES=" + aiAddedFileCount);
        return sortFiles(new ArrayList<>(byPath.values()));
    }

    private boolean isSafeFilePath(String generationRoot, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String path = normalizePath(filePath);
        String root = normalizePath(generationRoot);
        if (path.contains(":") || path.startsWith("/") || path.startsWith("~")) {
            return false;
        }
        for (String part : path.split("/")) {
            if ("..".equals(part)) {
                return false;
            }
        }
        String wrapped = "/" + path.toLowerCase(Locale.ROOT) + "/";
        if (wrapped.contains("/.git/") || wrapped.contains("/node_modules/") || wrapped.contains("/target/") || wrapped.contains("/dist/") || wrapped.contains("/build/") || wrapped.contains("/.venv/") || wrapped.contains("/venv/")) {
            return false;
        }
        return path.equals(root) || path.startsWith(root + "/");
    }

    private String normalizePath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private List<DevelopmentCodeFileResponse> sortFiles(List<DevelopmentCodeFileResponse> files) {
        return files.stream()
                .sorted(Comparator.comparingInt(DevelopmentCodeFileResponse::priority).thenComparing(DevelopmentCodeFileResponse::filePath))
                .toList();
    }

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private List<String> dedupe(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private String safeChoice(String value, Set<String> allowed, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeException(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return truncateForHttp(message, 1200);
    }

    private String truncateForHttp(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.length() > maxLength ? normalized.substring(0, maxLength) + "...[TRUNCATED]" : normalized;
    }

    public record AiCodegenResult(
            boolean aiGenerated,
            String generatorMode,
            String provider,
            String model,
            List<String> warnings,
            List<DevelopmentCodeFileResponse> files,
            String summary,
            Map<String, Object> usage
    ) {
    }
}

