package com.codemind.platform.module;

import java.util.UUID;

public class MoveProjectModuleNodeRequest {

    private UUID parentId;
    private Integer sortOrder;

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}