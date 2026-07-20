package com.codemind.platform.development;

public record DevelopmentPlanSnapshotQueryResponse(
        boolean exists,
        ConfirmedDevelopmentPlanSnapshotResponse snapshot
) {
}
