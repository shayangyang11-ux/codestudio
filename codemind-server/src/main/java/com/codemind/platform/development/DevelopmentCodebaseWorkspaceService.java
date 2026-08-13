package com.codemind.platform.development;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        DevelopmentCodeTraceGraphResponse codeTraceGraph = buildCodeTraceGraph(
                manifest,
                workspaceRoot,
                String.valueOf(moduleId),
                readiness
        );

        boolean selectedNodeHasCode = !nodeFiles.isEmpty();
        boolean selectedNodeCanGenerate = canGenerateCurrentNode(readiness);
        List<String> missingRequiredArtifacts = missingRequiredArtifacts(readiness);
        List<String> blockers = issueDescriptions(readiness.blockers());
        List<String> warnings = issueDescriptions(readiness.warnings());
        String defaultFilePath = chooseDefaultFile(fileTree, nodeFiles, codebaseRoot);
        String previewRoute = previewRoute(manifest, absoluteCodebaseRoot, String.valueOf(moduleId), readiness);
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
                codeTraceGraph,
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

        String normalizedTargetFilePath = normalizeRootRelative(workspaceRoot, targetPath);

        if (looksBinaryOrUnsafe(normalizedTargetFilePath) || isBinaryFileTreeItem(targetPath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该文件是二进制、归档或内部快照文件，不能在代码编辑器中直接预览。请通过导出或下载功能查看。"
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
        } catch (MalformedInputException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该文件不是 UTF-8 文本文件，不能在代码编辑器中直接预览。",
                    e
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
                        .filter(path -> !isIgnoredPath(path) && !isBinaryFileTreeItem(path))
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
                        .filter(child -> !isIgnoredPath(child) && !isBinaryFileTreeItem(child))
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
                            ".codemind".equals(name) ||
                            ".DS_Store".equals(name)
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean isBinaryFileTreeItem(Path path) {
        if (path == null || Files.isDirectory(path)) {
            return false;
        }

        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return looksBinaryOrUnsafe(fileName);
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
            addManifestFiles(files, workspaceRoot, node, nodeId, nodeType, nodeName, "SHARED", "sharedFiles");

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


    public DevelopmentCodebaseFileSaveResponse saveFileContent(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodebaseFileSaveRequest request
    ) {
        DevelopmentHandoffReadinessResponse readiness = developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );

        if (request == null || request.filePath() == null || request.filePath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "文件路径不能为空。"
            );
        }

        String resolvedTargetStack = resolveTargetStack(request.targetStack());
        DevelopmentCodebaseRegistryResponse codebaseRegistry = codebaseRegistryService.resolveCodebase(
                workspaceId,
                readiness.projectId(),
                projectCode,
                resolvedTargetStack
        );
        Path workspaceRoot = generatedPathService.resolveGeneratedStorageRoot();
        String codebaseRoot = codebaseRegistry.platformRootPath();
        Path absoluteCodebaseRoot = Path.of(codebaseRegistry.absoluteRootPath()).toAbsolutePath().normalize();
        String normalizedFilePath = normalizeEditableFilePath(codebaseRoot, request.filePath());
        Path targetPath = resolveSafeGeneratedPath(workspaceRoot, normalizedFilePath).toAbsolutePath().normalize();

        if (!targetPath.startsWith(absoluteCodebaseRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只能编辑当前生成项目目录内的文件。"
            );
        }

        if (isIgnoredPath(targetPath) || looksBinaryOrUnsafe(normalizedFilePath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该文件路径不允许在开发工作区中直接编辑。"
            );
        }

        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }

            String content = request.content() == null ? "" : request.content();
            Files.writeString(targetPath, content, StandardCharsets.UTF_8);

            String relationship = normalizeRelationship(request.relationship());
            updateManualCodeTraceManifest(
                    absoluteCodebaseRoot,
                    workspaceRoot,
                    codebaseRoot,
                    readiness,
                    normalizedFilePath,
                    relationship,
                    request.intentScope(),
                    request.fileRole()
            );

            return new DevelopmentCodebaseFileSaveResponse(
                    readiness.projectId(),
                    readiness.moduleId(),
                    readiness.moduleKey(),
                    readiness.moduleName(),
                    projectCode,
                    resolvedTargetStack,
                    normalizedFilePath,
                    targetPath.toString(),
                    detectLanguage(normalizedFilePath),
                    safeSize(targetPath),
                    relationship,
                    classifyFile(normalizedFilePath),
                    "SAVED",
                    List.of(String.valueOf(moduleId)),
                    List.of(),
                    "代码已保存，并已写入当前模块树节点的 Code Trace Graph 映射。",
                    LocalDateTime.now()
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "保存代码文件失败：" + normalizedFilePath,
                    e
            );
        }
    }

    private DevelopmentCodeTraceGraphResponse buildCodeTraceGraph(
            Map<String, Object> manifest,
            Path workspaceRoot,
            String selectedNodeId,
            DevelopmentHandoffReadinessResponse readiness
    ) {
        Object nodesObject = manifest.get("nodes");
        List<DevelopmentCodeTraceNodeResponse> nodeResponses = new ArrayList<>();
        List<DevelopmentCodeTraceEdgeResponse> edges = new ArrayList<>();
        Map<String, Set<String>> affectedNodeIdsByFile = new LinkedHashMap<>();
        Map<String, Set<String>> affectedNodeNamesByFile = new LinkedHashMap<>();
        Map<String, String> relationshipByFile = new LinkedHashMap<>();

        if (nodesObject instanceof List<?> nodes) {
            for (Object item : nodes) {
                if (!(item instanceof Map<?, ?> node)) {
                    continue;
                }

                String nodeId = safeText(stringValue(node.get("nodeId")), "");
                String nodeType = safeText(stringValue(node.get("nodeType")), "MODULE");
                String nodeName = safeText(stringValue(node.get("nodeName")), nodeId);
                String nodeKey = stringValue(node.get("nodeKey"));
                String previewRoute = stringValue(node.get("previewRoute"));

                int ownedCount = countList(node.get("ownedFiles"));
                int relatedCount = countList(node.get("contributedFiles"));
                int sharedCount = countList(node.get("sharedFiles"));

                nodeResponses.add(new DevelopmentCodeTraceNodeResponse(
                        nodeId,
                        nodeType,
                        nodeName,
                        nodeKey,
                        previewRoute,
                        ownedCount,
                        relatedCount,
                        sharedCount
                ));

                appendTraceEdges(edges, affectedNodeIdsByFile, affectedNodeNamesByFile, relationshipByFile, node, nodeId, nodeName, "OWNED", "ownedFiles");
                appendTraceEdges(edges, affectedNodeIdsByFile, affectedNodeNamesByFile, relationshipByFile, node, nodeId, nodeName, "CONTRIBUTED", "contributedFiles");
                appendTraceEdges(edges, affectedNodeIdsByFile, affectedNodeNamesByFile, relationshipByFile, node, nodeId, nodeName, "SHARED", "sharedFiles");
            }
        }

        List<DevelopmentCodeTraceFileResponse> fileResponses = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : affectedNodeIdsByFile.entrySet()) {
            String filePath = entry.getKey();
            Path absolutePath = resolveSafeGeneratedPath(workspaceRoot, filePath);
            boolean exists = Files.isRegularFile(absolutePath);
            Set<String> affectedNames = affectedNodeNamesByFile.getOrDefault(filePath, Set.of());

            fileResponses.add(new DevelopmentCodeTraceFileResponse(
                    filePath,
                    classifyFile(filePath),
                    detectLanguage(filePath),
                    relationshipByFile.getOrDefault(filePath, "CONTRIBUTED"),
                    exists,
                    exists ? safeSize(absolutePath) : 0,
                    List.copyOf(entry.getValue()),
                    List.copyOf(affectedNames)
            ));
        }

        int selectedFileCount = 0;

        for (DevelopmentCodeTraceEdgeResponse edge : edges) {
            if (selectedNodeId.equals(edge.nodeId())) {
                selectedFileCount++;
            }
        }

        String summary = selectedFileCount > 0
                ? "当前节点已通过 Code Trace Graph 关联 " + selectedFileCount + " 个代码文件。"
                : "当前节点暂未关联代码文件；生成代码或手动新增文件后会自动建立映射。";

        return new DevelopmentCodeTraceGraphResponse(
                safeText(stringValue(manifest.get("schemaVersion")), "2.0"),
                selectedNodeId,
                readiness.nodeType(),
                nodeResponses.size(),
                fileResponses.size(),
                edges.size(),
                selectedFileCount,
                nodeResponses,
                fileResponses,
                edges,
                summary
        );
    }

    private void appendTraceEdges(
            List<DevelopmentCodeTraceEdgeResponse> edges,
            Map<String, Set<String>> affectedNodeIdsByFile,
            Map<String, Set<String>> affectedNodeNamesByFile,
            Map<String, String> relationshipByFile,
            Map<?, ?> node,
            String nodeId,
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

            edges.add(new DevelopmentCodeTraceEdgeResponse(nodeId, filePath, relationship));
            affectedNodeIdsByFile.computeIfAbsent(filePath, ignored -> new LinkedHashSet<>()).add(nodeId);
            affectedNodeNamesByFile.computeIfAbsent(filePath, ignored -> new LinkedHashSet<>()).add(nodeName);

            String currentRelationship = relationshipByFile.get(filePath);
            if (currentRelationship == null || "OWNED".equals(relationship) || "SHARED".equals(currentRelationship)) {
                relationshipByFile.put(filePath, relationship);
            }
        }
    }

    private void updateManualCodeTraceManifest(
            Path absoluteCodebaseRoot,
            Path workspaceRoot,
            String codebaseRoot,
            DevelopmentHandoffReadinessResponse readiness,
            String filePath,
            String relationship,
            String intentScope,
            String fileRole
    ) throws IOException {
        Path manifestPath = absoluteCodebaseRoot.resolve("codemind.generated.json");
        Map<String, Object> manifest = Files.exists(manifestPath)
                ? objectMapper.readValue(
                        manifestPath.toFile(),
                        new TypeReference<Map<String, Object>>() {
                        }
                )
                : new LinkedHashMap<>();

        manifest.put("schemaVersion", "2.0");
        manifest.put("runtimeUnit", "PROJECT_CODEBASE");
        manifest.put("generationUnit", "MODULE_TREE_NODE_CONTRIBUTION");
        manifest.put("previewUnit", "PAGE_OR_FEATURE_SCENARIO");
        manifest.put("codeTraceGraphEnabled", true);
        manifest.put("root", codebaseRoot);
        manifest.put("updatedAt", LocalDateTime.now().toString());

        List<Map<String, Object>> nodes = new ArrayList<>();
        Object currentNodes = manifest.get("nodes");

        if (currentNodes instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> node) {
                    nodes.add(new LinkedHashMap<>((Map<String, Object>) node));
                }
            }
        }

        String nodeId = String.valueOf(readiness.moduleId());
        Map<String, Object> currentNode = null;

        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(String.valueOf(node.get("nodeId")))) {
                currentNode = node;
                break;
            }
        }

        if (currentNode == null) {
            currentNode = new LinkedHashMap<>();
            currentNode.put("nodeId", nodeId);
            nodes.add(currentNode);
        }

        currentNode.put("nodeType", readiness.nodeType());
        currentNode.put("nodeName", readiness.moduleName());
        currentNode.put("nodeKey", readiness.moduleKey());
        currentNode.put("moduleType", readiness.moduleType());
        currentNode.put("previewRoute", previewRoute(manifest, absoluteCodebaseRoot, nodeId, readiness));
        currentNode.put("manualTrace", true);
        currentNode.put("intentScope", safeText(intentScope, ""));
        currentNode.put("fileRole", safeText(fileRole, ""));
        currentNode.put("updatedAt", LocalDateTime.now().toString());

        String key = "OWNED".equals(relationship)
                ? "ownedFiles"
                : "SHARED".equals(relationship) ? "sharedFiles" : "contributedFiles";
        currentNode.put(key, appendUniqueString(currentNode.get(key), filePath));
        manifest.put("nodes", nodes);

        if (manifestPath.getParent() != null) {
            Files.createDirectories(manifestPath.getParent());
        }

        Files.writeString(
                manifestPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest) + System.lineSeparator(),
                StandardCharsets.UTF_8
        );
    }

    private List<String> appendUniqueString(Object source, String value) {
        List<String> result = new ArrayList<>();
        appendList(result, source);

        if (value != null && !value.isBlank() && !result.contains(value)) {
            result.add(value);
        }

        return result;
    }


    private void appendList(List<String> values, Object source) {
        if (!(source instanceof List<?> list)) {
            return;
        }

        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
    }

    private int countList(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }

        return 0;
    }

    private String normalizeEditableFilePath(String codebaseRoot, String filePath) {
        String normalized = filePath == null ? "" : filePath.trim().replace("\\", "/");

        if (normalized.matches("^[A-Za-z]:.*") || normalized.startsWith("//")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "不能使用本机绝对路径。"
            );
        }

        if (normalized.contains("..")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "文件路径不能包含 ..。"
            );
        }

        String root = safeText(codebaseRoot, "").replace("\\", "/").replaceAll("/+$", "");

        if (normalized.startsWith(root + "/")) {
            return normalized;
        }

        if (normalized.startsWith("generated/")) {
            return normalized;
        }

        return root + "/" + normalized.replaceAll("^/+", "");
    }

    private String normalizeRelationship(String value) {
        String upper = safeUpper(value);

        if ("OWNED".equals(upper) || "SHARED".equals(upper)) {
            return upper;
        }

        return "CONTRIBUTED";
    }

    private boolean looksBinaryOrUnsafe(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);

        return lower.endsWith(".png") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".gif") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".ico") ||
                lower.endsWith(".zip") ||
                lower.endsWith(".jar") ||
                lower.endsWith(".class") ||
                lower.endsWith(".exe") ||
                lower.contains("/node_modules/") ||
                lower.contains("/.git/") ||
                lower.contains("/dist/") ||
                lower.contains("/build/");
    }


    private String previewRoute(
            Map<String, Object> manifest,
            Path absoluteCodebaseRoot,
            String nodeId,
            DevelopmentHandoffReadinessResponse readiness
    ) {
        String routeFromRouter = previewRouteFromRouterFile(absoluteCodebaseRoot, readiness);

        if (routeFromRouter != null && !routeFromRouter.isBlank()) {
            return routeFromRouter;
        }

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

    private String previewRouteFromRouterFile(
            Path absoluteCodebaseRoot,
            DevelopmentHandoffReadinessResponse readiness
    ) {
        if (absoluteCodebaseRoot == null || readiness == null) {
            return null;
        }

        Path routesPath = absoluteCodebaseRoot
                .resolve("frontend")
                .resolve("src")
                .resolve("router")
                .resolve("routes.js");

        if (!Files.isRegularFile(routesPath)) {
            return null;
        }

        try {
            String text = Files.readString(routesPath, StandardCharsets.UTF_8);
            String expectedKey = normalizeRouteToken(readiness.moduleKey());
            String expectedName = normalizeRouteToken(readiness.moduleName());
            String firstNamedRoute = null;
            Matcher matcher = Pattern
                .compile("path\\s*:\\s*['\"]([^'\"]+)['\"][\\s\\S]{0,420}?name\\s*:\\s*['\"]([^'\"]+)['\"]")
                .matcher(text);

            while (matcher.find()) {
                String routePath = matcher.group(1);
                String routeName = matcher.group(2);

                if (routePath == null || routePath.isBlank() || routePath.contains(":")) {
                    continue;
                }

                if (firstNamedRoute == null) {
                    firstNamedRoute = routePath;
                }

                String routeNameToken = normalizeRouteToken(routeName);
                String routePathToken = normalizeRouteToken(routePath);

                if (matchesRouteToken(routeNameToken, expectedKey)
                        || matchesRouteToken(routePathToken, expectedKey)
                        || matchesRouteToken(routeNameToken, expectedName)
                        || matchesRouteToken(routePathToken, expectedName)) {
                    return routePath;
                }
            }

            if ("MODULE".equals(safeUpper(readiness.nodeType()))) {
                return firstNamedRoute;
            }
        } catch (IOException ignored) {
            return null;
        }

        return null;
    }

    private boolean matchesRouteToken(String candidate, String expected) {
        if (candidate == null || expected == null || candidate.isBlank() || expected.isBlank()) {
            return false;
        }

        if (expected.length() < 4) {
            return candidate.equals(expected);
        }

        return candidate.contains(expected) || expected.contains(candidate);
    }

    private String normalizeRouteToken(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("([a-z])([A-Z])", "$1$2")
                .replaceAll("[^A-Za-z0-9]+", "")
                .toLowerCase(Locale.ROOT);
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