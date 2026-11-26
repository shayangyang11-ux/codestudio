package com.codemind.platform.assistant;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/assistant")
public class WorkspaceProjectAssistantController {

    private final AssistantMessageService assistantMessageService;
    private final AssistantAttachmentService assistantAttachmentService;

    public WorkspaceProjectAssistantController(
            AssistantMessageService assistantMessageService,
            AssistantAttachmentService assistantAttachmentService
    ) {
        this.assistantMessageService = assistantMessageService;
        this.assistantAttachmentService = assistantAttachmentService;
    }

    @GetMapping("/messages")
    public List<AssistantConversationMessageResponse> listMessages(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return assistantMessageService.listMessages(
                workspaceId,
                projectCode,
                limit
        );
    }

    @PostMapping("/messages/archive")
    public AssistantConversationArchiveResponse archiveMessages(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode
    ) {
        return assistantMessageService.archiveMessages(
                workspaceId,
                projectCode
        );
    }

    @PostMapping("/messages")
    public AssistantMessageResponse sendMessage(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody AssistantMessageRequest request
    ) {
        return assistantMessageService.handleMessage(
                workspaceId,
                projectCode,
                request
        );
    }

    @PostMapping("/task-notifications")
    public AssistantMessageResponse createTaskNotification(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody AssistantTaskNotificationRequest request
    ) {
        return assistantMessageService.createTaskNotification(
                workspaceId,
                projectCode,
                request
        );
    }

    @PostMapping(
            value = "/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AssistantAttachmentUploadResponse uploadAttachment(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            return assistantAttachmentService.uploadAttachment(
                    workspaceId,
                    projectCode,
                    file
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "附件上传失败：" + ex.getClass().getSimpleName() + " - " + ex.getMessage(),
                    ex
            );
        }
    }
}