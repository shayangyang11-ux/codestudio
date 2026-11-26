package com.codemind.platform.assistant;

import com.codemind.platform.identity.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AssistantAttachmentService {

    private static final long DEFAULT_MAX_FILE_SIZE_BYTES =
            20L * 1024L * 1024L;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt",
            "md",
            "json",
            "pdf",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "png",
            "jpg",
            "jpeg",
            "webp"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final AssistantAttachmentParserService assistantAttachmentParserService;
    private final AssistantAttachmentStorageService assistantAttachmentStorageService;
    private final long maxFileSizeBytes;

    public AssistantAttachmentService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper,
            AssistantAttachmentParserService assistantAttachmentParserService,
            AssistantAttachmentStorageService assistantAttachmentStorageService,
            AssistantAttachmentStorageProperties storageProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.assistantAttachmentParserService =
                assistantAttachmentParserService;
        this.assistantAttachmentStorageService =
                assistantAttachmentStorageService;
        this.maxFileSizeBytes =
                storageProperties.getMaxSizeBytes() > 0
                        ? storageProperties.getMaxSizeBytes()
                        : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    @Transactional
    public AssistantAttachmentUploadResponse uploadAttachment(
            UUID workspaceId,
            String projectCode,
            MultipartFile file
    ) {
        validateFile(file);

        UUID attachmentId = UUID.randomUUID();

        String originalFileName = sanitizeFileName(
                Optional.ofNullable(
                        file.getOriginalFilename()
                ).orElse("attachment")
        );

        String extension =
                resolveExtension(originalFileName);

        String mimeType =
                resolveMimeType(file);

        Path targetPath =
                assistantAttachmentStorageService.resolveTargetPath(
                        workspaceId,
                        projectCode,
                        attachmentId,
                        originalFileName
                );

        String storageKey =
                assistantAttachmentStorageService.toStorageKey(
                        targetPath
                );

        String parsedText = null;
        String parseStatus = "PENDING";

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "originalFileName",
                originalFileName
        );
        metadata.put(
                "extension",
                extension
        );
        metadata.put(
                "contentType",
                mimeType
        );
        metadata.put(
                "storageVersion",
                "LOCAL_FILE_V2_RELATIVE_KEY"
        );
        metadata.put(
                "storageKey",
                storageKey
        );

        try {
            Files.createDirectories(
                    targetPath.getParent()
            );

            Files.copy(
                    file.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            if (isTextLike(extension, mimeType)) {
                parsedText =
                        extractSmallTextFile(targetPath);

                parseStatus = "PARSED";

                metadata.put(
                        "parser",
                        "TEXT_FILE_BASIC_PARSER"
                );
            } else if (isImage(extension, mimeType)) {
                enrichImageMetadata(
                        targetPath,
                        metadata
                );

                parseStatus = "PENDING";

                metadata.put(
                        "parser",
                        "IMAGE_METADATA_ONLY"
                );
            } else {
                parseStatus = "PENDING";

                metadata.put(
                        "parser",
                        "PENDING_ADVANCED_PARSER"
                );
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "附件保存失败：" + e.getMessage(),
                    e
            );
        }

        insertAttachment(
                attachmentId,
                workspaceId,
                projectCode,
                originalFileName,
                mimeType,
                file.getSize(),
                storageKey,
                parseStatus,
                parsedText,
                metadata
        );

        return parseImmediatelyAndBuildResponse(
                workspaceId,
                projectCode,
                attachmentId,
                originalFileName,
                mimeType,
                file.getSize(),
                parseStatus,
                parsedText,
                metadata
        );
    }

    private AssistantAttachmentUploadResponse parseImmediatelyAndBuildResponse(
            UUID workspaceId,
            String projectCode,
            UUID attachmentId,
            String fileName,
            String mimeType,
            long size,
            String fallbackParseStatus,
            String fallbackParsedText,
            Map<String, Object> fallbackMetadata
    ) {
        AssistantAttachmentRef attachmentRef =
                new AssistantAttachmentRef();

        attachmentRef.setAttachmentId(attachmentId);
        attachmentRef.setFileName(fileName);
        attachmentRef.setMimeType(mimeType);
        attachmentRef.setSize(size);

        try {
            List<AssistantAttachmentParsedContext> contexts =
                    assistantAttachmentParserService.parseAndBuildContext(
                            workspaceId,
                            projectCode,
                            List.of(attachmentRef)
                    );

            if (!contexts.isEmpty()) {
                AssistantAttachmentParsedContext context =
                        contexts.get(0);

                AssistantAttachmentUploadResponse response =
                        new AssistantAttachmentUploadResponse();

                response.setAttachmentId(
                        context.getAttachmentId()
                );
                response.setFileName(
                        context.getFileName()
                );
                response.setMimeType(
                        context.getMimeType()
                );
                response.setSize(
                        context.getSize()
                );
                response.setParseStatus(
                        context.getParseStatus()
                );
                response.setParsedTextExcerpt(
                        context.getParsedTextExcerpt()
                );
                response.setMetadata(
                        context.getMetadata() == null
                                ? Map.of()
                                : context.getMetadata()
                );

                return response;
            }
        } catch (Exception ex) {
            Map<String, Object> failedMetadata =
                    new LinkedHashMap<>(
                            fallbackMetadata == null
                                    ? Map.of()
                                    : fallbackMetadata
                    );

            failedMetadata.put(
                    "parseError",
                    ex.getMessage()
            );
            failedMetadata.put(
                    "parser",
                    "UPLOAD_IMMEDIATE_PARSER"
            );

            updateParseFailure(
                    attachmentId,
                    failedMetadata
            );

            AssistantAttachmentUploadResponse response =
                    new AssistantAttachmentUploadResponse();

            response.setAttachmentId(attachmentId);
            response.setFileName(fileName);
            response.setMimeType(mimeType);
            response.setSize(size);
            response.setParseStatus("FAILED");
            response.setParsedTextExcerpt("");
            response.setMetadata(failedMetadata);

            return response;
        }

        AssistantAttachmentUploadResponse response =
                new AssistantAttachmentUploadResponse();

        response.setAttachmentId(attachmentId);
        response.setFileName(fileName);
        response.setMimeType(mimeType);
        response.setSize(size);
        response.setParseStatus(
                fallbackParseStatus
        );
        response.setParsedTextExcerpt(
                toExcerpt(fallbackParsedText)
        );
        response.setMetadata(
                fallbackMetadata == null
                        ? Map.of()
                        : fallbackMetadata
        );

        return response;
    }

    private void updateParseFailure(
            UUID attachmentId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE assistant_attachment
                SET parse_status = 'FAILED',
                    parsed_text = NULL,
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
                                "metadata",
                                toJson(metadata)
                        )
        );
    }

    @Transactional
    public void bindAttachmentsToMessage(
            UUID workspaceId,
            String projectCode,
            UUID messageId,
            List<AssistantAttachmentRef> attachments
    ) {
        if (
                attachments == null
                        || attachments.isEmpty()
        ) {
            return;
        }

        List<UUID> attachmentIds =
                attachments.stream()
                        .map(
                                AssistantAttachmentRef::getAttachmentId
                        )
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (attachmentIds.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE assistant_attachment
                SET message_id = :messageId
                WHERE workspace_id = :workspaceId
                  AND project_code = :projectCode
                  AND id IN (:attachmentIds)
                """;

        int updated = jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue(
                                "messageId",
                                messageId
                        )
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
                        )
        );

        if (updated != attachmentIds.size()) {
            throw new IllegalArgumentException(
                    "部分附件不存在或不属于当前项目，无法绑定到 AI 消息。"
            );
        }
    }

    private void validateFile(
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "上传附件不能为空。"
            );
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                    "附件超过大小限制，当前限制为 "
                            + maxFileSizeBytes
                            / 1024
                            / 1024
                            + " MB。"
            );
        }

        String fileName = sanitizeFileName(
                Optional.ofNullable(
                        file.getOriginalFilename()
                ).orElse("")
        );

        String extension =
                resolveExtension(fileName);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "不支持的附件类型：" + extension
            );
        }
    }

    private String sanitizeFileName(
            String rawFileName
    ) {
        String fileName =
                rawFileName == null
                        ? "attachment"
                        : rawFileName.trim();

        fileName = Paths.get(fileName)
                .getFileName()
                .toString();

        fileName = fileName.replaceAll(
                "[\\\\/:*?\"<>|]",
                "_"
        );

        fileName = fileName.replaceAll(
                "\\s+",
                "_"
        );

        if (fileName.isBlank()) {
            return "attachment";
        }

        if (fileName.length() > 180) {
            int dotIndex =
                    fileName.lastIndexOf('.');

            String extension =
                    dotIndex >= 0
                            ? fileName.substring(dotIndex)
                            : "";

            String baseName =
                    dotIndex >= 0
                            ? fileName.substring(
                            0,
                            dotIndex
                    )
                            : fileName;

            fileName =
                    baseName.substring(
                            0,
                            Math.min(
                                    baseName.length(),
                                    160
                            )
                    ) + extension;
        }

        return fileName;
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

    private String resolveMimeType(
            MultipartFile file
    ) {
        if (
                file.getContentType() != null
                        && !file.getContentType().isBlank()
        ) {
            return file.getContentType();
        }

        return "application/octet-stream";
    }

    private boolean isTextLike(
            String extension,
            String mimeType
    ) {
        return Set.of(
                "txt",
                "md",
                "json"
        ).contains(extension)
                || mimeType.startsWith("text/");
    }

    private boolean isImage(
            String extension,
            String mimeType
    ) {
        return Set.of(
                "png",
                "jpg",
                "jpeg",
                "webp"
        ).contains(extension)
                || mimeType.startsWith("image/");
    }

    private String extractSmallTextFile(
            Path targetPath
    ) throws IOException {
        long maxExtractBytes =
                300L * 1024L;

        byte[] bytes =
                Files.readAllBytes(targetPath);

        if (bytes.length > maxExtractBytes) {
            return new String(
                    Arrays.copyOf(
                            bytes,
                            (int) maxExtractBytes
                    ),
                    StandardCharsets.UTF_8
            ) + "\n\n[内容过长，已截断。]";
        }

        return new String(
                bytes,
                StandardCharsets.UTF_8
        );
    }

    private void enrichImageMetadata(
            Path targetPath,
            Map<String, Object> metadata
    ) {
        try {
            BufferedImage image =
                    ImageIO.read(
                            targetPath.toFile()
                    );

            if (image != null) {
                metadata.put(
                        "imageWidth",
                        image.getWidth()
                );
                metadata.put(
                        "imageHeight",
                        image.getHeight()
                );
            }
        } catch (IOException ignored) {
            metadata.put(
                    "imageMetadataWarning",
                    "图片尺寸读取失败。"
            );
        }
    }


    @Transactional(readOnly = true)
    public AttachmentContent loadAttachmentContent(
            UUID workspaceId,
            String projectCode,
            UUID attachmentId
    ) {
        String sql = """
                SELECT
                    file_name,
                    mime_type,
                    storage_path
                FROM assistant_attachment
                WHERE id = :attachmentId
                  AND workspace_id = :workspaceId
                  AND project_code = :projectCode
                LIMIT 1
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("attachmentId", attachmentId)
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectCode", projectCode)
        );

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("附件不存在或无权访问。");
        }

        Map<String, Object> row = rows.get(0);

        String fileName = Objects.toString(
                row.get("file_name"),
                "attachment"
        );

        String mimeType = Objects.toString(
                row.get("mime_type"),
                "application/octet-stream"
        );

        String storagePath = Objects.toString(
                row.get("storage_path"),
                ""
        );

        Path path =
                assistantAttachmentStorageService.resolveStoredPath(
                        storagePath
                );

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("附件文件不存在或已被移除。");
        }

        return new AttachmentContent(
                path,
                fileName,
                mimeType
        );
    }

    public static class AttachmentContent {

        private final Path path;
        private final String fileName;
        private final String mimeType;

        public AttachmentContent(
                Path path,
                String fileName,
                String mimeType
        ) {
            this.path = path;
            this.fileName = fileName;
            this.mimeType = mimeType;
        }

        public Path getPath() {
            return path;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    private void insertAttachment(
            UUID attachmentId,
            UUID workspaceId,
            String projectCode,
            String fileName,
            String mimeType,
            long fileSize,
            String storagePath,
            String parseStatus,
            String parsedText,
            Map<String, Object> metadata
    ) {
        String sql = """
                INSERT INTO assistant_attachment (
                    id,
                    workspace_id,
                    project_code,
                    message_id,
                    file_name,
                    mime_type,
                    file_size,
                    storage_path,
                    parse_status,
                    parsed_text,
                    metadata,
                    created_by,
                    created_at
                )
                VALUES (
                    :id,
                    :workspaceId,
                    :projectCode,
                    NULL,
                    :fileName,
                    :mimeType,
                    :fileSize,
                    :storagePath,
                    :parseStatus,
                    :parsedText,
                    CAST(:metadata AS jsonb),
                    :createdBy,
                    CURRENT_TIMESTAMP
                )
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue(
                                "id",
                                attachmentId
                        )
                        .addValue(
                                "workspaceId",
                                workspaceId
                        )
                        .addValue(
                                "projectCode",
                                projectCode
                        )
                        .addValue(
                                "fileName",
                                fileName
                        )
                        .addValue(
                                "mimeType",
                                mimeType
                        )
                        .addValue(
                                "fileSize",
                                fileSize
                        )
                        .addValue(
                                "storagePath",
                                storagePath
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
                        .addValue(
                                "createdBy",
                                currentUserProvider
                                        .getCurrentUser()
                                        .username()
                        )
        );
    }

    private String toExcerpt(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        if (normalized.length() <= 800) {
            return normalized;
        }

        return normalized.substring(0, 800)
                + "\n\n[上传即时摘录过长，已截断。]";
    }

    private String toJson(
            Object value
    ) {
        try {
            return objectMapper
                    .writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "附件元数据序列化失败。",
                    e
            );
        }
    }
}