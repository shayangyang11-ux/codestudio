package com.codemind.platform.theme;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ProjectStyleReferenceImageUnderstandingService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProjectStyleReferenceImageUnderstandingService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> understandReferenceImages(
            UUID workspaceId,
            String projectCode,
            ProjectStyleAssistantRequest request
    ) {
        if (request == null || request.getAttachmentIds() == null || request.getAttachmentIds().isEmpty()) {
            return List.of();
        }

        List<UUID> attachmentIds = request.getAttachmentIds()
                .stream()
                .filter(Objects::nonNull)
                .map(this::parseUuidOrNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (attachmentIds.isEmpty()) {
            return List.of();
        }

        List<AttachmentRow> rows = loadRows(
                workspaceId,
                projectCode,
                attachmentIds
        );

        if (rows.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (AttachmentRow row : rows) {
            results.add(
                    understandRow(
                            row,
                            request
                    )
            );
        }

        return results;
    }

    private Map<String, Object> understandRow(
            AttachmentRow row,
            ProjectStyleAssistantRequest request
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("attachmentId", row.id().toString());
        result.put("fileName", row.fileName());
        result.put("mimeType", row.mimeType());
        result.put("fileSize", row.fileSize());
        result.put("parseStatus", row.parseStatus());
        result.put("source", "SERVER_IMAGE_UNDERSTANDING_BASIC");

        String extension = resolveExtension(row.fileName());

        if (!isImage(extension, row.mimeType())) {
            result.put("status", "SKIPPED_NON_IMAGE");
            result.put("summary", row.fileName() + " 不是图片文件，未进入参考图视觉理解。");
            return result;
        }

        Map<String, Object> metadata = row.metadata() == null
                ? Map.of()
                : row.metadata();

        result.put("metadata", metadata);

        Path path = resolvePath(row.storagePath());

        if (path == null || !Files.exists(path)) {
            result.put("status", "IMAGE_FILE_NOT_FOUND");
            result.put("summary", row.fileName() + " 已有附件记录，但本地文件不存在，已使用附件元数据兜底。");
            enrichFromMetadataOnly(
                    result,
                    metadata,
                    request,
                    row.fileName()
            );
            return result;
        }

        try {
            BufferedImage image = ImageIO.read(path.toFile());

            if (image == null) {
                result.put("status", "IMAGE_READ_FAILED");
                result.put("summary", row.fileName() + " 图片读取失败，已使用附件元数据兜底。");
                enrichFromMetadataOnly(
                        result,
                        metadata,
                        request,
                        row.fileName()
                );
                return result;
            }

            ServerImageAnalysis analysis = analyzeImage(
                    image,
                    row.fileName(),
                    request
            );

            result.put("status", "UNDERSTOOD");
            result.put("imageWidth", image.getWidth());
            result.put("imageHeight", image.getHeight());
            result.put("aspectRatio", round2(image.getWidth() * 1.0 / Math.max(1, image.getHeight())));
            result.put("primaryColor", analysis.primaryColor());
            result.put("palette", analysis.palette());
            result.put("averageLightness", analysis.averageLightness());
            result.put("averageSaturation", analysis.averageSaturation());
            result.put("toneLabel", analysis.toneLabel());
            result.put("pageTypeHint", analysis.pageTypeHint());
            result.put("layoutSignals", analysis.layoutSignals());
            result.put("visualToneSignals", analysis.visualToneSignals());
            result.put("componentSignals", analysis.componentSignals());
            result.put("summary", analysis.summary());

            return result;
        } catch (IOException ex) {
            result.put("status", "IMAGE_READ_EXCEPTION");
            result.put("error", ex.getMessage());
            enrichFromMetadataOnly(
                    result,
                    metadata,
                    request,
                    row.fileName()
            );
            return result;
        }
    }

    private ServerImageAnalysis analyzeImage(
            BufferedImage image,
            String fileName,
            ProjectStyleAssistantRequest request
    ) {
        int maxSize = 96;
        int width = image.getWidth();
        int height = image.getHeight();
        int stepX = Math.max(1, width / maxSize);
        int stepY = Math.max(1, height / maxSize);

        Map<String, Integer> buckets = new LinkedHashMap<>();
        double lightnessSum = 0.0;
        double saturationSum = 0.0;
        int sampleCount = 0;

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >> 24) & 0xff;

                if (alpha < 80) {
                    continue;
                }

                int r = (argb >> 16) & 0xff;
                int g = (argb >> 8) & 0xff;
                int b = argb & 0xff;

                double[] hsl = rgbToHsl(r, g, b);
                lightnessSum += hsl[2];
                saturationSum += hsl[1];
                sampleCount++;

                int qr = Math.min(255, Math.round(r / 32.0f) * 32);
                int qg = Math.min(255, Math.round(g / 32.0f) * 32);
                int qb = Math.min(255, Math.round(b / 32.0f) * 32);

                String color = rgbToHex(qr, qg, qb);
                buckets.put(color, buckets.getOrDefault(color, 0) + 1);
            }
        }

        List<String> palette = buckets.entrySet()
                .stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .map(Map.Entry::getKey)
                .limit(6)
                .toList();

        if (palette.isEmpty()) {
            palette = List.of("#64748b");
        }

        double averageLightness = sampleCount == 0
                ? 0.5
                : round3(lightnessSum / sampleCount);
        double averageSaturation = sampleCount == 0
                ? 0.2
                : round3(saturationSum / sampleCount);

        String primaryColor = pickPrimaryColor(palette);
        String toneLabel = buildToneLabel(averageLightness, averageSaturation);

        String text = normalizeText(
                join(
                        request == null ? null : request.getInstruction(),
                        request == null ? null : request.getReferenceText(),
                        request == null ? null : request.getReferenceNotes(),
                        fileName
                )
        );

        String pageTypeHint = inferPageType(text, width, height);
        List<String> layoutSignals = inferLayoutSignals(text, width, height);
        List<String> visualToneSignals = inferVisualToneSignals(text, averageLightness, averageSaturation, primaryColor);
        List<String> componentSignals = inferComponentSignals(text, averageSaturation);

        String summary = fileName
                + "：服务端已读取图片像素，尺寸 "
                + width
                + "×"
                + height
                + "，主色候选 "
                + primaryColor
                + "，色板 "
                + String.join(", ", palette)
                + "，整体倾向 "
                + toneLabel
                + "，页面类型推断 "
                + pageTypeHint
                + "。";

        return new ServerImageAnalysis(
                primaryColor,
                palette,
                averageLightness,
                averageSaturation,
                toneLabel,
                pageTypeHint,
                layoutSignals,
                visualToneSignals,
                componentSignals,
                summary
        );
    }

    private void enrichFromMetadataOnly(
            Map<String, Object> result,
            Map<String, Object> metadata,
            ProjectStyleAssistantRequest request,
            String fileName
    ) {
        Object width = firstNonNull(
                metadata.get("imageWidth"),
                metadata.get("width")
        );
        Object height = firstNonNull(
                metadata.get("imageHeight"),
                metadata.get("height")
        );

        result.put("imageWidth", width);
        result.put("imageHeight", height);
        result.put("palette", List.of());
        result.put("layoutSignals", inferLayoutSignals(
                normalizeText(join(
                        request == null ? null : request.getInstruction(),
                        request == null ? null : request.getReferenceText(),
                        request == null ? null : request.getReferenceNotes(),
                        fileName
                )),
                toInt(width),
                toInt(height)
        ));
        result.put("visualToneSignals", List.of("metadata_only"));
        result.put("componentSignals", List.of());
    }

    private List<AttachmentRow> loadRows(
            UUID workspaceId,
            String projectCode,
            List<UUID> attachmentIds
    ) {
        String sql = """
                SELECT
                    id,
                    file_name,
                    mime_type,
                    file_size,
                    storage_path,
                    parse_status,
                    parsed_text,
                    metadata
                FROM assistant_attachment
                WHERE workspace_id = :workspaceId
                  AND project_code = :projectCode
                  AND id IN (:attachmentIds)
                """;

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectCode", projectCode)
                        .addValue("attachmentIds", attachmentIds),
                (rs, rowNum) -> new AttachmentRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("file_name"),
                        rs.getString("mime_type"),
                        rs.getLong("file_size"),
                        rs.getString("storage_path"),
                        rs.getString("parse_status"),
                        rs.getString("parsed_text"),
                        parseMetadata(rs.getString("metadata"))
                )
        );
    }

    private Map<String, Object> parseMetadata(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    text,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private UUID parseUuidOrNull(
            String value
    ) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path resolvePath(
            String storagePath
    ) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }

        return Paths.get(storagePath).toAbsolutePath().normalize();
    }

    private String resolveExtension(
            String fileName
    ) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isImage(
            String extension,
            String mimeType
    ) {
        return Set.of("png", "jpg", "jpeg", "webp").contains(extension)
                || (mimeType != null && mimeType.startsWith("image/"));
    }

    private String pickPrimaryColor(
            List<String> palette
    ) {
        for (String color : palette) {
            double[] hsl = hexToHsl(color);
            double saturation = hsl[1];
            double lightness = hsl[2];

            if (saturation > 0.18 && lightness > 0.12 && lightness < 0.88) {
                return color;
            }
        }

        return palette.isEmpty() ? "#2563eb" : palette.get(0);
    }

    private String buildToneLabel(
            double lightness,
            double saturation
    ) {
        String lightLabel = lightness < 0.34
                ? "偏深色"
                : lightness > 0.72
                ? "偏浅色"
                : "明度适中";

        String saturationLabel = saturation < 0.2
                ? "低饱和"
                : saturation > 0.52
                ? "高饱和"
                : "中等饱和";

        return lightLabel + "、" + saturationLabel;
    }

    private String inferPageType(
            String text,
            int width,
            int height
    ) {
        if (containsAny(text, "京东", "电商", "商城", "商品", "购物", "首页", "banner")) {
            return "ECOMMERCE_HOME";
        }

        if (containsAny(text, "dashboard", "仪表盘", "后台", "管理端", "数据大屏")) {
            return "DASHBOARD";
        }

        if (containsAny(text, "登录", "注册", "login")) {
            return "AUTH_PAGE";
        }

        if (containsAny(text, "品牌", "海报", "宣传")) {
            return "BRAND_GRAPHIC";
        }

        if (width > height * 1.6) {
            return "WIDE_PAGE_OR_BANNER";
        }

        return "UNKNOWN_REFERENCE_IMAGE";
    }

    private List<String> inferLayoutSignals(
            String text,
            int width,
            int height
    ) {
        List<String> signals = new ArrayList<>();

        if (containsAny(text, "左侧导航", "侧边栏", "side nav", "后台")) {
            signals.add("side_navigation");
        }

        if (containsAny(text, "顶部导航", "top nav", "顶部菜单", "搜索栏")) {
            signals.add("top_navigation");
        }

        if (containsAny(text, "卡片", "card", "商品卡", "推荐")) {
            signals.add("card_grid");
        }

        if (containsAny(text, "banner", "大图", "轮播", "首页")) {
            signals.add("hero_banner");
        }

        if (width > height * 1.6) {
            signals.add("wide_horizontal_composition");
        }

        if (signals.isEmpty()) {
            signals.add("layout_unknown_from_pixels");
        }

        return signals;
    }

    private List<String> inferVisualToneSignals(
            String text,
            double lightness,
            double saturation,
            String primaryColor
    ) {
        List<String> signals = new ArrayList<>();

        if (lightness < 0.34) {
            signals.add("dark_background_tendency");
        } else if (lightness > 0.72) {
            signals.add("light_background_tendency");
        }

        if (saturation > 0.52) {
            signals.add("high_saturation");
        } else if (saturation < 0.2) {
            signals.add("low_saturation");
        }

        if (containsAny(text, "科技", "saas", "ai", "蓝紫", "渐变")) {
            signals.add("tech_modern_tone");
        }

        if (containsAny(text, "促销", "热卖", "电商", "购物")) {
            signals.add("commerce_promotional_tone");
        }

        signals.add("primary_color_candidate:" + primaryColor);

        return signals;
    }

    private List<String> inferComponentSignals(
            String text,
            double saturation
    ) {
        List<String> signals = new ArrayList<>();

        if (containsAny(text, "大圆角", "圆润", "高级卡片")) {
            signals.add("large_radius_cards");
        }

        if (containsAny(text, "按钮", "搜索")) {
            signals.add("prominent_call_to_action");
        }

        if (containsAny(text, "商品", "推荐", "列表", "卡片")) {
            signals.add("repeated_card_items");
        }

        if (saturation > 0.52) {
            signals.add("visually_strong_accent_blocks");
        }

        if (signals.isEmpty()) {
            signals.add("component_signals_need_manual_confirmation");
        }

        return signals;
    }

    private double[] rgbToHsl(
            int r,
            int g,
            int b
    ) {
        double rn = r / 255.0;
        double gn = g / 255.0;
        double bn = b / 255.0;
        double max = Math.max(rn, Math.max(gn, bn));
        double min = Math.min(rn, Math.min(gn, bn));
        double h = 0.0;
        double s = 0.0;
        double l = (max + min) / 2.0;

        if (max != min) {
            double d = max - min;
            s = l > 0.5
                    ? d / (2.0 - max - min)
                    : d / (max + min);

            if (max == rn) {
                h = (gn - bn) / d + (gn < bn ? 6.0 : 0.0);
            } else if (max == gn) {
                h = (bn - rn) / d + 2.0;
            } else {
                h = (rn - gn) / d + 4.0;
            }

            h /= 6.0;
        }

        return new double[]{h, s, l};
    }

    private double[] hexToHsl(
            String color
    ) {
        String normalized = color == null ? "" : color.replace("#", "");

        if (normalized.length() != 6) {
            return new double[]{0.0, 0.0, 0.5};
        }

        int r = Integer.parseInt(normalized.substring(0, 2), 16);
        int g = Integer.parseInt(normalized.substring(2, 4), 16);
        int b = Integer.parseInt(normalized.substring(4, 6), 16);

        return rgbToHsl(r, g, b);
    }

    private String rgbToHex(
            int r,
            int g,
            int b
    ) {
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private double round2(
            double value
    ) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round3(
            double value
    ) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private Object firstNonNull(
            Object first,
            Object second
    ) {
        return first == null ? second : first;
    }

    private int toInt(
            Object value
    ) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value == null) {
            return 0;
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String join(
            String... parts
    ) {
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(part.trim());
        }

        return builder.toString();
    }

    private String normalizeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(
            String text,
            String... keywords
    ) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private record AttachmentRow(
            UUID id,
            String fileName,
            String mimeType,
            Long fileSize,
            String storagePath,
            String parseStatus,
            String parsedText,
            Map<String, Object> metadata
    ) {
    }

    private record ServerImageAnalysis(
            String primaryColor,
            List<String> palette,
            double averageLightness,
            double averageSaturation,
            String toneLabel,
            String pageTypeHint,
            List<String> layoutSignals,
            List<String> visualToneSignals,
            List<String> componentSignals,
            String summary
    ) {
    }
}
