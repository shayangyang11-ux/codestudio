package com.codemind.platform.task;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AgentTaskErrorClassifier {

    public AgentTaskErrorDiagnosis diagnose(
            String errorCode,
            String errorMessage
    ) {
        String originalCode = safe(errorCode, "AGENT_TASK_FAILED");
        String originalMessage = safe(errorMessage, "任务执行失败。");

        String text = (originalCode + " " + originalMessage)
                .toLowerCase(Locale.ROOT);

        if (containsAny(
                text,
                "insufficient balance",
                "insufficient_quota",
                "insufficient quota",
                "quota exceeded",
                "credit balance",
                "account balance",
                "balance not enough",
                "not enough balance",
                "no enough balance",
                "余额不足",
                "账户余额不足",
                "额度不足",
                "配额不足",
                "欠费",
                "充值"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "LLM_QUOTA_EXHAUSTED",
                    "模型额度不足，请检查 DeepSeek 账户余额、API Key 配额或计费状态后重试。",
                    originalCode,
                    originalMessage,
                    true,
                    "请充值或更换可用的模型 API Key 后点击重试。"
            );
        }

        if (containsAny(
                text,
                "invalid api key",
                "api key invalid",
                "apikey invalid",
                "unauthorized",
                "authentication",
                "401",
                "403",
                "permission denied",
                "forbidden",
                "鉴权失败",
                "认证失败",
                "api key 无效",
                "apikey 无效",
                "无权限"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "LLM_AUTHENTICATION_FAILED",
                    "模型服务鉴权失败，请检查 DeepSeek API Key 是否正确、是否已启用、是否具备调用权限。",
                    originalCode,
                    originalMessage,
                    false,
                    "请更新模型 API Key 或检查供应商控制台权限配置。"
            );
        }

        if (containsAny(
                text,
                "rate limit",
                "too many requests",
                "429",
                "requests per minute",
                "rpm",
                "tpm",
                "限流",
                "请求过快",
                "频率限制"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "LLM_RATE_LIMITED",
                    "模型服务请求被限流，请稍后重试或降低并发任务数量。",
                    originalCode,
                    originalMessage,
                    true,
                    "建议稍后重试，或降低批量任务并发量。"
            );
        }

        if (containsAny(
                text,
                "timeout",
                "timed out",
                "read timed out",
                "connect timed out",
                "socket timeout",
                "gateway timeout",
                "504",
                "超时"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "MODEL_SERVICE_TIMEOUT",
                    "模型或 Agent 服务响应超时，请稍后重试；如果频繁出现，请检查 Agent 服务和模型供应商状态。",
                    originalCode,
                    originalMessage,
                    true,
                    "请稍后点击重试，必要时检查 Python Agent 服务和模型网络连接。"
            );
        }

        if (containsAny(
                text,
                "context length",
                "maximum context",
                "max context",
                "token limit",
                "tokens exceed",
                "too many tokens",
                "context_length_exceeded",
                "上下文过长",
                "token 超限",
                "超过上下文",
                "超过最大长度"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "LLM_CONTEXT_TOO_LONG",
                    "输入上下文过长，模型无法处理。请减少需求文本、缩小模块范围或拆分生成任务后重试。",
                    originalCode,
                    originalMessage,
                    false,
                    "建议缩短需求文本或按模块拆分生成。"
            );
        }

        if (containsAny(
                text,
                "json parse",
                "json parsing",
                "invalid json",
                "deserialize",
                "deserialization",
                "mismatchedinputexception",
                "jsondecodeerror",
                "parse error",
                "格式错误",
                "json 解析",
                "无法解析"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "AGENT_OUTPUT_FORMAT_INVALID",
                    "模型输出格式不符合系统要求，系统已保留失败任务，可点击重试。",
                    originalCode,
                    originalMessage,
                    true,
                    "建议直接重试；如果多次失败，请调整 Prompt 或检查模型输出模板。"
            );
        }

        if (containsAny(
                text,
                "connection refused",
                "connectexception",
                "connection reset",
                "connection aborted",
                "no route to host",
                "python agent",
                "agent service",
                "服务不可用",
                "连接失败",
                "连接被拒绝"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "PYTHON_AGENT_UNAVAILABLE",
                    "Python Agent 服务不可用或连接失败，请检查 Agent 服务是否启动、网络是否可达。",
                    originalCode,
                    originalMessage,
                    true,
                    "请启动或重启 Python Agent 服务后点击重试。"
            );
        }

        if (containsAny(
                text,
                "dependency",
                "missing upstream",
                "upstream",
                "前置资产",
                "依赖",
                "缺少上游"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "AGENT_TASK_DEPENDENCY_MISSING",
                    "任务缺少上游依赖资产，请先生成或确认前置需求资产后重试。",
                    originalCode,
                    originalMessage,
                    true,
                    "请先生成前置资产，再重试当前任务。"
            );
        }

        if (containsAny(
                text,
                "500",
                "internal server error",
                "server error",
                "bad gateway",
                "502",
                "503"
        )) {
            return new AgentTaskErrorDiagnosis(
                    "AGENT_OR_MODEL_SERVER_ERROR",
                    "Agent 或模型服务执行异常，请稍后重试；如果反复失败，请查看任务详情和后端日志。",
                    originalCode,
                    originalMessage,
                    true,
                    "建议先重试；若多次失败，请查看任务执行日志。"
            );
        }

        return new AgentTaskErrorDiagnosis(
                normalizeFallbackCode(originalCode),
                buildFallbackMessage(originalMessage),
                originalCode,
                originalMessage,
                true,
                "请查看任务详情或稍后重试。"
        );
    }

    private String safe(
            String value,
            String fallback
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }

    private boolean containsAny(
            String text,
            String... keywords
    ) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private String normalizeFallbackCode(String originalCode) {
        if (originalCode == null || originalCode.isBlank()) {
            return "AGENT_TASK_FAILED";
        }

        String normalized = originalCode.trim();

        if (normalized.length() > 128) {
            return normalized.substring(0, 128);
        }

        return normalized;
    }

    private String buildFallbackMessage(String originalMessage) {
        if (originalMessage == null || originalMessage.isBlank()) {
            return "任务执行失败，请稍后重试；如果多次失败，请查看任务详情和后端日志。";
        }

        String message = originalMessage.trim();

        if (message.length() > 300) {
            message = message.substring(0, 300) + "...";
        }

        return "任务执行失败：" + message;
    }
}