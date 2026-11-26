package com.codemind.platform.development;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules/{moduleId}/development")
public class DevelopmentCodeProjectController {

    private final DevelopmentCodeProjectService developmentCodeProjectService;
    private final DevelopmentCodebaseWorkspaceService developmentCodebaseWorkspaceService;
    private final DevelopmentRuntimeManagerService developmentRuntimeManagerService;
    private final DevelopmentCodebaseExportService developmentCodebaseExportService;
    private final DevelopmentCodebaseSnapshotService developmentCodebaseSnapshotService;
    private final DevelopmentCodebaseAcceptanceService developmentCodebaseAcceptanceService;
    private final DevelopmentCodebaseBindingService developmentCodebaseBindingService;
    private final DevelopmentCodebaseDeploymentService developmentCodebaseDeploymentService;
    private final DevelopmentCodebaseRepairService developmentCodebaseRepairService;

    public DevelopmentCodeProjectController(
            DevelopmentCodeProjectService developmentCodeProjectService,
            DevelopmentCodebaseWorkspaceService developmentCodebaseWorkspaceService,
            DevelopmentRuntimeManagerService developmentRuntimeManagerService,
            DevelopmentCodebaseExportService developmentCodebaseExportService,
            DevelopmentCodebaseSnapshotService developmentCodebaseSnapshotService,
            DevelopmentCodebaseAcceptanceService developmentCodebaseAcceptanceService,
            DevelopmentCodebaseBindingService developmentCodebaseBindingService,
            DevelopmentCodebaseDeploymentService developmentCodebaseDeploymentService,
            DevelopmentCodebaseRepairService developmentCodebaseRepairService
    ) {
        this.developmentCodeProjectService = developmentCodeProjectService;
        this.developmentCodebaseWorkspaceService = developmentCodebaseWorkspaceService;
        this.developmentRuntimeManagerService = developmentRuntimeManagerService;
        this.developmentCodebaseExportService = developmentCodebaseExportService;
        this.developmentCodebaseSnapshotService = developmentCodebaseSnapshotService;
        this.developmentCodebaseAcceptanceService = developmentCodebaseAcceptanceService;
        this.developmentCodebaseBindingService = developmentCodebaseBindingService;
        this.developmentCodebaseDeploymentService = developmentCodebaseDeploymentService;
        this.developmentCodebaseRepairService = developmentCodebaseRepairService;
    }

