package com.codemind.platform.assistant;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/assistant/asset-edit-proposals")
public class AssistantAssetEditProposalController {

    private final AssistantAssetEditProposalService assistantAssetEditProposalService;

    public AssistantAssetEditProposalController(
            AssistantAssetEditProposalService assistantAssetEditProposalService
    ) {
        this.assistantAssetEditProposalService = assistantAssetEditProposalService;
    }

    @PostMapping("/propose")
    public AssistantMessageResponse proposeAssetEdit(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody AssistantAssetEditProposalRequest request
    ) {
        return assistantAssetEditProposalService.propose(
                workspaceId,
                projectCode,
                request
        );
    }

    @PostMapping("/confirm")
    public ConfirmAssistantAssetEditProposalResponse confirmAssetEdit(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody ConfirmAssistantAssetEditProposalRequest request
    ) {
        return assistantAssetEditProposalService.confirm(
                workspaceId,
                projectCode,
                request
        );
    }
}
