package com.codemind.platform.auth.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String username,
        String displayName
) {
}