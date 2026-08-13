package com.codemind.platform.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
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
public class DevelopmentCodebaseExportService {

    private static final long MAX_EXPORT_BYTES = 260L * 1024L * 1024L;
    private static final DateTimeFormatter ZIP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final List<String> EXCLUDED_DIRECTORIES = List.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".turbo",
            ".next",
            "coverage",
            ".idea",
            ".vscode"
    );

    private final DevelopmentHandoffGateService developmentHandoffGateService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final ObjectMapper objectMapper;

    public DevelopmentCodebaseExportService(
            DevelopmentHandoffGateService developmentHandoffGateService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            ObjectMapper objectMapper
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.objectMapper = objectMapper;
    }

    public DevelopmentCodebaseExportStatusResponse getExportStatus(
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
        boolean exists = Files.isDirectory(codebaseRoot);
        boolean manifestExists = Files.isRegularFile(codebaseRoot.resolve("codemind.generated.json"));
        ScanResult scanResult = exists ? scanExportableFiles(codebaseRoot) : ScanResult.empty();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!registry.registered()) {
            blockers.add("当前代码库尚未登记到数据库。请先执行“写入 generated/nodejs”，让平台记录代码库位置。 ");
        }

        if (!exists) {
            blockers.add("代码库物理目录不存在或不可访问：" + registry.absoluteRootPath());
        }

        if (!manifestExists) {
            blockers.add("缺少 codemind.generated.json，无法确认模块树与代码文件映射。请重新生成或重新写入当前节点代码。 ");
        }

        if (scanResult.fileCount() <= 0 && exists) {
            blockers.add("代码库目录为空，暂无可导出的源码文件。 ");
        }

        if (scanResult.totalBytes() > MAX_EXPORT_BYTES) {
            blockers.add("代码库体积超过导出上限 260MB。请删除运行产物、日志或大文件后再导出。 ");
        }

        if (exists) {
            warnings.add("导出 ZIP 会自动排除 node_modules、dist、build、.git、IDE 配置和运行数据文件。 ");
            warnings.add("导出包来自数据库登记的代码库目录，不会重新生成代码。 ");
        }

        String exportStatus = blockers.isEmpty() ? "READY" : "BLOCKED";
        String zipName = buildZipFileName(projectCode, resolvedStack);
        String summary = blockers.isEmpty()
                ? "当前生成代码库已可导出，可下载为一个可交付的 Node.js 全栈源码包。"
                : "当前生成代码库暂不能导出，请先处理阻断项。";

        return new DevelopmentCodebaseExportStatusResponse(
                readiness.projectId(),
                readiness.moduleId(),
                readiness.moduleKey(),
                readiness.moduleName(),
                projectCode,
                resolvedStack,
                exportStatus,
                blockers.isEmpty(),
                registry,
                registry.platformRootPath(),
                registry.absoluteRootPath(),
                exists,
                registry.registered(),
                manifestExists,
                scanResult.fileCount(),
                scanResult.totalBytes(),
                zipName,
                EXCLUDED_DIRECTORIES,
                blockers,
                warnings,
                summary,
                LocalDateTime.now()
        );
    }

    public String buildZipFileName(String projectCode, String targetStack) {
        String projectSegment = toSafeName(projectCode, "project");
        String stackSegment = toSafeName(resolveTargetStack(targetStack), "nodejs-fullstack");
        return "codemind-" + projectSegment + "-" + stackSegment + "-" + LocalDateTime.now().format(ZIP_TIME_FORMAT) + ".zip";
    }

    public void writeZip(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack,
            OutputStream outputStream
    ) {
        DevelopmentCodebaseExportStatusResponse status = getExportStatus(workspaceId, projectCode, moduleId, targetStack);

        if (!status.exportable()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    status.blockers().isEmpty()
                            ? "当前代码库暂不能导出。"
                            : String.join("；", status.blockers())
            );
        }

        Path codebaseRoot = Path.of(status.absoluteCodebaseRoot()).toAbsolutePath().normalize();
        String zipRoot = "codemind-" + toSafeName(projectCode, "project") + "-nodejs-fullstack/";

        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            putDirectory(zip, zipRoot);
            putExportMetadata(zip, zipRoot, status);

            try (Stream<Path> stream = Files.walk(codebaseRoot)) {
                List<Path> files = stream
                        .filter(path -> Files.isRegularFile(path))
                        .filter(path -> !isIgnoredPath(codebaseRoot, path))
                        .sorted(Comparator.comparing(path -> normalizeEntryPath(codebaseRoot.relativize(path))))
                        .toList();

                for (Path file : files) {
                    writeFileEntry(zip, codebaseRoot, file, zipRoot);
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "导出生成代码 ZIP 失败。",
                    e
            );
        }
    }

    private ScanResult scanExportableFiles(Path codebaseRoot) {
        int fileCount = 0;
        long totalBytes = 0L;

        try (Stream<Path> stream = Files.walk(codebaseRoot)) {
            List<Path> files = stream
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> !isIgnoredPath(codebaseRoot, path))
                    .toList();

            for (Path file : files) {
                fileCount++;
                totalBytes += safeSize(file);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "扫描生成代码库失败。",
                    e
            );
        }

        return new ScanResult(fileCount, totalBytes);
    }

    private void putDirectory(ZipOutputStream zip, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(System.currentTimeMillis());
        zip.putNextEntry(entry);
        zip.closeEntry();
    }

    private void putExportMetadata(
            ZipOutputStream zip,
            String zipRoot,
            DevelopmentCodebaseExportStatusResponse status
    ) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "1.0");
        metadata.put("packageType", "CODEMIND_GENERATED_CODEBASE_EXPORT");
        metadata.put("projectId", String.valueOf(status.projectId()));
        metadata.put("moduleId", String.valueOf(status.moduleId()));
        metadata.put("projectCode", status.projectCode());
        metadata.put("targetStack", status.targetStack());
        metadata.put("sourceRelativeRootPath", status.codebaseRegistry() == null ? null : status.codebaseRegistry().relativeRootPath());
        metadata.put("sourceManifestRelativePath", status.codebaseRegistry() == null ? null : status.codebaseRegistry().manifestRelativePath());
        metadata.put("fileCount", status.fileCount());
        metadata.put("totalBytes", status.totalBytes());
        metadata.put("excludedDirectories", status.excludedDirectories());
        metadata.put("exportedAt", LocalDateTime.now().toString());
        metadata.put("notes", List.of(
                "该 ZIP 来自 CodeMind Dev Agent 生成代码库。",
                "node_modules、构建产物、运行日志和本地运行数据已自动排除。",
                "解压后在包根目录执行 npm install，再执行 npm run dev。"
        ));

        byte[] content = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(metadata)
                .getBytes(StandardCharsets.UTF_8);

        ZipEntry entry = new ZipEntry(zipRoot + "codemind.export.json");
        entry.setTime(System.currentTimeMillis());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private void writeFileEntry(
            ZipOutputStream zip,
            Path codebaseRoot,
            Path file,
            String zipRoot
    ) throws IOException {
        Path normalizedFile = file.toAbsolutePath().normalize();

        if (!normalizedFile.startsWith(codebaseRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导出文件路径越界。 ");
        }

        String relative = normalizeEntryPath(codebaseRoot.relativize(normalizedFile));
        ZipEntry entry = new ZipEntry(zipRoot + relative);
        entry.setTime(Files.getLastModifiedTime(normalizedFile).toMillis());
        zip.putNextEntry(entry);
        Files.copy(normalizedFile, zip);
        zip.closeEntry();
    }

    private boolean isIgnoredPath(Path codebaseRoot, Path path) {
        Path relative = codebaseRoot.relativize(path.toAbsolutePath().normalize());

        for (Path part : relative) {
            String name = part.toString();

            if (EXCLUDED_DIRECTORIES.contains(name)) {
                return true;
            }
        }

        String normalized = normalizeEntryPath(relative);
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();

        return normalized.equals("backend/data/local-store.json") ||
                normalized.startsWith("backend/data/") ||
                normalized.startsWith("logs/") ||
                normalized.endsWith(".log") ||
                fileName.equals(".DS_Store") ||
                fileName.equals(".env.local") ||
                fileName.equals(".env.development.local");
    }

    private String normalizeEntryPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private String resolveTargetStack(String targetStack) {
        try {
            return DevelopmentTargetStack.resolve(targetStack).stackCode();
        } catch (IllegalArgumentException e) {
            return "NODEJS_FULLSTACK";
        }
    }

    private String toSafeName(String value, String fallback) {
        String source = value == null || value.isBlank() ? fallback : value;
        String normalized = source.trim()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(Locale.ROOT);

        return normalized.isBlank() ? fallback : normalized;
    }

    private record ScanResult(int fileCount, long totalBytes) {
        static ScanResult empty() {
            return new ScanResult(0, 0L);
        }
    }
}
