package com.codemind.platform.development;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Locale;

@Service
public class DevelopmentGeneratedPathService {

    @Value("${codemind.development.generated-root:}")
    private String configuredGeneratedRoot;

    @Value("${codemind.development.workspace-root:}")
    private String configuredWorkspaceRoot;

    public Path resolveGeneratedStorageRoot() {
        String configured = firstNonBlank(
                configuredGeneratedRoot,
                System.getProperty("codemind.development.generated-root"),
                System.getenv("CODEMIND_DEVELOPMENT_GENERATED_ROOT")
        );

        if (configured != null) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        return resolveWorkspaceRoot().resolve("generated").toAbsolutePath().normalize();
    }

    public String storageRootKey() {
        String configured = firstNonBlank(
                configuredGeneratedRoot,
                System.getProperty("codemind.development.generated-root"),
                System.getenv("CODEMIND_DEVELOPMENT_GENERATED_ROOT")
        );

        if (configured != null) {
            return "CONFIGURED_GENERATED_ROOT";
        }

        return "WORKSPACE_GENERATED_ROOT";
    }

    public Path resolveWorkspaceRoot() {
        String configured = firstNonBlank(
                configuredWorkspaceRoot,
                System.getProperty("codemind.development.workspace-root"),
                System.getenv("CODEMIND_WORKSPACE_ROOT")
        );

        if (configured != null) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        if (
                current.getFileName() != null &&
                        "codemind-server".equalsIgnoreCase(current.getFileName().toString()) &&
                        current.getParent() != null
        ) {
            return current.getParent().toAbsolutePath().normalize();
        }

        return current;
    }

    public String buildRelativeCodebaseRoot(String projectCode, String targetStack) {
        String stack = targetStack == null || targetStack.isBlank()
                ? "NODEJS_FULLSTACK"
                : targetStack.trim().toUpperCase(Locale.ROOT);

        if (!"NODEJS_FULLSTACK".equals(stack)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前代码库登记第一版只支持 NODEJS_FULLSTACK。"
            );
        }

        return "nodejs/" + toPathSegment(projectCode, "project") + "/app";
    }

    public String buildPlatformCodebaseRoot(String projectCode, String targetStack) {
        return toPlatformPath(buildRelativeCodebaseRoot(projectCode, targetStack));
    }

    public String toPlatformPath(String relativeGeneratedPath) {
        String normalized = normalizeRelativeGeneratedPath(relativeGeneratedPath);
        return "generated/" + normalized;
    }

    public String toRelativeGeneratedPath(String platformOrRelativePath) {
        String normalized = platformOrRelativePath == null
                ? ""
                : platformOrRelativePath.replace('\\', '/').replaceAll("^/+", "").trim();

        if (normalized.startsWith("generated/")) {
            normalized = normalized.substring("generated/".length());
        }

        return normalizeRelativeGeneratedPath(normalized);
    }

    public Path resolvePlatformGeneratedPath(String platformOrRelativePath) {
        return resolveRelativeGeneratedPath(toRelativeGeneratedPath(platformOrRelativePath));
    }

    public Path resolveRelativeGeneratedPath(String relativeGeneratedPath) {
        String normalized = normalizeRelativeGeneratedPath(relativeGeneratedPath);
        Path relative = Path.of(normalized).normalize();

        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生成文件路径不安全：" + relativeGeneratedPath);
        }

        Path storageRoot = resolveGeneratedStorageRoot();
        Path target = storageRoot.resolve(relative).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生成文件路径越界：" + relativeGeneratedPath);
        }

        return target;
    }

    public String toPlatformGeneratedPath(Path absolutePath) {
        Path storageRoot = resolveGeneratedStorageRoot();
        Path normalized = absolutePath.toAbsolutePath().normalize();

        if (!normalized.startsWith(storageRoot)) {
            return normalized.toString().replace('\\', '/');
        }

        return toPlatformPath(storageRoot.relativize(normalized).toString().replace('\\', '/'));
    }

    public String manifestRelativePath(String relativeCodebaseRoot) {
        return normalizeRelativeGeneratedPath(relativeCodebaseRoot) + "/codemind.generated.json";
    }

    public String manifestPlatformPath(String relativeCodebaseRoot) {
        return toPlatformPath(manifestRelativePath(relativeCodebaseRoot));
    }

    public String normalizeRelativeGeneratedPath(String path) {
        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生成路径不能为空。");
        }

        String normalized = path.replace('\\', '/').replaceAll("^/+", "").trim();

        if (
                normalized.contains("\u0000") ||
                        normalized.startsWith("../") ||
                        normalized.contains("/../") ||
                        normalized.equals("..")
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生成路径不安全：" + path);
        }

        if (normalized.startsWith("generated/")) {
            normalized = normalized.substring("generated/".length());
        }

        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生成路径不能为空。");
        }

        return normalized;
    }

    public String toPathSegment(String primary, String fallback) {
        String source = primary == null || primary.isBlank() ? fallback : primary;
        String normalized = source == null ? "node" : source.trim()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[^\u4e00-\u9fa5a-zA-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(Locale.ROOT);

        return normalized.isBlank() ? "node" : normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }
}
