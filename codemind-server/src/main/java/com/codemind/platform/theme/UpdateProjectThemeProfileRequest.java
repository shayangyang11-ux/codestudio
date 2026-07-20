package com.codemind.platform.theme;

public record UpdateProjectThemeProfileRequest(
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
        Boolean darkModeEnabled,
        Boolean locked,
        String notes
) {
}
