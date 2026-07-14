package com.codemind.platform.module;

import java.util.ArrayList;
import java.util.List;

public class ApplyModuleTreePatchRequest {

    private String description;
    private String source;
    private List<ModuleTreePatchOperationRequest> operations = new ArrayList<>();

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<ModuleTreePatchOperationRequest> getOperations() {
        return operations;
    }

    public void setOperations(List<ModuleTreePatchOperationRequest> operations) {
        this.operations = operations;
    }
}