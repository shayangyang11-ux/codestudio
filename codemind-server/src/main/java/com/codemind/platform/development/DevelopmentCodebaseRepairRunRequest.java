package com.codemind.platform.development;

import java.util.List;
import java.util.Map;

public record DevelopmentCodebaseRepairRunRequest(
        String targetStack,
        Boolean allowOverwrite,
        DevelopmentCodebaseAcceptanceResponse acceptance,
        List<String> extraLogLines,
        Map<String, Object> context,
        String remarks
) {
}
