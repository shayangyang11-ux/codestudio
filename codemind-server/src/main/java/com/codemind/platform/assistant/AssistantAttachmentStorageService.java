package com.codemind.platform.assistant;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AssistantAttachmentStorageService {

    private static final String STORAGE_DIRECTORY_NAME =
            "assistant-attachments";

    private static final Pattern WINDOWS_ABSOLUTE_PATH =
            Pattern.compile("^[A-Za-z]:[\\\\/].*");

    private final Path storageRoot;

    public AssistantAttachmentStorageService(
            AssistantAttachmentStorageProperties properties
    ) {
        this.storageRoot = Paths
                .get(properties.getStorageRoot())
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "无法初始化 Assistant 附件存储目录："
                            + storageRoot,
                    e
            );
        }
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    public Path resolveTargetPath(
            UUID workspaceId,
            String projectCode,
            UUID attachmentId,
            String safeFileName
    ) {
        String datePart = LocalDate.now().toString();

        String storageFileName =
                attachmentId + "__" + safeFileName;

        String storageKey =
                workspaceId
                        + "/"
                        + projectCode
                        + "/"
                        + datePart
                        + "/"
                        + storageFileName;

        return resolveStorageKey(storageKey);
    }

    public String toStorageKey(
            Path absolutePath
    ) {
        Path normalized = absolutePath
                .toAbsolutePath()
                .normalize();

        if (!normalized.startsWith(storageRoot)) {
            throw new IllegalArgumentException(
                    "附件路径不属于配置的存储根目录。"
            );
        }

        return storageRoot
                .relativize(normalized)
                .toString()
                .replace('\\', '/');
    }

    public Path resolveStoredPath(
            String storedPath
    ) {
        if (
                storedPath == null
                        || storedPath.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "附件存储路径为空。"
            );
        }

        String trimmed = storedPath.trim();
        Path rawPath = Paths.get(trimmed);

        /*
         * 兼容迁移之前数据库中保存的绝对路径。
         * 如果旧文件仍存在，先允许读取。
         */
        if (rawPath.isAbsolute()) {
            Path normalizedAbsolute =
                    rawPath.normalize();

            if (Files.exists(normalizedAbsolute)) {
                return normalizedAbsolute;
            }
        }

        /*
         * 从旧绝对路径中提取 assistant-attachments
         * 后面的相对 storage key，并切换到新根目录。
         */
        Optional<String> legacyStorageKey =
                extractStorageKeyFromLegacyPath(trimmed);

        if (legacyStorageKey.isPresent()) {
            return resolveStorageKey(
                    legacyStorageKey.get()
            );
        }

        if (
                WINDOWS_ABSOLUTE_PATH
                        .matcher(trimmed)
                        .matches()
        ) {
            throw new IllegalArgumentException(
                    "旧附件绝对路径无法映射到新存储根目录："
                            + trimmed
            );
        }

        return resolveStorageKey(trimmed);
    }

    public Path resolveStorageKey(
            String storageKey
    ) {
        if (
                storageKey == null
                        || storageKey.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "附件 storage key 为空。"
            );
        }

        String normalizedKey = storageKey
                .trim()
                .replace('\\', '/');

        while (normalizedKey.startsWith("/")) {
            normalizedKey =
                    normalizedKey.substring(1);
        }

        Path resolved = storageRoot
                .resolve(normalizedKey)
                .normalize();

        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException(
                    "非法附件 storage key。"
            );
        }

        return resolved;
    }

    private Optional<String> extractStorageKeyFromLegacyPath(
            String storedPath
    ) {
        String normalized =
                storedPath.replace('\\', '/');

        String marker =
                "/" + STORAGE_DIRECTORY_NAME + "/";

        int markerIndex = normalized
                .toLowerCase()
                .lastIndexOf(
                        marker.toLowerCase()
                );

        if (markerIndex >= 0) {
            String key = normalized.substring(
                    markerIndex + marker.length()
            );

            return key.isBlank()
                    ? Optional.empty()
                    : Optional.of(key);
        }

        String directPrefix =
                STORAGE_DIRECTORY_NAME + "/";

        if (
                normalized
                        .toLowerCase()
                        .startsWith(
                                directPrefix.toLowerCase()
                        )
        ) {
            String key = normalized.substring(
                    directPrefix.length()
            );

            return key.isBlank()
                    ? Optional.empty()
                    : Optional.of(key);
        }

        return Optional.empty();
    }
}