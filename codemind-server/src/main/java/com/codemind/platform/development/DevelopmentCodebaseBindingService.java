package com.codemind.platform.development;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DevelopmentCodebaseBindingService {

    private static final int MAX_COUNTED_FILES = 20_000;

    private final DevelopmentHandoffGateService developmentHandoffGateService;
    private final DevelopmentGeneratedPathService generatedPathService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final ObjectMapper objectMapper;

    public DevelopmentCodebaseBindingService(
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

    public DevelopmentCodebaseBindingStatusResponse getStatus(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack
    ) {
        BindingContext context = resolveContext(workspaceId, projectCode, moduleId, targetStack);
        DevelopmentCodebaseBindingValidationResponse validation = validateRelativeRoot(context.registry().relativeRootPath());
        boolean bindable = validation.blockers().isEmpty();
        List<String> blockers = new ArrayList<>(validation.blockers());
        List<String> warnings = new ArrayList<>(validation.warnings());
        String bindingStatus = bindingStatus(context.registry(), validation);

        return new DevelopmentCodebaseBindingStatusResponse(
                context.readiness().projectId(),
                context.readiness().moduleId(),
                context.readiness().moduleKey(),
                context.readiness().moduleName(),
                projectCode,
                context.targetStack(),
                bindingStatus,
                context.registry().registered(),
                bindable,
                generatedPathService.resolveGeneratedStorageRoot().toString(),
                generatedPathService.storageRootKey(),
                context.registry().relativeRootPath(),
                context.registry().platformRootPath(),
                context.registry(),
                validation,
                blockers,
                warnings,
                summary(context.registry(), validation, bindingStatus),
                LocalDateTime.now()
        );
    }

    public DevelopmentCodebaseRebindResponse rebind(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodebaseRebindRequest request
    ) {
        String requestedStack = request == null ? null : request.targetStack();
        BindingContext context = resolveContext(workspaceId, projectCode, moduleId, requestedStack);
        String requestedRoot = firstNonBlank(
                request == null ? null : request.relativeRootPath(),
                request == null ? null : request.platformRootPath(),
                context.registry().relativeRootPath(),
                generatedPathService.buildRelativeCodebaseRoot(projectCode, context.targetStack())
        );
        String relativeRoot = normalizeRequestedRoot(requestedRoot);
        DevelopmentCodebaseBindingValidationResponse validation = validateRelativeRoot(relativeRoot);
        boolean bindable = validation.blockers().isEmpty();
        List<String> blockers = new ArrayList<>(validation.blockers());
        List<String> warnings = new ArrayList<>(validation.warnings());

        if (!bindable) {
            return new DevelopmentCodebaseRebindResponse(
                    context.readiness().projectId(),
                    context.readiness().moduleId(),
                    context.readiness().moduleKey(),
                    context.readiness().moduleName(),
                    projectCode,
                    context.targetStack(),
                    false,
                    "REJECTED",
                    relativeRoot,
                    generatedPathService.toPlatformPath(relativeRoot),
                    context.registry(),
                    validation,
                    blockers,
                    warnings,
                    "代码库路径未通过绑定校验，未更新数据库登记。",
                    LocalDateTime.now()
            );
        }

        String status = validation.manifestExists() && validation.manifestReadable()
                ? "BOUND"
                : "BOUND_MANIFEST_MISSING";
        DevelopmentCodebaseRegistryResponse registry = codebaseRegistryService.rebindExistingCodebase(
                workspaceId,
                context.readiness().projectId(),
                projectCode,
                context.targetStack(),
                relativeRoot,
                status
        );

        return new DevelopmentCodebaseRebindResponse(
                context.readiness().projectId(),
                context.readiness().moduleId(),
                context.readiness().moduleKey(),
                context.readiness().moduleName(),
                projectCode,
                context.targetStack(),
                true,
                status,
                relativeRoot,
                generatedPathService.toPlatformPath(relativeRoot),
                registry,
                validation,
                blockers,
                warnings,
                validation.manifestExists()
                        ? "已重新绑定到指定生成代码库，并同步 manifest 中的模块树节点贡献索引。"
                        : "已重新绑定到指定生成代码库，但缺少 codemind.generated.json，当前只能按项目目录查看代码。",
                LocalDateTime.now()
        );
    }

    private BindingContext resolveContext(
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

        return new BindingContext(readiness, resolvedStack, registry);
    }

    private DevelopmentCodebaseBindingValidationResponse validateRelativeRoot(String relativeRootPath) {
        String relativeRoot = generatedPathService.toRelativeGeneratedPath(relativeRootPath);
        Path root = generatedPathService.resolveRelativeGeneratedPath(relativeRoot);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean directoryExists = Files.isDirectory(root);
        boolean packageJsonExists = Files.isRegularFile(root.resolve("package.json"));
        boolean manifestExists = Files.isRegularFile(root.resolve("codemind.generated.json"));
        boolean manifestReadable = false;
        boolean manifestContainsNodes = false;
        int manifestNodeCount = 0;
        int fileCount = 0;
        long totalBytes = 0L;

        if (!directoryExists) {
            blockers.add("代码库目录不存在。请确认路径位于当前 configured generated-root 下。 ");
        } else {
            CountResult countResult = countFiles(root);
            fileCount = countResult.fileCount();
            totalBytes = countResult.totalBytes();
            warnings.addAll(countResult.warnings());
        }

        if (directoryExists && !packageJsonExists) {
            blockers.add("缺少根 package.json，不能识别为可运行的 Node.js 代码库。 ");
        }

        if (directoryExists && !manifestExists) {
            warnings.add("缺少 codemind.generated.json。可以绑定，但模块树节点与代码文件的精确关联会降级。 ");
        }

        if (manifestExists) {
            try {
                Map<String, Object> manifest = objectMapper.readValue(
                        root.resolve("codemind.generated.json").toFile(),
                        new TypeReference<Map<String, Object>>() {
                        }
                );
                manifestReadable = true;
                Object nodesObject = manifest.get("nodes");

                if (nodesObject instanceof List<?> nodes) {
                    manifestNodeCount = nodes.size();
                    manifestContainsNodes = manifestNodeCount > 0;
                }

                if (!manifestContainsNodes) {
                    warnings.add("codemind.generated.json 中没有 nodes 映射，当前节点关联代码可能为空。 ");
                }
            } catch (IOException e) {
                warnings.add("codemind.generated.json 存在但解析失败：" + e.getMessage());
            }
        }

        return new DevelopmentCodebaseBindingValidationResponse(
                relativeRoot,
                generatedPathService.toPlatformPath(relativeRoot),
                root.toString(),
                directoryExists,
                packageJsonExists,
                manifestExists,
                manifestReadable,
                manifestContainsNodes,
                manifestNodeCount,
                fileCount,
                totalBytes,
                blockers,
                warnings
        );
    }

    private CountResult countFiles(Path root) {
        int fileCount = 0;
        long totalBytes = 0L;
        List<String> warnings = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnoredPath(root, path))
                    .limit(MAX_COUNTED_FILES + 1L)
                    .toList();

            if (files.size() > MAX_COUNTED_FILES) {
                warnings.add("代码库文件数量超过 " + MAX_COUNTED_FILES + "，状态统计已截断。 ");
            }

            for (Path file : files.stream().limit(MAX_COUNTED_FILES).toList()) {
                fileCount += 1;
                try {
                    totalBytes += Files.size(file);
                } catch (IOException ignored) {
                    // Ignore unreadable file size and keep validation available.
                }
            }
        } catch (IOException e) {
            warnings.add("扫描代码库文件失败：" + e.getMessage());
        }

        return new CountResult(fileCount, totalBytes, warnings);
    }

    private boolean isIgnoredPath(Path root, Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());

        for (Path part : relative) {
            String name = part.toString();

            if (
                    "node_modules".equals(name) ||
                            ".git".equals(name) ||
                            "dist".equals(name) ||
                            "build".equals(name) ||
                            "coverage".equals(name) ||
                            "logs".equals(name) ||
                            "snapshots".equals(name)
            ) {
                return true;
            }
        }

        return false;
    }

    private String bindingStatus(
            DevelopmentCodebaseRegistryResponse registry,
            DevelopmentCodebaseBindingValidationResponse validation
    ) {
        if (registry.registered() && validation.directoryExists() && validation.packageJsonExists() && validation.manifestExists()) {
            return "BOUND_READY";
        }

        if (registry.registered() && validation.directoryExists() && validation.packageJsonExists()) {
            return "BOUND_WARNING";
        }

        if (registry.registered()) {
            return "BOUND_BROKEN";
        }

        if (validation.directoryExists() && validation.packageJsonExists()) {
            return "UNREGISTERED_LOCAL_EXISTS";
        }

        return "NOT_BOUND";
    }

    private String summary(
            DevelopmentCodebaseRegistryResponse registry,
            DevelopmentCodebaseBindingValidationResponse validation,
            String bindingStatus
    ) {
        return switch (bindingStatus) {
            case "BOUND_READY" -> "当前项目代码库已登记，物理目录、package.json 和 codemind.generated.json 都可访问。";
            case "BOUND_WARNING" -> "当前项目代码库已登记并可访问，但 manifest 不完整，模块树关联代码能力会降级。";
            case "BOUND_BROKEN" -> "数据库里已有代码库登记，但物理目录或根 package.json 不可用，需要重新绑定或重新生成。";
            case "UNREGISTERED_LOCAL_EXISTS" -> "检测到本地生成代码库目录，但数据库尚未登记，可以执行重新绑定。";
            default -> registry.registered()
                    ? "当前代码库登记状态需要检查。"
                    : "当前项目尚未绑定生成代码库。生成代码后会自动登记，也可以手动绑定已有目录。";
        };
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


    private String normalizeRequestedRoot(String requestedRoot) {
        if (requestedRoot == null || requestedRoot.isBlank()) {
            return generatedPathService.buildRelativeCodebaseRoot("project", "NODEJS_FULLSTACK");
        }

        String requested = requestedRoot.trim();

        try {
            Path requestedPath = Path.of(requested);

            if (requestedPath.isAbsolute()) {
                Path storageRoot = generatedPathService.resolveGeneratedStorageRoot();
                Path absolute = requestedPath.toAbsolutePath().normalize();

                if (!absolute.startsWith(storageRoot)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "只能绑定 configured generated-root 下的代码目录。跨服务器迁移时请先调整 codemind.development.generated-root。"
                    );
                }

                return storageRoot.relativize(absolute).toString().replace('\\', '/');
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception ignored) {
            // Fall back to generated-root relative / platform path normalization.
        }

        return generatedPathService.toRelativeGeneratedPath(requested);
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

    private record BindingContext(
            DevelopmentHandoffReadinessResponse readiness,
            String targetStack,
            DevelopmentCodebaseRegistryResponse registry
    ) {
    }

    private record CountResult(
            int fileCount,
            long totalBytes,
            List<String> warnings
    ) {
    }
}
