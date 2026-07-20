package com.codemind.platform.uicontract;

public record UiContractSnapshotQueryResponse(
        boolean exists,
        ConfirmedUiContractSnapshotResponse snapshot
) {
}
