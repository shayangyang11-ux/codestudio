package com.codemind.platform.theme;

// A9.10.14.4.3.15.4.4_STYLE_OVERRIDE_LOCK

import com.codemind.platform.identity.CurrentUserProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectThemeProfileService {

    private static final Set<String> INDUSTRIES = Set.of(
            "GENERAL",
            "COMMERCE",
            "TECH",
            "INDUSTRIAL",
            "FINANCE",
            "GOV",
            "EDUCATION",
            "HEALTHCARE",
                "FOREIGN_TRADE",
                "WORK_ORDER",
                "WAREHOUSE",
                "MANUFACTURING",
                "CRM",
                "SAAS",
                "MEDICAL");

    private static final Set<String> THEME_KEYS = Set.of(
            "enterprise_default",
            "tech_modern",
            "commerce_light",
            "industrial_pro",
            "finance_stable",
            "gov_clean"
    );

    private static final Set<String> VISUAL_TONES = Set.of(
            "CLEAN",
            "TECH",
            "COMMERCE",
            "INDUSTRIAL",
            "FINANCE",
            "GOV"
    );

    private static final Set<String> DENSITIES = Set.of(
            "COMPACT",
            "STANDARD",
            "COMFORTABLE"
    );

    private static final Set<String> RADII = Set.of(
            "SMALL",
            "MEDIUM",
            "LARGE"
    );

    private static final Set<String> LOGIN_LAYOUTS = Set.of(
            "BRAND_SPLIT",
            "CENTER_CARD",
            "MINIMAL"
    );

    private static final Set<String> NAVIGATION_STYLES = Set.of(
            "SIDE_NAV",
            "TOP_NAV",
            "MIXED"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CurrentUserProvider currentUserProvider;

    public ProjectThemeProfileService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CurrentUserProvider currentUserProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public ProjectThemeProfileResponse getOrCreateThemeProfile(
            UUID workspaceId,
            String projectCode
    ) {
        ProjectAccess projectAccess = resolveAccessibleProject(
                workspaceId,
                projectCode
        );

        ProjectThemeProfileResponse existing = findByProjectId(projectAccess.projectId());

        if (existing != null) {
            return existing;
        }

        return insertDefault(projectAccess.projectId(), projectAccess.userId());
    }

    @Transactional
    public ProjectThemeProfileResponse updateThemeProfile(
            UUID workspaceId,
            String projectCode,
            UpdateProjectThemeProfileRequest request
    ) {
        ProjectAccess projectAccess = resolveAccessibleProject(
                workspaceId,
                projectCode
        );

        NormalizedThemeProfile normalized = normalizeRequest(request);
        UUID profileId = UUID.randomUUID();

        String sql = """
                INSERT INTO project_theme_profile (
                    id,
                    project_id,
                    industry,
                    theme_key,
                    visual_tone,
                    primary_color,
                    secondary_color,
                    accent_color,
                    density,
                    radius,
                    login_layout,
                    navigation_style,
                    dark_mode_enabled,
                    source,
                    locked,
                    notes,
                    created_by,
                    updated_by,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :projectId,
                    :industry,
                    :themeKey,
                    :visualTone,
                    :primaryColor,
                    :secondaryColor,
                    :accentColor,
                    :density,
                    :radius,
                    :loginLayout,
                    :navigationStyle,
                    :darkModeEnabled,
                    'USER',
                    :locked,
                    :notes,
                    :userId,
                    :userId,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (project_id) DO UPDATE SET
                    industry = EXCLUDED.industry,
                    theme_key = EXCLUDED.theme_key,
                    visual_tone = EXCLUDED.visual_tone,
                    primary_color = EXCLUDED.primary_color,
                    secondary_color = EXCLUDED.secondary_color,
                    accent_color = EXCLUDED.accent_color,
                    density = EXCLUDED.density,
                    radius = EXCLUDED.radius,
                    login_layout = EXCLUDED.login_layout,
                    navigation_style = EXCLUDED.navigation_style,
                    dark_mode_enabled = EXCLUDED.dark_mode_enabled,
                    source = 'USER',
                    locked = EXCLUDED.locked,
                    notes = EXCLUDED.notes,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = CURRENT_TIMESTAMP
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", profileId)
                        .addValue("projectId", projectAccess.projectId())
                        .addValue("industry", normalized.industry())
                        .addValue("themeKey", normalized.themeKey())
                        .addValue("visualTone", normalized.visualTone())
                        .addValue("primaryColor", normalized.primaryColor())
                        .addValue("secondaryColor", normalized.secondaryColor())
                        .addValue("accentColor", normalized.accentColor())
                        .addValue("density", normalized.density())
                        .addValue("radius", normalized.radius())
                        .addValue("loginLayout", normalized.loginLayout())
                        .addValue("navigationStyle", normalized.navigationStyle())
                        .addValue("darkModeEnabled", normalized.darkModeEnabled())
                        .addValue("locked", normalized.locked())
                        .addValue("notes", normalized.notes())
                        .addValue("userId", projectAccess.userId())
        );

        ProjectThemeProfileResponse response = findByProjectId(projectAccess.projectId());

        if (response == null) {
            throw new IllegalStateException("Project theme profile update failed.");
        }

        return response;
    }

    private ProjectThemeProfileResponse insertDefault(
            UUID projectId,
            UUID userId
    ) {
        String sql = """
                INSERT INTO project_theme_profile (
                    id,
                    project_id,
                    industry,
                    theme_key,
                    visual_tone,
                    primary_color,
                    density,
                    radius,
                    login_layout,
                    navigation_style,
                    dark_mode_enabled,
                    source,
                    locked,
                    created_by,
                    updated_by,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :projectId,
                    'GENERAL',
                    'enterprise_default',
                    'CLEAN',
                    '#2563eb',
                    'STANDARD',
                    'MEDIUM',
                    'BRAND_SPLIT',
                    'SIDE_NAV',
                    FALSE,
                    'SYSTEM_DEFAULT',
                    FALSE,
                    :userId,
                    :userId,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (project_id) DO NOTHING
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("projectId", projectId)
                        .addValue("userId", userId)
        );

        ProjectThemeProfileResponse response = findByProjectId(projectId);

        if (response == null) {
            throw new IllegalStateException("Unable to create default project theme profile.");
        }

        return response;
    }

    private ProjectThemeProfileResponse findByProjectId(
            UUID projectId
    ) {
        String sql = """
                SELECT *
                FROM project_theme_profile
                WHERE project_id = :projectId
                LIMIT 1
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("projectId", projectId),
                    (rs, rowNum) -> mapResponse(rs)
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private ProjectAccess resolveAccessibleProject(
            UUID workspaceId,
            String projectCode
    ) {
        UUID userId = currentUserProvider.getCurrentUser().userId();

        String sql = """
                SELECT p.id
                FROM project p
                JOIN workspace_member wm
                    ON wm.workspace_id = p.workspace_id
                   AND wm.user_id = :userId
                   AND wm.status = 'ACTIVE'
                LEFT JOIN project_member pm
                    ON pm.project_id = p.id
                   AND pm.user_id = :userId
                   AND pm.status = 'ACTIVE'
                WHERE p.workspace_id = :workspaceId
                  AND p.project_code = :projectCode
                  AND p.status = 'ACTIVE'
                  AND (
                        p.visibility = 'WORKSPACE'
                        OR pm.user_id IS NOT NULL
                  )
                LIMIT 1
                """;

        try {
            UUID projectId = jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("workspaceId", workspaceId)
                            .addValue("projectCode", projectCode)
                            .addValue("userId", userId),
                    UUID.class
            );

            if (projectId == null) {
                throw new IllegalArgumentException("Project not accessible.");
            }

            return new ProjectAccess(projectId, userId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException(
                    "Project not accessible. workspaceId="
                            + workspaceId
                            + ", projectCode="
                            + projectCode
            );
        }
    }

    private NormalizedThemeProfile normalizeRequest(
            UpdateProjectThemeProfileRequest request
    ) {
        String industry = normalizeEnum(
                request == null ? null : request.industry(),
                "GENERAL",
                INDUSTRIES
        );

        String themeKey = normalizeThemeKey(
                request == null ? null : request.themeKey(),
                themeKeyForIndustry(industry)
        );

        String visualTone = normalizeEnum(
                request == null ? null : request.visualTone(),
                visualToneForTheme(themeKey),
                VISUAL_TONES
        );

        String primaryColor = normalizeColor(
                request == null ? null : request.primaryColor(),
                primaryColorForTheme(themeKey)
        );

        String secondaryColor = normalizeOptionalColor(
                request == null ? null : request.secondaryColor()
        );

        String accentColor = normalizeOptionalColor(
                request == null ? null : request.accentColor()
        );

        String density = normalizeEnum(
                request == null ? null : request.density(),
                "STANDARD",
                DENSITIES
        );

        String radius = normalizeEnum(
                request == null ? null : request.radius(),
                "MEDIUM",
                RADII
        );

        String loginLayout = normalizeEnum(
                request == null ? null : request.loginLayout(),
                "BRAND_SPLIT",
                LOGIN_LAYOUTS
        );

        String navigationStyle = normalizeEnum(
                request == null ? null : request.navigationStyle(),
                "SIDE_NAV",
                NAVIGATION_STYLES
        );

        boolean darkModeEnabled = request != null
                && Boolean.TRUE.equals(request.darkModeEnabled());

        boolean locked = request != null
                && Boolean.TRUE.equals(request.locked());

        String notes = trimToNull(request == null ? null : request.notes());

        return new NormalizedThemeProfile(
                industry,
                themeKey,
                visualTone,
                primaryColor,
                secondaryColor,
                accentColor,
                density,
                radius,
                loginLayout,
                navigationStyle,
                darkModeEnabled,
                locked,
                notes
        );
    }

    private String normalizeEnum(
            String value,
            String fallback,
            Set<String> allowed
    ) {
        String normalized = value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);

        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String normalizeThemeKey(
            String value,
            String fallback
    ) {
        String normalized = value == null ? "" : value.trim();

        return THEME_KEYS.contains(normalized) ? normalized : fallback;
    }

    private String normalizeColor(
            String value,
            String fallback
    ) {
        String normalized = value == null ? "" : value.trim();

        if (normalized.matches("^#[0-9a-fA-F]{6}$")) {
            return normalized;
        }

        return fallback;
    }

    private String normalizeOptionalColor(
            String value
    ) {
        String normalized = value == null ? "" : value.trim();

        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.matches("^#[0-9a-fA-F]{6}$")) {
            return normalized;
        }

        return null;
    }

    private String themeKeyForIndustry(
            String industry
    ) {
        return switch (industry) {
            case "COMMERCE" -> "commerce_light";
            case "FOREIGN_TRADE", "CRM" -> "enterprise_default";
            case "WORK_ORDER", "SAAS" -> "tech_modern";
            case "WAREHOUSE", "MANUFACTURING" -> "industrial_pro";
            case "TECH" -> "tech_modern";
            case "INDUSTRIAL" -> "industrial_pro";
            case "FINANCE" -> "finance_stable";
            case "GOV" -> "gov_clean";
            default -> "enterprise_default";
        };
    }

    private String primaryColorForTheme(
            String themeKey
    ) {
        return switch (themeKey) {
            case "commerce_light" -> "#2563eb";
            case "tech_modern" -> "#4f46e5";
            case "industrial_pro" -> "#0f766e";
            case "finance_stable" -> "#1e3a8a";
            case "gov_clean" -> "#1d4ed8";
            default -> "#2563eb";
        };
    }

    private String visualToneForTheme(
            String themeKey
    ) {
        return switch (themeKey) {
            case "commerce_light" -> "COMMERCE";
            case "tech_modern" -> "TECH";
            case "industrial_pro" -> "INDUSTRIAL";
            case "finance_stable" -> "FINANCE";
            case "gov_clean" -> "GOV";
            default -> "CLEAN";
        };
    }

    private String trimToNull(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() > 2000) {
            return trimmed.substring(0, 2000);
        }

        return trimmed;
    }

    private ProjectThemeProfileResponse mapResponse(
            ResultSet rs
    ) throws SQLException {
        return new ProjectThemeProfileResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("industry"),
                rs.getString("theme_key"),
                rs.getString("visual_tone"),
                rs.getString("primary_color"),
                rs.getString("secondary_color"),
                rs.getString("accent_color"),
                rs.getString("density"),
                rs.getString("radius"),
                rs.getString("login_layout"),
                rs.getString("navigation_style"),
                rs.getBoolean("dark_mode_enabled"),
                rs.getString("source"),
                rs.getBoolean("locked"),
                rs.getString("notes"),
                rs.getObject("created_by", UUID.class),
                rs.getObject("updated_by", UUID.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
        );
    }

    private record ProjectAccess(
            UUID projectId,
            UUID userId
    ) {
    }

    private record NormalizedThemeProfile(
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
            boolean locked,
            String notes
    ) {
    }
}
