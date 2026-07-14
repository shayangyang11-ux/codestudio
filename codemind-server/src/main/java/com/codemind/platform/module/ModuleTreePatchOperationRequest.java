package com.codemind.platform.module;

import java.util.Map;
import java.util.UUID;

public class ModuleTreePatchOperationRequest {

    private String op;
    private UUID nodeId;
    private UUID parentId;
    private UUID newParentId;
    private String moduleName;
    private String moduleKey;
    private String moduleType;
    private Integer sortOrder;
    private Map<String, Object> extra;

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getNewParentId() {
        return newParentId;
    }

    public void setNewParentId(UUID newParentId) {
        this.newParentId = newParentId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}