package com.codemind.platform.development;

import java.util.List;

public record DevelopmentTargetStackResponse(
        String stackCode,
        String displayName,
        String lifecycleStatus,
        boolean available,
        String description,
        List<String> capabilities,
        List<String> runtimeCommands,
        List<String> notes
) {
}