    @PostMapping("/handoff/code-projects/generate")
    public DevelopmentCodeProjectResponse generateCodeProject(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) GenerateDevelopmentCodeProjectRequest request
    ) {
        return developmentCodeProjectService.generateCodeProject(
                workspaceId,
                projectCode,
                moduleId,
                request == null
                        ? new GenerateDevelopmentCodeProjectRequest(null, null, null)
                        : request
        );
    }

    @PostMapping("/handoff/code-projects/materialize")
    public DevelopmentCodeMaterializationResponse materializeCodeProject(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) MaterializeDevelopmentCodeProjectRequest request
    ) {
        return developmentCodeProjectService.materializeCodeProject(
                workspaceId,
                projectCode,
                moduleId,
                request == null
                        ? new MaterializeDevelopmentCodeProjectRequest(null, null, null, false)
                        : request
        );
    }

    @GetMapping("/codebase/workspace")
    public DevelopmentCodebaseWorkspaceResponse getCodebaseWorkspace(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        return developmentCodebaseWorkspaceService.getWorkspace(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
    }

    @GetMapping("/codebase/files/content")
    public DevelopmentCodebaseFileContentResponse readCodebaseFileContent(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack,
            @RequestParam String filePath
    ) {
        return developmentCodebaseWorkspaceService.readFileContent(
                workspaceId,
                projectCode,
                moduleId,
                targetStack,
                filePath
        );
    }


    @PostMapping("/codebase/files/content")
    public DevelopmentCodebaseFileSaveResponse saveCodebaseFileContent(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody DevelopmentCodebaseFileSaveRequest request
    ) {
        return developmentCodebaseWorkspaceService.saveFileContent(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @GetMapping("/codebase/runtime/status")
    public DevelopmentRuntimeStatusResponse getRuntimeStatus(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        return developmentRuntimeManagerService.getStatus(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
    }

    @PostMapping("/codebase/runtime/install")
    public DevelopmentRuntimeStatusResponse installRuntimeDependencies(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentRuntimeActionRequest request
    ) {
        return developmentRuntimeManagerService.installDependencies(
                workspaceId,
                projectCode,
                moduleId,
                request == null ? new DevelopmentRuntimeActionRequest(null, false) : request
        );
    }

    @PostMapping("/codebase/runtime/start")
    public DevelopmentRuntimeStatusResponse startRuntime(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentRuntimeActionRequest request
    ) {
        return developmentRuntimeManagerService.startRuntime(
                workspaceId,
                projectCode,
                moduleId,
                request == null ? new DevelopmentRuntimeActionRequest(null, false) : request
        );
    }

    @PostMapping("/codebase/runtime/stop")
    public DevelopmentRuntimeStatusResponse stopRuntime(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentRuntimeActionRequest request
    ) {
        return developmentRuntimeManagerService.stopRuntime(
                workspaceId,
                projectCode,
                moduleId,
                request == null ? new DevelopmentRuntimeActionRequest(null, false) : request
        );
    }

    @PostMapping("/codebase/runtime/restart")
    public DevelopmentRuntimeStatusResponse restartRuntime(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentRuntimeActionRequest request
    ) {
        return developmentRuntimeManagerService.restartRuntime(
                workspaceId,
                projectCode,
                moduleId,
                request == null ? new DevelopmentRuntimeActionRequest(null, true) : request
        );
    }

    @GetMapping("/codebase/runtime/logs")
    public DevelopmentRuntimeLogsResponse getRuntimeLogs(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack,
            @RequestParam(required = false) Integer lines
    ) {
        return developmentRuntimeManagerService.getLogs(
                workspaceId,
                projectCode,
                moduleId,
                targetStack,
                lines
        );
    }


    @GetMapping("/codebase/snapshots/status")
    public DevelopmentCodebaseSnapshotStatusResponse getCodebaseSnapshotStatus(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        return developmentCodebaseSnapshotService.getSnapshotStatus(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
    }


    @GetMapping("/codebase/acceptance/status")
    public DevelopmentCodebaseAcceptanceResponse getCodebaseAcceptanceStatus(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        return developmentCodebaseAcceptanceService.getStatus(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
    }

    @PostMapping("/codebase/acceptance/run")
    public DevelopmentCodebaseAcceptanceResponse runCodebaseAcceptance(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentCodebaseAcceptanceRunRequest request
    ) {
        return developmentCodebaseAcceptanceService.runAcceptance(
                workspaceId,
                projectCode,
                moduleId,
                request == null
                        ? new DevelopmentCodebaseAcceptanceRunRequest(null, true, true, true, true)
                        : request
        );
    }


    @PostMapping("/codebase/repair/propose")
    public DevelopmentCodebaseRepairResponse proposeCodebaseRepair(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentCodebaseRepairRunRequest request
    ) {
        return developmentCodebaseRepairService.proposeRepair(
                workspaceId,
                projectCode,
                moduleId,
                request == null
                        ? new DevelopmentCodebaseRepairRunRequest(null, false, null, java.util.List.of(), java.util.Map.of(), null)
                        : request
        );
    }

    @PostMapping("/codebase/repair/apply")
    public DevelopmentCodebaseRepairResponse applyCodebaseRepair(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentCodebaseRepairRunRequest request
    ) {
        return developmentCodebaseRepairService.applyRepair(
                workspaceId,
                projectCode,
                moduleId,
                request == null
                        ? new DevelopmentCodebaseRepairRunRequest(null, false, null, java.util.List.of(), java.util.Map.of(), null)
                        : request
        );
    }



    @GetMapping("/codebase/binding/status")
    public DevelopmentCodebaseBindingStatusResponse getCodebaseBindingStatus(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        return developmentCodebaseBindingService.getStatus(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
    }

    @PostMapping("/codebase/binding/rebind")
    public DevelopmentCodebaseRebindResponse rebindCodebase(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentCodebaseRebindRequest request
    ) {
        return developmentCodebaseBindingService.rebind(
                workspaceId,
                projectCode,
                moduleId,
                request == null
                        ? new DevelopmentCodebaseRebindRequest(null, null, null, null)
                        : request
        );
    }

    @GetMapping("/codebase/deployment/status")
    public DevelopmentCodebaseDeploymentStatusResponse getCodebaseDeploymentStatus(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        return developmentCodebaseDeploymentService.getStatus(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
    }

    @PostMapping("/codebase/deployment/apply")
    public DevelopmentCodebaseDeploymentApplyResponse applyCodebaseDeploymentConfig(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody(required = false) DevelopmentCodebaseDeploymentApplyRequest request
    ) {
        return developmentCodebaseDeploymentService.applyDeploymentConfig(
                workspaceId,
                projectCode,
                moduleId,
                request == null
                        ? new DevelopmentCodebaseDeploymentApplyRequest(null, false, null)
                        : request
        );
    }


    @GetMapping("/codebase/export/status")
    public DevelopmentCodebaseExportStatusResponse getCodebaseExportStatus(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        return developmentCodebaseExportService.getExportStatus(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
    }

    @GetMapping(value = "/codebase/export/download", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadCodebaseZip(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String targetStack
    ) {
        DevelopmentCodebaseExportStatusResponse status = developmentCodebaseExportService.getExportStatus(
                workspaceId,
                projectCode,
                moduleId,
                targetStack
        );
        if (!status.exportable()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    status.blockers() == null || status.blockers().isEmpty()
                            ? "当前代码库暂不能导出。"
                            : String.join("；", status.blockers())
            );
        }

        String fileName = status.estimatedZipName();

        StreamingResponseBody body = outputStream -> developmentCodebaseExportService.writeZip(
                workspaceId,
                projectCode,
                moduleId,
                targetStack,
                outputStream
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(body);
    }
}