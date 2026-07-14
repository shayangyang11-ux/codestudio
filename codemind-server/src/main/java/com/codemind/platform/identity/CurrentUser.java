package com.codemind.platform.identity;

import java.util.UUID;

public record CurrentUser(
        UUID userId,
        String username,
        String displayName
) {
}