package com.codemind.platform.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AssistantAttachmentParserService {

    private static final int MAX_STORED_TEXT_CHARS =
            60_000;

    private static final int MAX_CONTEXT_EXCERPT_CHARS =
            8_000;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AssistantAttachmentStorageService assistantAttachmentStorageService;

    public AssistantAttachmentParserService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AssistantAttachmentStorageService assistantAttachmentStorageService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.assistantAttachmentStorageService =
                assistantAttachmentStorageService;
    }

    @Transactional
    public List<AssistantAttachmentParsedContext> parseAndBuildContext(
            UUID workspaceId,
            String projectCode,
            List<AssistantAttachmentRef> attachments
    ) {
        if (
                attachments == null
                        || attachments.isEmpty()
        ) {
            return List.of();
        }

        List<UUID> attachmentIds =
                attachments.stream()
                        .map(
                                AssistantAttachmentRef::getAttachmentId
                        )
                        .filter(
                                java.util.Objects::nonNull
                        )
                        .distinct()
                        .toList();

        if (attachmentIds.isEmpty()) {
            return List.of();
        }

        List<AttachmentRow> rows =
                loadAttachments(
                        workspaceId,
                        projectCode,
                        attachmentIds
                );

        if (rows.size() != attachmentIds.size()) {
            throw new IllegalArgumentException(
                    "部分附件不存在或不属于当前项目，无法解析。"
            );
        }

        List<AssistantAttachmentParsedContext> contexts =
                new ArrayList<>();

        for (AttachmentRow row : rows) {
            AttachmentRow parsedRow = row;

            if (shouldParse(row)) {
                parsedRow = parseAttachment(row);
            }

            contexts.add(
                    toContext(parsedRow)
            );
        }

        return contexts;
    }

    private boolean shouldParse(
            AttachmentRow row
    ) {
        if (
                row.parsedText() != null
                        && !row.parsedText().isBlank()
        ) {
            return false;
        }

        String status =
                nullToEmpty(
                        row.parseStatus()
                ).toUpperCase(Locale.ROOT);

        return !status.equals("PARSED")
                && !status.equals("FAILED");
    }

    private AttachmentRow parseAttachment(
            AttachmentRow row
    ) {
        Path path;

        try {
            path =
                    assistantAttachmentStorageService.resolveStoredPath(
                            row.storagePath()
                    );
        } catch (Exception ex) {
            Map<String, Object> metadata =
                    new LinkedHashMap<>(
                            row.metadata()
                    );

            metadata.put(
                    "parseError",
                    ex.getMessage()
            );

            updateParseResult(
                    row.id(),
                    "FAILED",
                    null,
                    metadata
            );

            return row.withParseResult(
                    "FAILED",
                    null,
                    metadata
            );
        }

        if (
                !Files.exists(path)
                        || !Files.isRegularFile(path)
        ) {
            Map<String, Object> metadata =
                    new LinkedHashMap<>(
                            row.metadata()
                    );

            metadata.put(
                    "parseError",
                    "附件文件不存在或不是普通文件。"
            );
            metadata.put(
                    "resolvedStoragePath",
                    path.toString()
            );

            updateParseResult(
                    row.id(),
                    "FAILED",
                    null,
                    metadata
            );

            return row.withParseResult(
                    "FAILED",
                    null,
                    metadata
            );
        }

        String extension =
                resolveExtension(row.fileName());

        Map<String, Object> metadata =
                new LinkedHashMap<>(
                        row.metadata()
                );

        metadata.put(
                "parserVersion",
                "ATTACHMENT_PARSER_V2"
        );

        try {
            String parsedText =
                    switch (extension) {
                        case "txt", "md", "json" ->
                                parseText(path);

                        case "pdf" ->
                                parsePdf(
                                        path,
                                        metadata
                                );

                        case "docx" ->
                                parseDocx(
                                        path,
                                        metadata
                                );

                        case "doc" ->
                                parseDoc(
                                        path,
                                        metadata
                                );

                        case "xlsx" ->
                                parseXlsx(
                                        path,
                                        metadata
                                );

                        case "xls" ->
                                parseXls(
                                        path,
                                        metadata
                                );

                        case "png",
                             "jpg",
                             "jpeg",
                             "webp" -> {
                            metadata.put(
                                    "parser",
                                    "IMAGE_METADATA_ONLY"
                            );
                            metadata.put(
                                    "visionStatus",
                                    "PENDING_VISION_PARSER"
                            );
                            yield "";
                        }

                        default -> {
                            metadata.put(
                                    "parser",
                                    "UNSUPPORTED_ADVANCED_PARSER"
                            );
                            yield "";
                        }
                    };

            String normalizedText =
                    normalizeParsedText(
                            parsedText
                    );

            String parseStatus =
                    normalizedText.isBlank()
                            ? "PENDING"
                            : "PARSED";

            if (
                    Set.of(
                            "png",
                            "jpg",
                            "jpeg",
                            "webp"
                    ).contains(extension)
            ) {
                parseStatus = "PENDING";
            }

            metadata.put(
                    "extension",
                    extension
            );
            metadata.put(
                    "parsedTextLength",
                    normalizedText.length()
            );

            updateParseResult(
                    row.id(),
                    parseStatus,
                    normalizedText.isBlank()
                            ? null
                            : normalizedText,
                    metadata
            );

            return row.withParseResult(
                    parseStatus,
                    normalizedText.isBlank()
                            ? null
                            : normalizedText,
                    metadata
            );
        } catch (Exception e) {
            metadata.put(
                    "parseError",
                    e.getMessage()
            );

            updateParseResult(
                    row.id(),
                    "FAILED",
                    null,
                    metadata
            );

            return row.withParseResult(
                    "FAILED",
                    null,
                    metadata
            );
        }
    }

    private String parseText(
            Path path
    ) throws IOException {
        byte[] bytes =
                Files.readAllBytes(path);

        return new String(
                bytes,
                StandardCharsets.UTF_8
        );
    }

    private String parsePdf(
            Path path,
            Map<String, Object> metadata
    ) throws IOException {
        try (
                PDDocument document =
                        Loader.loadPDF(
                                path.toFile()
                        )
        ) {
            metadata.put(
                    "parser",
                    "PDFBOX_TEXT_STRIPPER"
            );
            metadata.put(
                    "pageCount",
                    document.getNumberOfPages()
            );

            PDFTextStripper stripper =
                    new PDFTextStripper();

            return stripper.getText(document);
        }
    }

    private String parseDocx(
            Path path,
            Map<String, Object> metadata
    ) throws IOException {
        StringBuilder builder =
                new StringBuilder();

        try (
                InputStream inputStream =
                        Files.newInputStream(path);

                XWPFDocument document =
                        new XWPFDocument(inputStream)
        ) {
            metadata.put(
                    "parser",
                    "POI_XWPF"
            );
            metadata.put(
                    "paragraphCount",
                    document.getParagraphs().size()
            );
            metadata.put(
                    "tableCount",
                    document.getTables().size()
            );

            for (
                    XWPFParagraph paragraph
                    : document.getParagraphs()
            ) {
                String text =
                        paragraph.getText();

                if (
                        text != null
                                && !text.isBlank()
                ) {
                    builder
                            .append(text)
                            .append('\n');
                }
            }

            for (
                    XWPFTable table
                    : document.getTables()
            ) {
                for (
                        XWPFTableRow row
                        : table.getRows()
                ) {
                    List<String> cells =
                            new ArrayList<>();

                    for (
                            XWPFTableCell cell
                            : row.getTableCells()
                    ) {
                        String text =
                                cell.getText();

                        if (
                                text != null
                                        && !text.isBlank()
                        ) {
                            cells.add(
                                    text.replace(
                                            '\n',
                                            ' '
                                    ).trim()
                            );
                        }
                    }

                    if (!cells.isEmpty()) {
                        builder
                                .append(
                                        String.join(
                                                " | ",
                                                cells
                                        )
                                )
                                .append('\n');
                    }
                }
            }
        }

        return builder.toString();
    }

    private String parseDoc(
            Path path,
            Map<String, Object> metadata
    ) throws IOException {
        try (
                InputStream inputStream =
                        Files.newInputStream(path);

                HWPFDocument document =
                        new HWPFDocument(inputStream);

                WordExtractor extractor =
                        new WordExtractor(document)
        ) {
            metadata.put(
                    "parser",
                    "POI_HWPF"
            );

            return extractor.getText();
        }
    }

    private String parseXlsx(
            Path path,
            Map<String, Object> metadata
    ) throws IOException {
        try (
                InputStream inputStream =
                        Files.newInputStream(path);

                Workbook workbook =
                        new XSSFWorkbook(inputStream)
        ) {
            metadata.put(
                    "parser",
                    "POI_XSSF"
            );
            metadata.put(
                    "sheetCount",
                    workbook.getNumberOfSheets()
            );

            return extractWorkbookText(workbook);
        }
    }

    private String parseXls(
            Path path,
            Map<String, Object> metadata
    ) throws IOException {
        try (
                InputStream inputStream =
                        Files.newInputStream(path);

                Workbook workbook =
                        new HSSFWorkbook(inputStream)
        ) {
            metadata.put(
                    "parser",
                    "POI_HSSF"
            );
            metadata.put(
                    "sheetCount",
                    workbook.getNumberOfSheets()
            );

            return extractWorkbookText(workbook);
        }
    }

    private String extractWorkbookText(
            Workbook workbook
    ) {
        StringBuilder builder =
                new StringBuilder();

        DataFormatter formatter =
                new DataFormatter();

        for (
                int sheetIndex = 0;
                sheetIndex
                        < workbook.getNumberOfSheets();
                sheetIndex++
        ) {
            Sheet sheet =
                    workbook.getSheetAt(sheetIndex);

            builder
                    .append("Sheet: ")
                    .append(
                            sheet.getSheetName()
                    )
                    .append('\n');

            int rowCount = 0;

            for (Row row : sheet) {
                List<String> cells =
                        new ArrayList<>();

                for (Cell cell : row) {
                    String value =
                            formatter.formatCellValue(
                                    cell
                            );

                    if (
                            value != null
                                    && !value.isBlank()
                    ) {
                        cells.add(
                                value.trim()
                        );
                    }
                }

                if (!cells.isEmpty()) {
                    builder
                            .append(
                                    String.join(
                                            " | ",
                                            cells
                                    )
                            )
                            .append('\n');
                }

                rowCount++;

                if (rowCount >= 500) {
                    builder.append(
                            "[该 Sheet 行数较多，仅提取前 500 行。]\n"
                    );
                    break;
                }
            }

            builder.append('\n');
        }

        return builder.toString();
    }

    private AssistantAttachmentParsedContext toContext(
            AttachmentRow row
    ) {
        AssistantAttachmentParsedContext context =
                new AssistantAttachmentParsedContext();

        context.setAttachmentId(row.id());
        context.setFileName(row.fileName());
        context.setMimeType(row.mimeType());
        context.setSize(row.fileSize());
        context.setParseStatus(
                row.parseStatus()
        );
        context.setParsedTextExcerpt(
                toExcerpt(row.parsedText())
        );
        context.setMetadata(row.metadata());

        return context;
    }

    private List<AttachmentRow> loadAttachments(
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
                        .addValue(
                                "workspaceId",
                                workspaceId
                        )
                        .addValue(
                                "projectCode",
                                projectCode
                        )
                        .addValue(
                                "attachmentIds",
                                attachmentIds
                        ),
                (rs, rowNum) ->
                        new AttachmentRow(
                                UUID.fromString(
                                        rs.getString("id")
                                ),
                                rs.getString(
                                        "file_name"
                                ),
                                rs.getString(
                                        "mime_type"
                                ),
                                rs.getLong(
                                        "file_size"
                                ),
                                rs.getString(
                                        "storage_path"
                                ),
                                rs.getString(
                                        "parse_status"
                                ),
                                rs.getString(
                                        "parsed_text"
                                ),
                                parseMetadata(
                                        rs.getString(
                                                "metadata"
                                        )
                                )
                        )
        );
    }

    private void updateParseResult(
            UUID attachmentId,
            String parseStatus,
            String parsedText,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE assistant_attachment
                SET parse_status = :parseStatus,
                    parsed_text = :parsedText,
                    metadata = CAST(:metadata AS jsonb)
                WHERE id = :attachmentId
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue(
                                "attachmentId",
                                attachmentId
                        )
                        .addValue(
                                "parseStatus",
                                parseStatus
                        )
                        .addValue(
                                "parsedText",
                                parsedText
                        )
                        .addValue(
                                "metadata",
                                toJson(metadata)
                        )
        );
    }

    private Map<String, Object> parseMetadata(
            String metadataJson
    ) {
        if (
                metadataJson == null
                        || metadataJson.isBlank()
        ) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(
                    metadataJson,
                    new TypeReference<
                            LinkedHashMap<
                                    String,
                                    Object
                                    >
                            >() {
                    }
            );
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String normalizeParsedText(
            String text
    ) {
        if (text == null) {
            return "";
        }

        String normalized = text
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        if (
                normalized.length()
                        <= MAX_STORED_TEXT_CHARS
        ) {
            return normalized;
        }

        return normalized.substring(
                0,
                MAX_STORED_TEXT_CHARS
        ) + "\n\n[附件解析文本过长，已截断。]";
    }

    private String toExcerpt(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return "";
        }

        if (
                text.length()
                        <= MAX_CONTEXT_EXCERPT_CHARS
        ) {
            return text;
        }

        return text.substring(
                0,
                MAX_CONTEXT_EXCERPT_CHARS
        ) + "\n\n[上下文摘录过长，已截断。]";
    }

    private String resolveExtension(
            String fileName
    ) {
        int dotIndex =
                fileName == null
                        ? -1
                        : fileName.lastIndexOf('.');

        if (
                dotIndex < 0
                        || dotIndex
                        == fileName.length() - 1
        ) {
            return "";
        }

        return fileName.substring(
                dotIndex + 1
        ).toLowerCase(Locale.ROOT);
    }

    private String toJson(
            Object value
    ) {
        try {
            return objectMapper
                    .writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "附件解析元数据序列化失败。",
                    e
            );
        }
    }

    private String nullToEmpty(
            String value
    ) {
        return value == null
                ? ""
                : value;
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
        AttachmentRow withParseResult(
                String nextParseStatus,
                String nextParsedText,
                Map<String, Object> nextMetadata
        ) {
            return new AttachmentRow(
                    id,
                    fileName,
                    mimeType,
                    fileSize,
                    storagePath,
                    nextParseStatus,
                    nextParsedText,
                    nextMetadata
            );
        }
    }
}