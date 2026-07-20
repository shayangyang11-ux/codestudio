package com.codemind.platform.agent.requirement.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record UiContractValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        Map<String, Object> metrics
) {

    public void assertValid() {
        if (valid) {
            return;
        }

        String message = errors == null || errors.isEmpty()
                ? "UI_CONTRACT 校验失败。"
                : String.join("；", errors);

        throw new IllegalStateException(message);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("errors", errors == null ? List.of() : errors);
        result.put("warnings", warnings == null ? List.of() : warnings);
        result.put("metrics", metrics == null ? Map.of() : metrics);
        return result;
    }
}
