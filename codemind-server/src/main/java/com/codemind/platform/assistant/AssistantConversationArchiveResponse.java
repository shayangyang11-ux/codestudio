package com.codemind.platform.assistant;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssistantConversationArchiveResponse(
        UUID archiveMessageId,
        int archivedMessageCount,
        LocalDateTime archivedAt,
        String message
) {
}