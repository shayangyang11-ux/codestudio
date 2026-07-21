package com.codemind.platform.artifact;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/artifacts/manual-edits")
public class ManualArtifactEditController {

    private final ManualArtifactEditService manualArtifactEditService;

    public ManualArtifactEditController(
            ManualArtifactEditService manualArtifactEditService
    ) {
        this.manualArtifactEditService = manualArtifactEditService;
    }

    @PostMapping
    public ManualArtifactEditResponse saveManualDocumentEdit(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody ManualArtifactEditRequest request
    ) {
        return manualArtifactEditService.saveManualDocumentEdit(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }
}
