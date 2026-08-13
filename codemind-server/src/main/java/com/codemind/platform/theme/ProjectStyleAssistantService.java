package com.codemind.platform.theme;

// A9.10.14.4.3.15.4.4_STYLE_OVERRIDE_LOCK

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProjectStyleAssistantService {

    private static final Set<String> INDUSTRIES = Set.of("GENERAL", "COMMERCE", "TECH", "INDUSTRIAL", "FINANCE", "GOV", "EDUCATION", "HEALTHCARE",
                "FOREIGN_TRADE",
                "WORK_ORDER",
                "WAREHOUSE",
                "MANUFACTURING",
                "CRM",
                "SAAS",
                "MEDICAL");
    private static final Set<String> THEME_KEYS = Set.of("enterprise_default", "tech_modern", "commerce_light", "industrial_pro", "finance_stable", "gov_clean");
    private static final Set<String> VISUAL_TONES = Set.of("CLEAN", "TECH", "COMMERCE", "INDUSTRIAL", "FINANCE", "GOV");
    private static final Set<String> DENSITIES = Set.of("COMPACT", "STANDARD", "COMFORTABLE");
    private static final Set<String> RADII = Set.of("SMALL", "MEDIUM", "LARGE");
    private static final Set<String> LOGIN_LAYOUTS = Set.of("BRAND_SPLIT", "CENTER_CARD", "MINIMAL");
    private static final Set<String> NAVIGATION_STYLES = Set.of("SIDE_NAV", "TOP_NAV", "MIXED");
    private static final Set<String> ANALYSIS_MODES = Set.of(
            "STYLE_INTENT_TEXT",
            "REFERENCE_TEXT",
            "REFERENCE_IMAGE",
            "MIXED_REFERENCE",
            "REFERENCE_IMAGE_PENDING",
            "MIXED_REFERENCE_PENDING",
            "JAVA_RULE_FALLBACK",
            "PYTHON_RULE_FALLBACK"
    );
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final ProjectStyleAssistantAgentClient agentClient;
    private final ProjectStyleReferenceImageUnderstandingService imageUnderstandingService;
    private final ObjectMapper objectMapper;

    public ProjectStyleAssistantService(
            ProjectStyleAssistantAgentClient agentClient,
            ProjectStyleReferenceImageUnderstandingService imageUnderstandingService,
            ObjectMapper objectMapper
    ) {
        this.agentClient = agentClient;
        this.imageUnderstandingService = imageUnderstandingService;
        this.objectMapper = objectMapper;
    }

    public ProjectStyleAssistantResponse analyze(
            UUID workspaceId,
            String projectCode,
            ProjectStyleAssistantRequest request,
            ProjectThemeProfileResponse currentProfile
    ) {
        List<Map<String, Object>> imageUnderstandings =
                imageUnderstandingService.understandReferenceImages(
                        workspaceId,
                        projectCode,
                        request
                );

        ProjectStyleAssistantRequest enrichedRequest =
                enrichRequestWithImageUnderstandings(
                        request,
                        imageUnderstandings
                );

        try {
            Object agentResponse =
                    agentClient.analyzeStyle(
                            enrichedRequest,
                            currentProfile
                    );

            ProjectStyleAssistantResponse response =
                    fromAgentResponse(
                            agentResponse,
                            enrichedRequest,
                            currentProfile,
                            imageUnderstandings
                    );

            List<String> warnings =
                    new ArrayList<>(
                            safeList(response.getWarnings())
                    );

            warnings.add("统一风格助手结果已通过 Java 后端枚举、颜色、附件归属和图片理解兜底规则校验。");

            response.setWarnings(limit(warnings, 10));

            return response;
        } catch (Exception e) {
            ProjectStyleAssistantResponse fallback =
                    fallbackResponse(
                            enrichedRequest,
                            currentProfile,
                            imageUnderstandings
                    );

            List<String> warnings =
                    new ArrayList<>(
                            safeList(fallback.getWarnings())
                    );

            warnings.add("Style Assistant Agent 暂不可用，已使用 Java 规则兜底：" + safeMessage(e));

            fallback.setWarnings(limit(warnings, 10));

            return fallback;
        }
    }

    private ProjectStyleAssistantRequest enrichRequestWithImageUnderstandings(
            ProjectStyleAssistantRequest request,
            List<Map<String, Object>> imageUnderstandings
    ) {
        ProjectStyleAssistantRequest result = new ProjectStyleAssistantRequest();

        result.setInstruction(request == null ? null : request.getInstruction());
        result.setReferenceText(request == null ? null : request.getReferenceText());
        result.setReferenceNotes(request == null ? null : request.getReferenceNotes());
        result.setReferenceMode(request == null ? "AUTO" : request.getReferenceMode());
        result.setTargetPageType(request == null ? null : request.getTargetPageType());
        result.setAllowDarkMode(request == null ? null : request.getAllowDarkMode());
        result.setAttachmentIds(request == null ? List.of() : safeStringList(request.getAttachmentIds()));
        result.setAttachmentNames(request == null ? List.of() : safeStringList(request.getAttachmentNames()));

        if (imageUnderstandings == null || imageUnderstandings.isEmpty()) {
            return result;
        }

        StringBuilder builder = new StringBuilder();

        if (result.getReferenceText() != null && !result.getReferenceText().isBlank()) {
            builder.append(result.getReferenceText().trim());
            builder.append("\n\n");
        }

        builder.append("【服务端参考图视觉理解】");

        for (Map<String, Object> item : imageUnderstandings) {
            builder.append("\n- ");
            builder.append(firstNonBlank(asString(item.get("summary")), "已完成参考图基础视觉理解。"));
        }

        result.setReferenceText(builder.toString());

        return result;
    }

    @SuppressWarnings("unchecked")
    private ProjectStyleAssistantResponse fromAgentResponse(
            Object agentResponse,
            ProjectStyleAssistantRequest request,
            ProjectThemeProfileResponse currentProfile,
            List<Map<String, Object>> imageUnderstandings
    ) {
        Map<String, Object> raw = toMap(agentResponse);
        ProjectStyleAssistantResponse fallback = fallbackResponse(request, currentProfile, imageUnderstandings);

        String industry = normalizeAllowed(readString(raw, "industry"), INDUSTRIES, fallback.getIndustry());
        String themeKey = normalizeAllowed(readString(raw, "themeKey", "theme_key"), THEME_KEYS, themeKeyForIndustry(industry));
        String visualTone = normalizeAllowed(readString(raw, "visualTone", "visual_tone"), VISUAL_TONES, visualToneForTheme(themeKey));
        String primaryColor = normalizeColor(readString(raw, "primaryColor", "primary_color"), fallback.getPrimaryColor());
        String secondaryColor = normalizeNullableColor(readString(raw, "secondaryColor", "secondary_color"));
        String accentColor = normalizeNullableColor(readString(raw, "accentColor", "accent_color"));
        String density = normalizeAllowed(readString(raw, "density"), DENSITIES, fallback.getDensity());
        String radius = normalizeAllowed(readString(raw, "radius"), RADII, fallback.getRadius());
        String loginLayout = normalizeAllowed(readString(raw, "loginLayout", "login_layout"), LOGIN_LAYOUTS, fallback.getLoginLayout());
        String navigationStyle = normalizeAllowed(readString(raw, "navigationStyle", "navigation_style"), NAVIGATION_STYLES, fallback.getNavigationStyle());
        String analysisMode = normalizeAllowed(readString(raw, "analysisMode", "analysis_mode"), ANALYSIS_MODES, resolveAnalysisMode(request, imageUnderstandings));
        boolean darkMode = readBoolean(raw, "darkModeEnabled", "dark_mode_enabled", fallback.isDarkModeEnabled());

        ProjectStyleAssistantResponse response = new ProjectStyleAssistantResponse();
        response.setIndustry(industry);
        response.setThemeKey(themeKey);
        response.setVisualTone(visualTone);
        response.setPrimaryColor(primaryColor);
        response.setSecondaryColor(secondaryColor);
        response.setAccentColor(accentColor);
        response.setDensity(density);
        response.setRadius(radius);
        response.setLoginLayout(loginLayout);
        response.setNavigationStyle(navigationStyle);
        response.setDarkModeEnabled(darkMode);
        response.setAnalysisMode(analysisMode);
        response.setAgentMode(firstNonBlank(readString(raw, "agentMode", "agent_mode"), "STYLE_ASSISTANT_AGENT"));
        response.setAttachmentIds(request == null || request.getAttachmentIds() == null ? List.of() : request.getAttachmentIds());
        response.setAttachmentNames(request == null || request.getAttachmentNames() == null ? List.of() : request.getAttachmentNames());
        response.setExtractedSignals(mergeImageUnderstandings(readMap(raw, "extractedSignals", "extracted_signals"), imageUnderstandings));
        response.setConfidence(readDouble(raw, "confidence", Math.max(0.68, fallback.getConfidence())));
        response.setSummary(firstNonBlank(readString(raw, "summary"), fallback.getSummary()));
        response.setReasons(limit(firstNonEmpty(readStringList(raw, "reasons"), fallback.getReasons()), 10));
        response.setWarnings(limit(firstNonEmpty(readStringList(raw, "warnings"), fallback.getWarnings()), 10));
        response.setUpdatePayload(buildUpdatePayload(response, currentProfile));
        return response;
    }

    private ProjectStyleAssistantResponse fallbackResponse(
            ProjectStyleAssistantRequest request,
            ProjectThemeProfileResponse currentProfile,
            List<Map<String, Object>> imageUnderstandings
    ) {
        String text = normalizeText(join(
                request == null ? null : request.getInstruction(),
                request == null ? null : request.getReferenceText(),
                request == null ? null : request.getReferenceNotes(),
                request == null ? null : request.getTargetPageType(),
                request == null || request.getAttachmentNames() == null ? null : String.join(" ", request.getAttachmentNames())
        ));

        String industry = inferIndustry(text, currentProfile);
        String themeKey = themeKeyForIndustry(industry);
        String visualTone = visualToneForTheme(themeKey);
        String primaryColor = inferPrimaryColor(text, themeKey, currentProfile, imageUnderstandings);
        String density = inferDensity(text, currentProfile);
        String radius = inferRadius(text, industry, currentProfile);
        String loginLayout = inferLoginLayout(text, currentProfile);
        String navigationStyle = inferNavigationStyle(text, currentProfile);
        boolean darkMode = inferDarkMode(text, request, currentProfile);
        String analysisMode = resolveAnalysisMode(request, imageUnderstandings);

        ProjectStyleAssistantResponse response = new ProjectStyleAssistantResponse();
        response.setIndustry(industry);
        response.setThemeKey(themeKey);
        response.setVisualTone(visualTone);
        response.setPrimaryColor(primaryColor);
        response.setSecondaryColor("TECH".equals(industry) ? "#06b6d4" : null);
        response.setAccentColor("TECH".equals(industry) ? "#22c55e" : null);
        response.setDensity(density);
        response.setRadius(radius);
        response.setLoginLayout(loginLayout);
        response.setNavigationStyle(navigationStyle);
        response.setDarkModeEnabled(darkMode);
        response.setAnalysisMode(analysisMode);
        response.setAgentMode("JAVA_RULE_FALLBACK");
        response.setAttachmentIds(request == null || request.getAttachmentIds() == null ? List.of() : request.getAttachmentIds());
        response.setAttachmentNames(request == null || request.getAttachmentNames() == null ? List.of() : request.getAttachmentNames());
        response.setExtractedSignals(mergeImageUnderstandings(buildFallbackSignals(text, analysisMode), imageUnderstandings));
        response.setConfidence(resolveConfidence(text, analysisMode, imageUnderstandings));
        response.setSummary(buildSummary(industry, themeKey, density, radius, loginLayout, analysisMode, imageUnderstandings));
        response.setReasons(buildReasons(text, industry, themeKey, density, radius, loginLayout, navigationStyle, analysisMode, imageUnderstandings));
        response.setWarnings(buildWarnings(text, analysisMode, imageUnderstandings));
        response.setUpdatePayload(buildUpdatePayload(response, currentProfile));
        return response;
    }

    private String resolveAnalysisMode(
            ProjectStyleAssistantRequest request,
            List<Map<String, Object>> imageUnderstandings
    ) {
        boolean hasAttachment = request != null
                && ((request.getAttachmentIds() != null && !request.getAttachmentIds().isEmpty())
                || (request.getAttachmentNames() != null && !request.getAttachmentNames().isEmpty()));
        boolean hasReferenceText = request != null
                && (hasText(request.getReferenceText()) || hasText(request.getReferenceNotes()));
        boolean hasImageUnderstanding = imageUnderstandings != null && !imageUnderstandings.isEmpty();
        String text = normalizeText(join(
                request == null ? null : request.getInstruction(),
                request == null ? null : request.getReferenceText(),
                request == null ? null : request.getReferenceNotes()
        ));
        boolean mentionsReference = containsAny(text, "参考", "截图", "竞品", "品牌图", "上传", "image", "screenshot", "reference");

        if (hasImageUnderstanding && (hasReferenceText || mentionsReference)) return "MIXED_REFERENCE";
        if (hasImageUnderstanding) return "REFERENCE_IMAGE";
        if (hasAttachment && (hasReferenceText || mentionsReference)) return "MIXED_REFERENCE_PENDING";
        if (hasAttachment) return "REFERENCE_IMAGE_PENDING";
        if (hasReferenceText || mentionsReference) return "REFERENCE_TEXT";
        return "STYLE_INTENT_TEXT";
    }

    private String inferIndustry(String text, ProjectThemeProfileResponse currentProfile) {
        if (containsAny(text, "电商", "商城", "零售", "订单", "商品", "购物", "会员", "用户中心", "转化", "营销", "mall", "shop", "ecommerce", "京东")) return "COMMERCE";
        if (containsAny(text, "科技", "智能", "ai", "算法", "数据平台", "saas", "云平台", "大数据", "数字化", "中台", "低代码", "研发")) return "TECH";
        if (containsAny(text, "工业", "制造", "工厂", "设备", "产线", "mes", "iot", "传感器", "运维", "监控", "工控")) return "INDUSTRIAL";
        if (containsAny(text, "金融", "风控", "银行", "支付", "交易", "账务", "结算", "授信", "finance", "risk")) return "FINANCE";
        if (containsAny(text, "政务", "政府", "监管", "公共服务", "办事", "审批", "gov")) return "GOV";
        if (currentProfile != null && hasText(currentProfile.industry())) return currentProfile.industry();
        return "GENERAL";
    }

    private String inferPrimaryColor(
            String text,
            String themeKey,
            ProjectThemeProfileResponse currentProfile,
            List<Map<String, Object>> imageUnderstandings
    ) {
        String imageColor = firstPrimaryColorFromImageUnderstandings(imageUnderstandings);

        if (imageColor != null && containsAny(text, "参考图", "截图", "品牌图", "提取", "配色", "颜色", "主色")) {
            return imageColor;
        }

        if (containsAny(text, "紫色", "紫蓝", "科技紫", "violet", "purple")) return "#4f46e5";
        if (containsAny(text, "青色", "青绿", "绿色", "teal", "green")) return "#0f766e";
        if (containsAny(text, "橙色", "橙黄", "orange")) return "#f97316";
        if (containsAny(text, "红色", "玫红", "red", "rose")) return "#e11d48";
        if (containsAny(text, "深蓝", "藏蓝", "navy")) return "#1e3a8a";
        if (currentProfile != null && hasText(currentProfile.primaryColor()) && !containsAny(text, "换色", "重新配色", "主色", "颜色", "配色")) return currentProfile.primaryColor();
        return primaryColorForTheme(themeKey);
    }

    private String firstPrimaryColorFromImageUnderstandings(
            List<Map<String, Object>> imageUnderstandings
    ) {
        if (imageUnderstandings == null) {
            return null;
        }

        for (Map<String, Object> item : imageUnderstandings) {
            String color = normalizeNullableColor(asString(item.get("primaryColor")));

            if (color != null) {
                return color;
            }
        }

        return null;
    }

    private String inferDensity(String text, ProjectThemeProfileResponse currentProfile) {
        if (containsAny(text, "紧凑", "高密度", "后台", "表格多", "信息多", "compact")) return "COMPACT";
        if (containsAny(text, "舒展", "留白", "高级", "大屏", "营销", "comfortable")) return "COMFORTABLE";
        return currentProfile == null ? "STANDARD" : currentProfile.density();
    }

    private String inferRadius(String text, String industry, ProjectThemeProfileResponse currentProfile) {
        if (containsAny(text, "圆润", "大圆角", "年轻", "亲和", "c端", "现代")) return "LARGE";
        if (containsAny(text, "严肃", "规整", "硬朗", "工业", "金融", "政务", "小圆角")) return "SMALL";
        if ("COMMERCE".equals(industry) || "TECH".equals(industry)) return "LARGE";
        if ("INDUSTRIAL".equals(industry) || "FINANCE".equals(industry) || "GOV".equals(industry)) return "SMALL";
        return currentProfile == null ? "MEDIUM" : currentProfile.radius();
    }

    private String inferLoginLayout(String text, ProjectThemeProfileResponse currentProfile) {
        if (containsAny(text, "居中", "居中卡片", "center")) return "CENTER_CARD";
        if (containsAny(text, "极简", "简单", "minimal")) return "MINIMAL";
        if (containsAny(text, "品牌", "宣传", "左右", "分栏", "brand")) return "BRAND_SPLIT";
        return currentProfile == null ? "BRAND_SPLIT" : currentProfile.loginLayout();
    }

    private String inferNavigationStyle(String text, ProjectThemeProfileResponse currentProfile) {
        if (containsAny(text, "顶部导航", "top nav", "官网", "c端", "搜索栏")) return "TOP_NAV";
        if (containsAny(text, "混合导航", "顶部加侧边", "mixed")) return "MIXED";
        if (containsAny(text, "后台", "管理端", "side nav", "侧边")) return "SIDE_NAV";
        return currentProfile == null ? "SIDE_NAV" : currentProfile.navigationStyle();
    }

    private boolean inferDarkMode(String text, ProjectStyleAssistantRequest request, ProjectThemeProfileResponse currentProfile) {
        if (containsAny(text, "暗色", "深色", "黑色", "dark", "夜间")) return true;
        if (request != null && Boolean.TRUE.equals(request.getAllowDarkMode())) return true;
        return currentProfile != null && currentProfile.darkModeEnabled();
    }

    private Map<String, Object> buildFallbackSignals(String text, String analysisMode) {
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("analysisMode", analysisMode);
        signals.put("styleKeywords", extractKeywords(text));
        signals.put("source", "JAVA_RULE_FALLBACK");
        signals.put("hasReferenceMaterial", !"STYLE_INTENT_TEXT".equals(analysisMode));
        return signals;
    }

    private Map<String, Object> mergeImageUnderstandings(
            Map<String, Object> signals,
            List<Map<String, Object>> imageUnderstandings
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();

        if (signals != null) {
            merged.putAll(signals);
        }

        if (imageUnderstandings != null && !imageUnderstandings.isEmpty()) {
            merged.put("serverImageUnderstandingCount", imageUnderstandings.size());
            merged.put("serverImageUnderstandings", imageUnderstandings);
        }

        return merged;
    }

    private List<String> buildReasons(
            String text,
            String industry,
            String themeKey,
            String density,
            String radius,
            String loginLayout,
            String navigationStyle,
            String analysisMode,
            List<Map<String, Object>> imageUnderstandings
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("识别输入模式为 " + analysisMode + "。");
        reasons.add("识别行业方向为 " + industry + "，匹配主题 " + themeKey + "。");
        reasons.add("页面密度建议为 " + density + "，圆角建议为 " + radius + "。");
        reasons.add("登录页布局建议为 " + loginLayout + "，导航建议为 " + navigationStyle + "。");

        if (imageUnderstandings != null && !imageUnderstandings.isEmpty()) {
            reasons.add("服务端已读取参考图片像素，提取主色、色板、明度、饱和度和布局提示。");
        }

        if (containsAny(text, "参考", "截图", "竞品", "品牌图")) {
            reasons.add("已将参考描述抽象为视觉信号，而不是复制具体页面。");
        }

        return reasons;
    }

    private List<String> buildWarnings(
            String text,
            String analysisMode,
            List<Map<String, Object>> imageUnderstandings
    ) {
        List<String> warnings = new ArrayList<>();

        if (text.isBlank()) {
            warnings.add("未提供明确风格描述，已基于当前项目配置和默认企业风格生成建议。");
        }

        if (
                ("REFERENCE_IMAGE_PENDING".equals(analysisMode) || "MIXED_REFERENCE_PENDING".equals(analysisMode))
                        && (imageUnderstandings == null || imageUnderstandings.isEmpty())
        ) {
            warnings.add("当前未取得服务端图片理解结果，已根据附件名称和文字说明进行风格抽象。");
        }

        warnings.add("仅提取抽象风格特征，不复制具体竞品页面、品牌资产或文案。");

        return warnings;
    }

    private double resolveConfidence(
            String text,
            String analysisMode,
            List<Map<String, Object>> imageUnderstandings
    ) {
        double confidence = text.isBlank() ? 0.45 : 0.64;

        if (!"STYLE_INTENT_TEXT".equals(analysisMode)) {
            confidence += 0.06;
        }

        if (imageUnderstandings != null && !imageUnderstandings.isEmpty()) {
            confidence += 0.1;
        }

        confidence += Math.min(0.18, text.length() / 300.0);

        return Math.max(0.35, Math.min(0.92, confidence));
    }

    private String buildSummary(
            String industry,
            String themeKey,
            String density,
            String radius,
            String loginLayout,
            String analysisMode,
            List<Map<String, Object>> imageUnderstandings
    ) {
        String prefix = imageUnderstandings != null && !imageUnderstandings.isEmpty()
                ? "已结合服务端参考图视觉理解，"
                : "";

        return prefix
                + "建议使用 "
                + industry
                + " / "
                + themeKey
                + "，输入模式 "
                + analysisMode
                + "，页面密度 "
                + density
                + "，圆角 "
                + radius
                + "，登录布局 "
                + loginLayout
                + "。";
    }

    private Map<String, Object> buildUpdatePayload(ProjectStyleAssistantResponse response, ProjectThemeProfileResponse currentProfile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("industry", response.getIndustry());
        payload.put("themeKey", response.getThemeKey());
        payload.put("visualTone", response.getVisualTone());
        payload.put("primaryColor", response.getPrimaryColor());
        payload.put("secondaryColor", response.getSecondaryColor());
        payload.put("accentColor", response.getAccentColor());
        payload.put("density", response.getDensity());
        payload.put("radius", response.getRadius());
        payload.put("loginLayout", response.getLoginLayout());
        payload.put("navigationStyle", response.getNavigationStyle());
        payload.put("darkModeEnabled", response.isDarkModeEnabled());
        payload.put("locked", currentProfile != null && currentProfile.locked());
        payload.put("notes", response.getSummary());
        return payload;
    }

    private String themeKeyForIndustry(String industry) {
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

    private String visualToneForTheme(String themeKey) {
        return switch (themeKey) {
            case "commerce_light" -> "COMMERCE";
            case "tech_modern" -> "TECH";
            case "industrial_pro" -> "INDUSTRIAL";
            case "finance_stable" -> "FINANCE";
            case "gov_clean" -> "GOV";
            default -> "CLEAN";
        };
    }

    private String primaryColorForTheme(String themeKey) {
        return switch (themeKey) {
            case "tech_modern" -> "#4f46e5";
            case "industrial_pro" -> "#0f766e";
            case "finance_stable" -> "#1e3a8a";
            case "gov_clean" -> "#1d4ed8";
            default -> "#2563eb";
        };
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private Map<String, Object> readMap(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Map<?, ?> rawMap) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return result;
            }
        }
        return Map.of();
    }

    private String readString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private boolean readBoolean(Map<String, Object> map, String key1, String key2, boolean fallback) {
        Object value = map.containsKey(key1) ? map.get(key1) : map.get(key2);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private double readDouble(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        try {
            return Math.max(0.35, Math.min(0.95, Double.parseDouble(String.valueOf(value))));
        } catch (Exception e) {
            return fallback;
        }
    }

    private List<String> readStringList(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object item : list) if (item != null && !String.valueOf(item).isBlank()) result.add(String.valueOf(item).trim());
                return result;
            }
        }
        return List.of();
    }

    private List<String> firstNonEmpty(List<String> first, List<String> second) {
        return first == null || first.isEmpty() ? safeList(second) : first;
    }

    private List<String> safeList(List<String> source) {
        return source == null ? List.of() : source;
    }

    private List<String> safeStringList(List<String> source) {
        return source == null ? List.of() : source;
    }

    private List<String> limit(List<String> values, int max) {
        if (values == null) return List.of();
        if (values.size() <= max) return values;
        return new ArrayList<>(values.subList(0, max));
    }

    private String normalizeAllowed(String value, Set<String> allowed, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        if (allowed.contains(trimmed)) return trimmed;
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (allowed.contains(upper)) return upper;
        return fallback;
    }

    private String normalizeColor(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String text = value.trim();
        if (!text.startsWith("#")) text = "#" + text;
        if (text.length() == 4) {
            text = "#" + text.substring(1, 2).repeat(2)
                    + text.substring(2, 3).repeat(2)
                    + text.substring(3, 4).repeat(2);
        }
        if (!COLOR_PATTERN.matcher(text).matches()) return fallback;
        return text.toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableColor(String value) {
        if (value == null || value.isBlank()) return null;
        return normalizeColor(value, null);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null) return "unknown";
        return e.getMessage().length() > 180 ? e.getMessage().substring(0, 180) : e.getMessage();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : List.of("科技", "SaaS", "深色", "蓝紫", "金融", "电商", "工业", "政务", "紧凑", "舒展", "大圆角", "小圆角", "左侧导航", "顶部导航", "品牌分栏")) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) keywords.add(keyword);
        }
        return keywords;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
