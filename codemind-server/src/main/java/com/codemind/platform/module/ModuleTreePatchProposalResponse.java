package com.codemind.platform.module;

import java.util.Map;

public record ModuleTreePatchProposalResponse(
        String description,
        String source,
        Map<String, Object> proposal,
        String markdown,
        boolean requiresUserConfirmation
) {
}
