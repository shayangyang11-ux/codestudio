package com.codemind.platform.theme;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectThemeProfileResponse(
        UUID id,
        UUID projectId,
        String industry,
        String themeKey,
        String visualTone,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String density,
        String radius,
        String loginLayout,
        String navigationStyle,
        boolean darkModeEnabled,
        String source,
        boolean locked,
        String notes,
        UUID createdBy,
        UUID updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
