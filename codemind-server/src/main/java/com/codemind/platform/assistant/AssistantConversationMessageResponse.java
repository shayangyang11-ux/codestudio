package com.codemind.platform.assistant;

import com.codemind.platform.module.ModuleTreePatchProposalResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssistantConversationMessageResponse(
        UUID messageId,
        String role,
        String intent,
        UUID selectedNodeId,
        String selectedArtifactType,
        String type,
        String text,
        List<AssistantAttachmentRef> attachments,
        ModuleTreePatchProposalResponse proposal,
        Map<String, Object> content,
        LocalDateTime createdAt
) {
}