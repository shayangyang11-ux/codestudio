package com.codemind.platform.uicontract;

import java.util.UUID;

public record ConfirmUiContractSnapshotRequest(
        UUID artifactId,
        String confirmationNote
) {
}
