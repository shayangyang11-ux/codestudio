package com.codemind.platform.development;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DevelopmentCodebaseSnapshotService {

    private static final DateTimeFormatter SNAPSHOT_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> EXCLUDED_DIRECTORIES = List.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".turbo",
            ".next",
            "coverage",
            ".idea",
            ".vscode",
            "logs"
    );

    private final DevelopmentHandoffGateService developmentHandoffGateService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final DevelopmentGeneratedPathService generatedPathService;
    private final ObjectMapper objectMapper;

    public DevelopmentCodebaseSnapshotService(
            DevelopmentHandoffGateService developmentHandoffGateService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            DevelopmentGeneratedPathService generatedPathService,
            ObjectMapper objectMapper
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.generatedPathService = generatedPathService;
        this.objectMapper = objectMapper;
    }

    public DevelopmentCodebaseSnapshotStatusResponse getSnapshotStatus(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack
    ) {
        DevelopmentHandoffReadinessResponse readiness = developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );
        String resolvedStack = resolveTargetStack(targetStack);
        DevelopmentCodebaseRegistryResponse registry = codebaseRegistryService.resolveCodebase(
                workspaceId,
                readiness.projectId(),
                projectCode,
                resolvedStack
        );

        Path codebaseRoot = Path.of(registry.absoluteRootPath()).toAbsolutePath().normalize();
        boolean codebaseExists = Files.isDirectory(codebaseRoot);
        Path snapshotRoot = snapshotRoot(codebaseRoot);
        List<DevelopmentCodebaseSnapshotResponse> snapshots = codebaseExists
                ? readSnapshots(snapshotRoot)
                : List.of();
        List<String> warnings = new ArrayList<>();

        if (!registry.registered()) {
            warnings.add("当前代码库尚未登记。写入 generated/nodejs 后，平台才会开始维护代码快照。");
        }

        if (!codebaseExists) {
            warnings.add("代码库目录不存在，暂无可用快照。 ");
        }

        String summary = snapshots.isEmpty()
                ? "当前代码库还没有生成快照。首次安全再生成时会自动创建快照。"
                : "当前代码库已有 " + snapshots.size() + " 个快照。后续重新生成代码前会先保存当前版本。";

        return new DevelopmentCodebaseSnapshotStatusResponse(
                readiness.projectId(),
                readiness.moduleId(),
                readiness.moduleKey(),
                readiness.moduleName(),
                projectCode,
                resolvedStack,
                registry,
                registry.platformRootPath(),
                registry.absoluteRootPath(),
                codebaseExists,
                codebaseExists ? generatedPathService.toPlatformGeneratedPath(snapshotRoot) : "",
                snapshots.size(),
                snapshots.isEmpty() ? null : snapshots.get(0),
                snapshots,
                warnings,
                summary,
                LocalDateTime.now()
        );
    }

    public DevelopmentCodebaseSafetyCheckResponse analyzeCodeProject(
            Path codebaseRoot,
            DevelopmentCodeProjectResponse codeProject,
            boolean allowOverwrite
    ) {
        if (codeProject == null || codeProject.files() == null) {
            return new DevelopmentCodebaseSafetyCheckResponse(
                    "EMPTY",
                    true,
                    allowOverwrite,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of("本次没有可写入的代码文件。"),
                    List.of(),
                    LocalDateTime.now()
            );
        }

        Map<String, String> previousHashes = readPreviousGeneratedHashes(codebaseRoot);
        List<DevelopmentCodebaseSafetyFileResponse> files = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int added = 0;
        int modified = 0;
        int unchanged = 0;
        int conflicts = 0;
        int skipped = 0;

        for (DevelopmentCodeFileResponse file : codeProject.files()) {
            if (file == null || file.filePath() == null || file.filePath().isBlank()) {
                continue;
            }

            if (file.filePath().endsWith("/codemind.generated.json")) {
                continue;
            }

            DevelopmentCodebaseSafetyFileResponse safetyFile = analyzeOneFile(file, previousHashes, allowOverwrite);
            files.add(safetyFile);

            switch (safetyFile.changeType()) {
                case "ADDED" -> added++;
                case "MODIFIED", "OVERWRITE_REQUESTED" -> modified++;
                case "UNCHANGED" -> unchanged++;
                case "CONFLICT" -> conflicts++;
                case "SKIPPED_KEEP" -> skipped++;
                default -> {
                }
            }
        }

        if (conflicts > 0 && !allowOverwrite) {
            blockers.add("检测到 " + conflicts + " 个可能被用户修改过的文件，已阻止默认覆盖。请查看冲突文件后再决定是否覆盖写入。 ");
        }

        if (conflicts > 0 && allowOverwrite) {
            warnings.add("本次开启了覆盖写入，" + conflicts + " 个冲突文件会被新生成代码覆盖。覆盖前已自动创建代码快照。 ");
        }

        if (skipped > 0) {
            warnings.add("有 " + skipped + " 个项目主干文件采用 CREATE_OR_KEEP 策略，默认保留现有内容。 ");
        }

        boolean safeToApply = conflicts == 0 || allowOverwrite;
        String status = safeToApply ? "SAFE" : "CONFLICT";

        return new DevelopmentCodebaseSafetyCheckResponse(
                status,
                safeToApply,
                allowOverwrite,
                files.size(),
                added,
                modified,
                unchanged,
                conflicts,
                skipped,
                blockers,
                warnings,
                files,
                LocalDateTime.now()
        );
    }

    public DevelopmentCodebaseSnapshotResponse createSnapshotIfNeeded(
            Path codebaseRoot,
            String projectCode,
            String targetStack,
            String reason
    ) {
        Path normalizedRoot = codebaseRoot.toAbsolutePath().normalize();

        if (!Files.isDirectory(normalizedRoot)) {
            return null;
        }

        List<Path> sourceFiles = listSnapshotSourceFiles(normalizedRoot);

        if (sourceFiles.isEmpty()) {
            return null;
        }

        String baseSnapshotId = LocalDateTime.now().format(SNAPSHOT_ID_FORMAT);
        String snapshotId = baseSnapshotId;
        Path root = snapshotRoot(normalizedRoot);
        Path snapshotDir = root.resolve(snapshotId);
        int suffix = 2;

        while (Files.exists(snapshotDir)) {
            snapshotId = baseSnapshotId + "-" + suffix;
            snapshotDir = root.resolve(snapshotId);
            suffix++;
        }

        Path archivePath = snapshotDir.resolve("codebase.zip");
        Path metadataPath = snapshotDir.resolve("snapshot.json");

        try {
            Files.createDirectories(snapshotDir);

            List<DevelopmentCodebaseSnapshotFileResponse> fileRecords = new ArrayList<>();
            long totalBytes = 0L;

            for (Path file : sourceFiles) {
                long bytes = safeSize(file);
                totalBytes += bytes;
                fileRecords.add(new DevelopmentCodebaseSnapshotFileResponse(
                        normalizeRootRelative(normalizedRoot, file),
                        sha256(file),
                        bytes,
                        modifiedTime(file)
                ));
            }

            String checksum = treeChecksum(fileRecords);
            writeSnapshotArchive(normalizedRoot, sourceFiles, archivePath);

            DevelopmentCodebaseSnapshotResponse response = new DevelopmentCodebaseSnapshotResponse(
                    snapshotId,
                    "CREATED",
                    safeText(reason, "CODEBASE_MATERIALIZE_BEFORE_WRITE"),
                    generatedPathService.toPlatformGeneratedPath(snapshotDir),
                    generatedPathService.toPlatformGeneratedPath(archivePath),
                    archivePath.toString(),
                    fileRecords.size(),
                    totalBytes,
                    checksum,
                    LocalDateTime.now(),
                    List.of("快照会排除 node_modules、构建产物、运行日志和历史快照目录。"),
                    fileRecords
            );

            writeSnapshotMetadata(metadataPath, response, projectCode, targetStack, normalizedRoot);
            return response;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "创建生成代码快照失败。",
                    e
            );
        }
    }

    public void refreshManifestGeneratedFiles(
            Path codebaseRoot,
            DevelopmentCodeProjectResponse codeProject,
            List<DevelopmentCodeMaterializedFileResponse> materializedFiles
    ) {
        if (codebaseRoot == null || codeProject == null || codeProject.files() == null) {
            return;
        }

        Path manifestPath = codebaseRoot.resolve("codemind.generated.json").toAbsolutePath().normalize();

        if (!Files.isRegularFile(manifestPath)) {
            return;
        }

        try {
            Map<String, Object> manifest = objectMapper.readValue(
                    manifestPath.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            List<String> eligiblePaths = materializedFiles == null
                    ? List.of()
                    : materializedFiles.stream()
                    .filter(item -> item != null && (
                            "WRITTEN".equals(item.status()) ||
                                    "OVERWRITTEN".equals(item.status()) ||
                                    "UNCHANGED".equals(item.status())
                    ))
                    .map(DevelopmentCodeMaterializedFileResponse::filePath)
                    .toList();
            List<Map<String, Object>> generatedFiles = new ArrayList<>();

            for (DevelopmentCodeFileResponse file : codeProject.files()) {
                if (file == null || file.filePath() == null || file.filePath().endsWith("/codemind.generated.json")) {
                    continue;
                }

                if (!eligiblePaths.contains(file.filePath())) {
                    continue;
                }

                Path target = generatedPathService.resolvePlatformGeneratedPath(file.filePath());

                if (!Files.isRegularFile(target) || !target.startsWith(codebaseRoot)) {
                    continue;
                }

                Map<String, Object> record = new LinkedHashMap<>();
                record.put("path", file.filePath());
                record.put("relativePath", normalizeRootRelative(codebaseRoot, target));
                record.put("sha256", sha256(target));
                record.put("fileType", file.fileType());
                record.put("action", file.action());
                record.put("source", file.source());
                record.put("language", file.language());
                record.put("bytes", safeSize(target));
                record.put("lastGeneratedAt", LocalDateTime.now().toString());
                generatedFiles.add(record);
            }

            manifest.put("schemaVersion", "1.1");
            manifest.put("fileHashStrategy", "SHA256_AFTER_MATERIALIZE");
            manifest.put("generatedFiles", mergeGeneratedFileRecords(manifest.get("generatedFiles"), generatedFiles));
            manifest.put("updatedAt", LocalDateTime.now().toString());

            Files.writeString(
                    manifestPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest) + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "更新 codemind.generated.json 文件哈希失败。",
                    e
            );
        }
    }

    private DevelopmentCodebaseSafetyFileResponse analyzeOneFile(
            DevelopmentCodeFileResponse file,
            Map<String, String> previousHashes,
            boolean allowOverwrite
    ) {
        Path target = generatedPathService.resolvePlatformGeneratedPath(file.filePath());
        boolean exists = Files.isRegularFile(target);
        String previousSha = previousHashes.get(file.filePath());
        String currentSha = exists ? sha256(target) : null;
        String proposedSha = sha256(file.content() == null ? "" : file.content().strip() + System.lineSeparator());

        if (!exists) {
            return new DevelopmentCodebaseSafetyFileResponse(
                    file.filePath(),
                    file.fileType(),
                    file.action(),
                    "ADDED",
                    true,
                    false,
                    false,
                    previousSha,
                    currentSha,
                    proposedSha,
                    "新文件，可安全写入。"
            );
        }

        if (proposedSha.equals(currentSha)) {
            return new DevelopmentCodebaseSafetyFileResponse(
                    file.filePath(),
                    file.fileType(),
                    file.action(),
                    "UNCHANGED",
                    true,
                    false,
                    false,
                    previousSha,
                    currentSha,
                    proposedSha,
                    "文件内容与本次生成结果一致，无需写入。"
            );
        }

        if ("CREATE_OR_KEEP".equals(file.action()) && !allowOverwrite) {
            return new DevelopmentCodebaseSafetyFileResponse(
                    file.filePath(),
                    file.fileType(),
                    file.action(),
                    "SKIPPED_KEEP",
                    true,
                    false,
                    false,
                    previousSha,
                    currentSha,
                    proposedSha,
                    "项目主干保留文件已存在，默认保持当前内容。"
            );
        }

        if (previousSha != null && previousSha.equals(currentSha)) {
            return new DevelopmentCodebaseSafetyFileResponse(
                    file.filePath(),
                    file.fileType(),
                    file.action(),
                    "MODIFIED",
                    true,
                    false,
                    false,
                    previousSha,
                    currentSha,
                    proposedSha,
                    "磁盘文件仍等于上次 AI 生成版本，可安全更新。"
            );
        }

        return new DevelopmentCodebaseSafetyFileResponse(
                file.filePath(),
                file.fileType(),
                file.action(),
                allowOverwrite ? "OVERWRITE_REQUESTED" : "CONFLICT",
                allowOverwrite,
                true,
                true,
                previousSha,
                currentSha,
                proposedSha,
                allowOverwrite
                        ? "检测到文件可能被用户修改，本次将按用户确认覆盖写入。"
                        : "检测到文件可能被用户修改，默认不会覆盖。"
        );
    }

    private Map<String, String> readPreviousGeneratedHashes(Path codebaseRoot) {
        Path manifestPath = codebaseRoot.resolve("codemind.generated.json").toAbsolutePath().normalize();

        if (!Files.isRegularFile(manifestPath)) {
            return Map.of();
        }

        try {
            Map<String, Object> manifest = objectMapper.readValue(
                    manifestPath.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            Object value = manifest.get("generatedFiles");
            Map<String, String> hashes = new LinkedHashMap<>();

            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }

                    Object path = map.get("path");
                    Object sha = map.get("sha256");

                    if (path != null && sha != null) {
                        hashes.put(String.valueOf(path), String.valueOf(sha));
                    }
                }
            }

            return hashes;
        } catch (IOException e) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> mergeGeneratedFileRecords(
            Object current,
            List<Map<String, Object>> incoming
    ) {
        Map<String, Map<String, Object>> byPath = new LinkedHashMap<>();

        if (current instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && map.get("path") != null) {
                    byPath.put(String.valueOf(map.get("path")), new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }

        for (Map<String, Object> item : incoming) {
            if (item != null && item.get("path") != null) {
                byPath.put(String.valueOf(item.get("path")), item);
            }
        }

        return new ArrayList<>(byPath.values());
    }

    private List<DevelopmentCodebaseSnapshotResponse> readSnapshots(Path snapshotRoot) {
        if (!Files.isDirectory(snapshotRoot)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(snapshotRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(this::readSnapshot)
                    .filter(snapshot -> snapshot != null)
                    .sorted(Comparator.comparing((DevelopmentCodebaseSnapshotResponse snapshot) -> snapshot.createdAt() == null ? LocalDateTime.MIN : snapshot.createdAt()).reversed())
                    .limit(20)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private DevelopmentCodebaseSnapshotResponse readSnapshot(Path snapshotDir) {
        Path metadataPath = snapshotDir.resolve("snapshot.json");

        if (!Files.isRegularFile(metadataPath)) {
            return null;
        }

        try {
            Map<String, Object> metadata = objectMapper.readValue(
                    metadataPath.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            return new DevelopmentCodebaseSnapshotResponse(
                    stringValue(metadata.get("snapshotId")),
                    stringValue(metadata.get("snapshotStatus")),
                    stringValue(metadata.get("reason")),
                    stringValue(metadata.get("snapshotRoot")),
                    stringValue(metadata.get("archivePlatformPath")),
                    stringValue(metadata.get("archiveAbsolutePath")),
                    intValue(metadata.get("fileCount")),
                    longValue(metadata.get("totalBytes")),
                    stringValue(metadata.get("checksum")),
                    parseTime(metadata.get("createdAt")),
                    readStringList(metadata.get("warnings")),
                    List.of()
            );
        } catch (IOException e) {
            return null;
        }
    }

    private void writeSnapshotArchive(
            Path codebaseRoot,
            List<Path> sourceFiles,
            Path archivePath
    ) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivePath), StandardCharsets.UTF_8)) {
            for (Path file : sourceFiles) {
                String entryName = normalizeEntryPath(codebaseRoot.relativize(file));
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(System.currentTimeMillis());
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private void writeSnapshotMetadata(
            Path metadataPath,
            DevelopmentCodebaseSnapshotResponse response,
            String projectCode,
            String targetStack,
            Path codebaseRoot
    ) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "1.0");
        metadata.put("packageType", "CODEMIND_CODEBASE_SNAPSHOT");
        metadata.put("snapshotId", response.snapshotId());
        metadata.put("snapshotStatus", response.snapshotStatus());
        metadata.put("reason", response.reason());
        metadata.put("projectCode", projectCode);
        metadata.put("targetStack", targetStack);
        metadata.put("codebaseRoot", generatedPathService.toPlatformGeneratedPath(codebaseRoot));
        metadata.put("snapshotRoot", response.snapshotRoot());
        metadata.put("archivePlatformPath", response.archivePlatformPath());
        metadata.put("archiveAbsolutePath", response.archiveAbsolutePath());
        metadata.put("fileCount", response.fileCount());
        metadata.put("totalBytes", response.totalBytes());
        metadata.put("checksum", response.checksum());
        metadata.put("createdAt", response.createdAt().toString());
        metadata.put("warnings", response.warnings());
        metadata.put("files", response.files());

        Files.writeString(
                metadataPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata) + System.lineSeparator(),
                StandardCharsets.UTF_8
        );
    }

    private List<Path> listSnapshotSourceFiles(Path codebaseRoot) {
        try (Stream<Path> stream = Files.walk(codebaseRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnoredPath(codebaseRoot, path))
                    .sorted(Comparator.comparing(path -> normalizeEntryPath(codebaseRoot.relativize(path))))
                    .toList();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "扫描生成代码库快照文件失败。",
                    e
            );
        }
    }

    private Path snapshotRoot(Path codebaseRoot) {
        return codebaseRoot.resolve(".codemind").resolve("snapshots").toAbsolutePath().normalize();
    }

    private boolean isIgnoredPath(Path root, Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        String normalized = relative.toString().replace('\\', '/');

        if (normalized.startsWith(".codemind/snapshots/")) {
            return true;
        }

        if (normalized.endsWith(".log") || normalized.endsWith(".env.local") || normalized.endsWith(".env.development.local")) {
            return true;
        }

        for (Path part : relative) {
            String name = part.toString();

            if (EXCLUDED_DIRECTORIES.contains(name)) {
                return true;
            }
        }

        return false;
    }

    private String resolveTargetStack(String targetStack) {
        try {
            return DevelopmentTargetStack.resolve(targetStack).stackCode();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private String normalizeEntryPath(Path path) {
        return path.toString().replace('\\', '/').replaceAll("^/+", "");
    }

    private String normalizeRootRelative(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private long safeSize(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private LocalDateTime modifiedTime(Path path) {
        try {
            FileTime fileTime = Files.getLastModifiedTime(path);
            return LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }

    private String sha256(Path path) {
        try {
            return sha256(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "计算文件哈希失败：" + path, e);
        }
    }

    private String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder();

            for (byte item : encoded) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }

            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256.", e);
        }
    }

    private String treeChecksum(List<DevelopmentCodebaseSnapshotFileResponse> files) {
        StringBuilder builder = new StringBuilder();

        for (DevelopmentCodebaseSnapshotFileResponse file : files) {
            builder.append(file.filePath()).append(':').append(file.sha256()).append('\n');
        }

        return sha256(builder.toString());
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private LocalDateTime parseTime(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }

        return result;
    }
}
