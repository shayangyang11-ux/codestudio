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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DevelopmentCodebaseWorkspaceService {

    private static final int MAX_TREE_ITEMS = 800;

    private final DevelopmentHandoffGateService developmentHandoffGateService;
    private final DevelopmentGeneratedPathService generatedPathService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final ObjectMapper objectMapper;

    public DevelopmentCodebaseWorkspaceService(
            DevelopmentHandoffGateService developmentHandoffGateService,
            DevelopmentGeneratedPathService generatedPathService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            ObjectMapper objectMapper
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
        this.generatedPathService = generatedPathService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.objectMapper = objectMapper;
    }

    public DevelopmentCodebaseWorkspaceResponse getWorkspace(
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

        String resolvedTargetStack = resolveTargetStack(targetStack);
        DevelopmentCodebaseRegistryResponse codebaseRegistry = codebaseRegistryService.resolveCodebase(
                workspaceId,
                readiness.projectId(),
                projectCode,
                resolvedTargetStack
        );
        Path workspaceRoot = generatedPathService.resolveGeneratedStorageRoot();
        String codebaseRoot = codebaseRegistry.platformRootPath();
        Path absoluteCodebaseRoot = Path.of(codebaseRegistry.absoluteRootPath()).toAbsolutePath().normalize();
        boolean exists = Files.isDirectory(absoluteCodebaseRoot);
        List<DevelopmentCodebaseFileTreeItemResponse> fileTree = exists
                ? readFileTree(absoluteCodebaseRoot, workspaceRoot)
                : List.of();

        Map<String, Object> manifest = readManifest(absoluteCodebaseRoot);
        List<DevelopmentCodebaseNodeFileResponse> nodeFiles = readNodeFiles(
                manifest,
                workspaceRoot,
                String.valueOf(moduleId),
                readiness.nodeType(),
                readiness.moduleName()
        );

        boolean selectedNodeHasCode = !nodeFiles.isEmpty();
        boolean selectedNodeCanGenerate = canGenerateCurrentNode(readiness);
        List<String> missingRequiredArtifacts = missingRequiredArtifacts(readiness);
        List<String> blockers = issueDescriptions(readiness.blockers());
        List<String> warnings = issueDescriptions(readiness.warnings());
        String defaultFilePath = chooseDefaultFile(fileTree, nodeFiles, codebaseRoot);
        String previewRoute = previewRoute(manifest, String.valueOf(moduleId), readiness);
        String status = resolveStatus(exists, selectedNodeHasCode, readiness, selectedNodeCanGenerate);

        return new DevelopmentCodebaseWorkspaceResponse(
                readiness.projectId(),
                readiness.moduleId(),
                readiness.moduleKey(),
                readiness.moduleName(),
                readiness.moduleType(),
                readiness.nodeType(),
                resolvedTargetStack,
                status,
                codebaseRegistry,
                codebaseRoot,
                absoluteCodebaseRoot.toString(),
                exists,
                selectedNodeHasCode,
                selectedNodeCanGenerate,
                generateActionLabel(readiness),
                readiness.readinessLevel(),
                readiness.ready(),
                readiness.score(),
                missingRequiredArtifacts,
                blockers,
                warnings,
                fileTree,
                nodeFiles,
                defaultFilePath,
                previewRoute,
                buildSummary(readiness, exists, selectedNodeHasCode, selectedNodeCanGenerate, missingRequiredArtifacts),
                LocalDateTime.now()
        );
    }

    public DevelopmentCodebaseFileContentResponse readFileContent(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack,
            String filePath
    ) {
        // Keep access tied to an existing project/module permission path.
        developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );

        if (filePath == null || filePath.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "文件路径不能为空。"
            );
        }

        DevelopmentHandoffReadinessResponse readiness = developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );
        DevelopmentCodebaseRegistryResponse codebaseRegistry = codebaseRegistryService.resolveCodebase(
                workspaceId,
                readiness.projectId(),
                projectCode,
                resolveTargetStack(targetStack)
        );
        Path workspaceRoot = generatedPathService.resolveGeneratedStorageRoot();
        Path absoluteCodebaseRoot = Path.of(codebaseRegistry.absoluteRootPath()).toAbsolutePath().normalize();
        Path targetPath = generatedPathService.resolvePlatformGeneratedPath(filePath);

        if (!targetPath.startsWith(absoluteCodebaseRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只能读取当前生成项目目录下的文件。"
            );
        }

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "文件不存在：" + filePath
            );
        }

        if (isIgnoredPath(targetPath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "不允许读取该目录下的文件。"
            );
        }

        try {
            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            return new DevelopmentCodebaseFileContentResponse(
                    normalizeRootRelative(workspaceRoot, targetPath),
                    targetPath.toString(),
                    detectLanguage(targetPath.getFileName().toString()),
                    content,
                    Files.size(targetPath),
                    modifiedTime(targetPath)
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取生成代码文件失败。",
                    e
            );
        }
    }

    private String resolveTargetStack(String targetStack) {
        try {
            return DevelopmentTargetStack.resolve(targetStack).stackCode();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage(),
                    ex
            );
        }
    }

    private boolean canGenerateCurrentNode(DevelopmentHandoffReadinessResponse readiness) {
        return readiness != null
                && readiness.directDevelopmentAllowed()
                && readiness.ready();
    }

    private String generateActionLabel(DevelopmentHandoffReadinessResponse readiness) {
        String nodeType = safeUpper(readiness == null ? null : readiness.nodeType());

        return switch (nodeType) {
            case "MODULE" -> "生成 / 更新当前模块骨架代码";
            case "SUBMODULE" -> "随所属模块生成子流程代码贡献";
            case "PAGE" -> "生成 / 更新当前页面代码";
            case "FEATURE" -> "随父页面生成当前功能代码贡献";
            case "DOMAIN" -> "业务域默认作为开发上下文，不直接生成代码";
            default -> "生成 / 更新当前节点代码";
        };
    }

    private String resolveStatus(
            boolean exists,
            boolean selectedNodeHasCode,
            DevelopmentHandoffReadinessResponse readiness,
            boolean selectedNodeCanGenerate
    ) {
        if (selectedNodeHasCode) {
            return "NODE_CODE_READY";
        }

        if (!exists) {
            return readiness.ready() && selectedNodeCanGenerate
                    ? "READY_NO_CODEBASE"
                    : "NOT_READY_NO_CODEBASE";
        }

        return readiness.ready() && selectedNodeCanGenerate
                ? "CODEBASE_READY_NODE_NOT_GENERATED"
                : "CODEBASE_EXISTS_NODE_NOT_DIRECT_GENERATION_UNIT";
    }

    private String buildSummary(
            DevelopmentHandoffReadinessResponse readiness,
            boolean exists,
            boolean selectedNodeHasCode,
            boolean selectedNodeCanGenerate,
            List<String> missingRequiredArtifacts
    ) {
        String nodeName = safeText(readiness.moduleName(), "当前节点");
        String nodeType = safeUpper(readiness.nodeType());

        if (selectedNodeHasCode) {
            return "当前节点“" + nodeName + "”已有代码贡献记录，可按模块树查看关联文件，也可切换到项目目录视角查看完整 Node.js 代码包。";
        }

        if (!readiness.ready()) {
            return "当前节点“" + nodeName + "”尚未达到开发 Agent 准备度要求。请先补齐缺失资产，再生成代码。";
        }

        if (!selectedNodeCanGenerate) {
            return "当前节点“" + nodeName + "”作为 " + nodeType + " 上下文使用，默认不直接生成代码；它的下层模块、页面或功能会继承该上下文。";
        }

        if (!exists) {
            return "Node.js 项目代码主干尚未初始化。生成当前节点代码时会先补齐项目主干，再写入该节点的代码贡献。";
        }

        return "当前节点“" + nodeName + "”已具备开发输入，可以生成或更新其在 Node.js 项目主干中的代码贡献。";
    }

    private List<String> missingRequiredArtifacts(DevelopmentHandoffReadinessResponse readiness) {
        if (readiness == null || readiness.assets() == null) {
            return List.of();
        }

        List<String> missing = new ArrayList<>();

        for (DevelopmentHandoffAssetResponse asset : readiness.assets()) {
            if (asset == null || !asset.required()) {
                continue;
            }

            if (!asset.exists() || !asset.contentReady() || "REJECTED".equalsIgnoreCase(asset.status())) {
                missing.add(asset.artifactType() + "：" + safeText(asset.statusText(), "未准备好"));
            }
        }

        return missing;
    }

    private List<String> issueDescriptions(List<DevelopmentHandoffIssueResponse> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();

        for (DevelopmentHandoffIssueResponse issue : issues) {
            if (issue != null) {
                values.add(safeText(issue.title(), "检查项") + "：" + safeText(issue.description(), "-"));
            }
        }

        return values;
    }

    private List<DevelopmentCodebaseFileTreeItemResponse> readFileTree(
            Path root,
            Path workspaceRoot
    ) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try {
            List<Path> children;

            try (Stream<Path> stream = Files.list(root)) {
                children = stream
                        .filter(path -> !isIgnoredPath(path))
                        .sorted(treeComparator())
                        .limit(MAX_TREE_ITEMS)
                        .toList();
            }

            List<DevelopmentCodebaseFileTreeItemResponse> items = new ArrayList<>();

            for (Path child : children) {
                items.add(toTreeItem(child, workspaceRoot, 0));
            }

            return items;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取生成项目目录失败。",
                    e
            );
        }
    }

    private DevelopmentCodebaseFileTreeItemResponse toTreeItem(
            Path path,
            Path workspaceRoot,
            int depth
    ) throws IOException {
        boolean directory = Files.isDirectory(path);
        List<DevelopmentCodebaseFileTreeItemResponse> children = List.of();

        if (directory && depth < 8) {
            try (Stream<Path> stream = Files.list(path)) {
                children = stream
                        .filter(child -> !isIgnoredPath(child))
                        .sorted(treeComparator())
                        .limit(160)
                        .map(child -> {
                            try {
                                return toTreeItem(child, workspaceRoot, depth + 1);
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .toList();
            }
        }

        return new DevelopmentCodebaseFileTreeItemResponse(
                path.getFileName() == null ? path.toString() : path.getFileName().toString(),
                normalizeRootRelative(workspaceRoot, path),
                directory ? "DIRECTORY" : "FILE",
                directory ? "" : detectLanguage(path.getFileName() == null ? "" : path.getFileName().toString()),
                directory ? 0 : safeSize(path),
                modifiedTime(path),
                children
        );
    }

    private Comparator<Path> treeComparator() {
        return (left, right) -> {
            boolean leftDirectory = Files.isDirectory(left);
            boolean rightDirectory = Files.isDirectory(right);

            if (leftDirectory != rightDirectory) {
                return leftDirectory ? -1 : 1;
            }

            return left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString());
        };
    }

    private boolean isIgnoredPath(Path path) {
        for (Path part : path) {
            String name = part.toString();

            if (
                    "node_modules".equals(name) ||
                            ".git".equals(name) ||
                            "dist".equals(name) ||
                            "build".equals(name) ||
                            ".DS_Store".equals(name)
            ) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Object> readManifest(Path absoluteCodebaseRoot) {
        Path manifestPath = absoluteCodebaseRoot.resolve("codemind.generated.json");

        if (!Files.exists(manifestPath) || !Files.isRegularFile(manifestPath)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    manifestPath.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (IOException e) {
            return Map.of(
                    "manifestReadError",
                    "codemind.generated.json 解析失败：" + e.getMessage()
            );
        }
    }

    private List<DevelopmentCodebaseNodeFileResponse> readNodeFiles(
            Map<String, Object> manifest,
            Path workspaceRoot,
            String nodeId,
            String fallbackNodeType,
            String fallbackNodeName
    ) {
        Object nodesObject = manifest.get("nodes");

        if (!(nodesObject instanceof List<?> nodes)) {
            return List.of();
        }

        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> node)) {
                continue;
            }

            String currentNodeId = stringValue(node.get("nodeId"));

            if (!nodeId.equals(currentNodeId)) {
                continue;
            }

            String nodeType = safeText(stringValue(node.get("nodeType")), fallbackNodeType);
            String nodeName = safeText(stringValue(node.get("nodeName")), fallbackNodeName);
            List<DevelopmentCodebaseNodeFileResponse> files = new ArrayList<>();

            addManifestFiles(files, workspaceRoot, node, nodeId, nodeType, nodeName, "OWNED", "ownedFiles");
            addManifestFiles(files, workspaceRoot, node, nodeId, nodeType, nodeName, "CONTRIBUTED", "contributedFiles");

            return files;
        }

        return List.of();
    }

    private void addManifestFiles(
            List<DevelopmentCodebaseNodeFileResponse> files,
            Path workspaceRoot,
            Map<?, ?> node,
            String nodeId,
            String nodeType,
            String nodeName,
            String relationship,
            String key
    ) {
        Object value = node.get(key);

        if (!(value instanceof List<?> paths)) {
            return;
        }

        for (Object pathObject : paths) {
            String filePath = stringValue(pathObject);

            if (filePath == null || filePath.isBlank()) {
                continue;
            }

            Path absolutePath = resolveSafeGeneratedPath(workspaceRoot, filePath);
            boolean exists = Files.isRegularFile(absolutePath);

            files.add(new DevelopmentCodebaseNodeFileResponse(
                    nodeId,
                    nodeType,
                    nodeName,
                    relationship,
                    normalizeRootRelative(workspaceRoot, absolutePath),
                    classifyFile(filePath),
                    relationship.equals("OWNED") ? "当前节点主拥有文件" : "当前节点贡献或更新的协作文件",
                    detectLanguage(filePath),
                    exists,
                    exists ? safeSize(absolutePath) : 0
            ));
        }
    }

    private String previewRoute(
            Map<String, Object> manifest,
            String nodeId,
            DevelopmentHandoffReadinessResponse readiness
    ) {
        Object nodesObject = manifest.get("nodes");

        if (nodesObject instanceof List<?> nodes) {
            for (Object item : nodes) {
                if (item instanceof Map<?, ?> node && nodeId.equals(stringValue(node.get("nodeId")))) {
                    String route = stringValue(node.get("previewRoute"));

                    if (route != null && !route.isBlank()) {
                        return route;
                    }
                }
            }
        }

        String segment = toPathSegment(readiness.moduleKey(), readiness.moduleName());
        String nodeType = safeUpper(readiness.nodeType());

        if ("FEATURE".equals(nodeType)) {
            return "/" + segment + "?scenario=" + segment;
        }

        if ("PAGE".equals(nodeType) || "MODULE".equals(nodeType)) {
            return "/" + segment;
        }

        return "/";
    }

    private String chooseDefaultFile(
            List<DevelopmentCodebaseFileTreeItemResponse> fileTree,
            List<DevelopmentCodebaseNodeFileResponse> nodeFiles,
            String codebaseRoot
    ) {
        for (DevelopmentCodebaseNodeFileResponse file : nodeFiles) {
            if (file.exists()) {
                return file.filePath();
            }
        }

        String readme = codebaseRoot + "/README.md";

        if (containsFile(fileTree, readme)) {
            return readme;
        }

        return firstFile(fileTree);
    }

    private boolean containsFile(List<DevelopmentCodebaseFileTreeItemResponse> tree, String filePath) {
        if (tree == null) {
            return false;
        }

        for (DevelopmentCodebaseFileTreeItemResponse item : tree) {
            if (item == null) {
                continue;
            }

            if ("FILE".equals(item.itemType()) && filePath.equals(item.path())) {
                return true;
            }

            if (containsFile(item.children(), filePath)) {
                return true;
            }
        }

        return false;
    }

    private String firstFile(List<DevelopmentCodebaseFileTreeItemResponse> tree) {
        if (tree == null) {
            return null;
        }

        for (DevelopmentCodebaseFileTreeItemResponse item : tree) {
            if (item == null) {
                continue;
            }

            if ("FILE".equals(item.itemType())) {
                return item.path();
            }

            String nested = firstFile(item.children());

            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    private String classifyFile(String filePath) {
        String normalized = filePath == null ? "" : filePath.replace("\\", "/");

        if (normalized.contains("/frontend/")) {
            return "FRONTEND";
        }

        if (normalized.contains("/backend/")) {
            return "BACKEND";
        }

        if (normalized.contains("/database/")) {
            return "DATABASE";
        }

        if (normalized.contains("/tests/")) {
            return "TEST";
        }

        if (normalized.endsWith(".md")) {
            return "DOC";
        }

        return "CONFIG";
    }

    private Path resolveMaterializationWorkspaceRoot() {
        String configured = System.getProperty("codemind.development.workspace-root");

        if (configured == null || configured.isBlank()) {
            configured = System.getenv("CODEMIND_WORKSPACE_ROOT");
        }

        if (configured != null && !configured.isBlank()) {
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

    private Path resolveSafeGeneratedPath(Path workspaceRoot, String filePath) {
        return generatedPathService.resolvePlatformGeneratedPath(filePath);
    }

    private String normalizeRootRelative(Path workspaceRoot, Path path) {
        return workspaceRoot.relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace("\\", "/");
    }

    private long safeSize(Path path) {
        try {
            return Files.exists(path) && Files.isRegularFile(path) ? Files.size(path) : 0;
        } catch (IOException e) {
            return 0;
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

    private String buildCodebaseRoot(String projectCode) {
        return generatedPathService.buildPlatformCodebaseRoot(projectCode, "NODEJS_FULLSTACK");
    }

    private String detectLanguage(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".vue")) {
            return "vue";
        }
        if (lower.endsWith(".js")) {
            return "javascript";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        if (lower.endsWith(".css")) {
            return "css";
        }
        if (lower.endsWith(".html")) {
            return "html";
        }
        if (lower.endsWith(".sql")) {
            return "sql";
        }

        return "text";
    }

    private String toPathSegment(String value, String fallback) {
        String source = safeText(value, fallback)
                .trim()
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(Locale.ROOT);

        return source.isBlank() ? fallback : source;
    }

    private String safeUpper(String value) {
        return safeText(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
