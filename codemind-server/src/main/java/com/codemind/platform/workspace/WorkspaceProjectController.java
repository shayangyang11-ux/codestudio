package com.codemind.platform.workspace;

import com.codemind.platform.workspace.dto.CreateProjectRequest;
import com.codemind.platform.workspace.dto.ProjectSummaryResponse;
import com.codemind.platform.workspace.dto.ProjectWorkspaceStateResponse;
import com.codemind.platform.workspace.dto.SaveProjectWorkspaceStateRequest;
import com.codemind.platform.workspace.dto.WorkspaceSummaryResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class WorkspaceProjectController {

    private final WorkspaceProjectService workspaceProjectService;

    public WorkspaceProjectController(
            WorkspaceProjectService workspaceProjectService
    ) {
        this.workspaceProjectService = workspaceProjectService;
    }

    @GetMapping("/me/workspaces")
    public List<WorkspaceSummaryResponse> listCurrentUserWorkspaces() {
        return workspaceProjectService.listCurrentUserWorkspaces();
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public List<ProjectSummaryResponse> listProjectsByWorkspace(
            @PathVariable UUID workspaceId
    ) {
        return workspaceProjectService.listProjectsByWorkspace(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    public ProjectSummaryResponse createProject(
            @PathVariable UUID workspaceId,
            @RequestBody CreateProjectRequest request
    ) {
        return workspaceProjectService.createProject(
                workspaceId,
                request
        );
    }

    @GetMapping("/workspaces/{workspaceId}/projects/{projectCode}/workspace-state")
    public ProjectWorkspaceStateResponse getProjectWorkspaceState(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode
    ) {
        return workspaceProjectService.getProjectWorkspaceState(
                workspaceId,
                projectCode
        );
    }

    @PutMapping("/workspaces/{workspaceId}/projects/{projectCode}/workspace-state")
    public ProjectWorkspaceStateResponse saveProjectWorkspaceState(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody SaveProjectWorkspaceStateRequest request
    ) {
        return workspaceProjectService.saveProjectWorkspaceState(
                workspaceId,
                projectCode,
                request
        );
    }
}