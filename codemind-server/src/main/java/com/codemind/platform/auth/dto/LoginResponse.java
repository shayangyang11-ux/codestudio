package com.codemind.platform.auth.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long expiresInSeconds,
        UserInfo user
) {
    public record UserInfo(
            UUID userId,
            String username,
            String displayName
    ) {
    }
}