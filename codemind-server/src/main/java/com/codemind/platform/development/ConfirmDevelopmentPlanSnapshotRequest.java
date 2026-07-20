package com.codemind.platform.development;

import java.util.Map;

public record ConfirmDevelopmentPlanSnapshotRequest(
        Map<String, Object> plan,
        String planChecksum,
        String confirmationNote
) {
}
