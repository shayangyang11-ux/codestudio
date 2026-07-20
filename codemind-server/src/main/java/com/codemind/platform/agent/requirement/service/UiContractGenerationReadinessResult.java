package com.codemind.platform.agent.requirement.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UiContractGenerationReadinessResult(
        boolean ready,
        UUID pageId,
        String pageKey,
        String pageName,
        String pageKind,
        boolean pageSpecAvailable,
        int featureCount,
        int completeFeatureCount,
        List<FeatureReadinessItem> features,
        List<String> blockers,
        List<String> warnings
) {

    public void assertReady() {
        if (ready) {
            return;
        }

        String message = blockers == null || blockers.isEmpty()
                ? "当前 PAGE 尚未满足交互原型生成条件。"
                : String.join("；", blockers);

        throw new IllegalStateException(message);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ready", ready);
        result.put("pageId", pageId == null ? null : pageId.toString());
        result.put("pageKey", pageKey);
        result.put("pageName", pageName);
        result.put("pageKind", pageKind);
        result.put("pageSpecAvailable", pageSpecAvailable);
        result.put("featureCount", featureCount);
        result.put("completeFeatureCount", completeFeatureCount);
        result.put(
                "features",
                features == null
                        ? List.of()
                        : features.stream().map(FeatureReadinessItem::toMap).toList()
        );
        result.put("blockers", blockers == null ? List.of() : blockers);
        result.put("warnings", warnings == null ? List.of() : warnings);
        return result;
    }

    public record FeatureReadinessItem(
            UUID featureId,
            String featureKey,
            String featureName,
            boolean complete,
            String definitionSource,
            List<String> missingFields
    ) {

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("featureId", featureId == null ? null : featureId.toString());
            result.put("featureKey", featureKey);
            result.put("featureName", featureName);
            result.put("complete", complete);
            result.put("definitionSource", definitionSource);
            result.put(
                    "missingFields",
                    missingFields == null ? List.of() : missingFields
            );
            return result;
        }
    }
}
