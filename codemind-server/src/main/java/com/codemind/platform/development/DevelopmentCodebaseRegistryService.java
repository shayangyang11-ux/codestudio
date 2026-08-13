package com.codemind.platform.development;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DevelopmentCodebaseRegistryService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DevelopmentGeneratedPathService generatedPathService;
    private final ObjectMapper objectMapper;

    public DevelopmentCodebaseRegistryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            DevelopmentGeneratedPathService generatedPathService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.generatedPathService = generatedPathService;
        this.objectMapper = objectMapper;
    }

    public DevelopmentCodebaseRegistryResponse resolveCodebase(
            UUID workspaceId,
            UUID projectId,
            String projectCode,
            String targetStack
    ) {
        String resolvedStack = resolveTargetStack(targetStack);
        Optional<DevelopmentCodebaseRegistryResponse> stored = findStored(workspaceId, projectId, resolvedStack);

        if (stored.isPresent()) {
            DevelopmentCodebaseRegistryResponse response = stored.get();
            Path absoluteRoot = generatedPathService.resolveRelativeGeneratedPath(response.relativeRootPath());
            boolean physicalExists = Files.isDirectory(absoluteRoot);

            return new DevelopmentCodebaseRegistryResponse(
                    response.id(),
                    response.workspaceId(),
                    response.projectId(),
                    response.projectCode(),
                    response.targetStack(),
                    response.storageType(),
                    response.storageRootKey(),
                    response.relativeRootPath(),
                    generatedPathService.toPlatformPath(response.relativeRootPath()),
                    response.manifestRelativePath(),
                    generatedPathService.toPlatformPath(response.manifestRelativePath()),
                    absoluteRoot.toString(),
                    physicalExists ? response.status() : "PATH_MISSING",
                    true,
                    physicalExists,
                    response.lastGeneratedAt(),
                    response.lastMaterializedAt(),
                    response.lastRuntimeStatus(),
                    response.lastFrontendPort(),
                    response.lastBackendPort(),
                    response.createdAt(),
                    response.updatedAt()
            );
        }

        String relativeRoot = generatedPathService.buildRelativeCodebaseRoot(projectCode, resolvedStack);
        String manifestRelativePath = generatedPathService.manifestRelativePath(relativeRoot);
        Path absoluteRoot = generatedPathService.resolveRelativeGeneratedPath(relativeRoot);
        boolean physicalExists = Files.isDirectory(absoluteRoot);

        return new DevelopmentCodebaseRegistryResponse(
                null,
                workspaceId,
                projectId,
                projectCode,
                resolvedStack,
                "LOCAL_FILESYSTEM",
                generatedPathService.storageRootKey(),
                relativeRoot,
                generatedPathService.toPlatformPath(relativeRoot),
                manifestRelativePath,
                generatedPathService.toPlatformPath(manifestRelativePath),
                absoluteRoot.toString(),
                physicalExists ? "UNREGISTERED_EXISTS" : "NOT_REGISTERED",
                false,
                physicalExists,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Transactional
    public DevelopmentCodebaseRegistryResponse registerMaterializedCodebase(
            UUID workspaceId,
            UUID projectId,
            String projectCode,
            String targetStack,
            String materializationStatus,
            String platformRootPath,
            Path absoluteRoot,
            DevelopmentCodeProjectResponse codeProject,
            List<DevelopmentCodeMaterializedFileResponse> materializedFiles
    ) {
        String resolvedStack = resolveTargetStack(targetStack);
        String relativeRoot = generatedPathService.toRelativeGeneratedPath(platformRootPath);
        String manifestRelativePath = generatedPathService.manifestRelativePath(relativeRoot);
        String status = materializationStatus == null || materializationStatus.isBlank()
                ? "MATERIALIZED"
                : materializationStatus;

        UUID codebaseId = findStored(workspaceId, projectId, resolvedStack)
                .map(DevelopmentCodebaseRegistryResponse::id)
                .orElse(UUID.randomUUID());

        jdbcTemplate.update(
                """
                INSERT INTO development_codebase (
                    id,
                    workspace_id,
                    project_id,
                    project_code,
                    target_stack,
                    storage_type,
                    storage_root_key,
                    relative_root_path,
                    manifest_relative_path,
                    status,
                    last_generated_at,
                    last_materialized_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :workspaceId,
                    :projectId,
                    :projectCode,
                    :targetStack,
                    'LOCAL_FILESYSTEM',
                    :storageRootKey,
                    :relativeRootPath,
                    :manifestRelativePath,
                    :status,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (workspace_id, project_id, target_stack)
                DO UPDATE SET
                    project_code = EXCLUDED.project_code,
                    storage_type = EXCLUDED.storage_type,
                    storage_root_key = EXCLUDED.storage_root_key,
                    relative_root_path = EXCLUDED.relative_root_path,
                    manifest_relative_path = EXCLUDED.manifest_relative_path,
                    status = EXCLUDED.status,
                    last_generated_at = CURRENT_TIMESTAMP,
                    last_materialized_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """,
                new MapSqlParameterSource()
                        .addValue("id", codebaseId)
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectId", projectId)
                        .addValue("projectCode", projectCode)
                        .addValue("targetStack", resolvedStack)
                        .addValue("storageRootKey", generatedPathService.storageRootKey())
                        .addValue("relativeRootPath", relativeRoot)
                        .addValue("manifestRelativePath", manifestRelativePath)
                        .addValue("status", status)
        );

        syncNodeContribution(codebaseId, codeProject, absoluteRoot);

        return resolveCodebase(workspaceId, projectId, projectCode, resolvedStack);
    }



    @Transactional
    public DevelopmentCodebaseRegistryResponse rebindExistingCodebase(
            UUID workspaceId,
            UUID projectId,
            String projectCode,
            String targetStack,
            String relativeRootPath,
            String status
    ) {
        String resolvedStack = resolveTargetStack(targetStack);
        String relativeRoot = generatedPathService.toRelativeGeneratedPath(relativeRootPath);
        String manifestRelativePath = generatedPathService.manifestRelativePath(relativeRoot);
        Path absoluteRoot = generatedPathService.resolveRelativeGeneratedPath(relativeRoot);
        String resolvedStatus = status == null || status.isBlank() ? "BOUND" : status.trim();

        UUID codebaseId = findStored(workspaceId, projectId, resolvedStack)
                .map(DevelopmentCodebaseRegistryResponse::id)
                .orElse(UUID.randomUUID());

        jdbcTemplate.update(
                """
                INSERT INTO development_codebase (
                    id,
                    workspace_id,
                    project_id,
                    project_code,
                    target_stack,
                    storage_type,
                    storage_root_key,
                    relative_root_path,
                    manifest_relative_path,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :workspaceId,
                    :projectId,
                    :projectCode,
                    :targetStack,
                    'LOCAL_FILESYSTEM',
                    :storageRootKey,
                    :relativeRootPath,
                    :manifestRelativePath,
                    :status,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (workspace_id, project_id, target_stack)
                DO UPDATE SET
                    project_code = EXCLUDED.project_code,
                    storage_type = EXCLUDED.storage_type,
                    storage_root_key = EXCLUDED.storage_root_key,
                    relative_root_path = EXCLUDED.relative_root_path,
                    manifest_relative_path = EXCLUDED.manifest_relative_path,
                    status = EXCLUDED.status,
                    updated_at = CURRENT_TIMESTAMP
                """,
                new MapSqlParameterSource()
                        .addValue("id", codebaseId)
                        .addValue("workspaceId", workspaceId)
                        .addValue("projectId", projectId)
                        .addValue("projectCode", projectCode)
                        .addValue("targetStack", resolvedStack)
                        .addValue("storageRootKey", generatedPathService.storageRootKey())
                        .addValue("relativeRootPath", relativeRoot)
                        .addValue("manifestRelativePath", manifestRelativePath)
                        .addValue("status", resolvedStatus)
        );

        syncManifestContributions(codebaseId, absoluteRoot);

        return resolveCodebase(workspaceId, projectId, projectCode, resolvedStack);
    }

    @Transactional
    public void updateRuntimeStatus(
            UUID workspaceId,
            UUID projectId,
            String projectCode,
            String targetStack,
            String runtimeStatus,
            Integer frontendPort,
            Integer backendPort
    ) {
        DevelopmentCodebaseRegistryResponse registry = resolveCodebase(workspaceId, projectId, projectCode, targetStack);

        if (!registry.registered() || registry.id() == null) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE development_codebase
                SET last_runtime_status = :runtimeStatus,
                    last_frontend_port = :frontendPort,
                    last_backend_port = :backendPort,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", registry.id())
                        .addValue("runtimeStatus", runtimeStatus)
                        .addValue("frontendPort", frontendPort)
                        .addValue("backendPort", backendPort)
        );
    }

    private Optional<DevelopmentCodebaseRegistryResponse> findStored(
            UUID workspaceId,
            UUID projectId,
            String targetStack
    ) {
        try {
            DevelopmentCodebaseRegistryResponse response = jdbcTemplate.queryForObject(
                    """
                    SELECT *
                    FROM development_codebase
                    WHERE workspace_id = :workspaceId
                      AND project_id = :projectId
                      AND target_stack = :targetStack
                    """,
                    new MapSqlParameterSource()
                            .addValue("workspaceId", workspaceId)
                            .addValue("projectId", projectId)
                            .addValue("targetStack", targetStack),
                    this::mapCodebase
            );

            return Optional.ofNullable(response);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private DevelopmentCodebaseRegistryResponse mapCodebase(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID workspaceId = rs.getObject("workspace_id", UUID.class);
        UUID projectId = rs.getObject("project_id", UUID.class);
        String projectCode = rs.getString("project_code");
        String targetStack = rs.getString("target_stack");
        String relativeRootPath = rs.getString("relative_root_path");
        String manifestRelativePath = rs.getString("manifest_relative_path");
        Path absoluteRoot = generatedPathService.resolveRelativeGeneratedPath(relativeRootPath);

        return new DevelopmentCodebaseRegistryResponse(
                id,
                workspaceId,
                projectId,
                projectCode,
                targetStack,
                rs.getString("storage_type"),
                rs.getString("storage_root_key"),
                relativeRootPath,
                generatedPathService.toPlatformPath(relativeRootPath),
                manifestRelativePath,
                generatedPathService.toPlatformPath(manifestRelativePath),
                absoluteRoot.toString(),
                rs.getString("status"),
                true,
                Files.isDirectory(absoluteRoot),
                timestamp(rs, "last_generated_at"),
                timestamp(rs, "last_materialized_at"),
                rs.getString("last_runtime_status"),
                nullableInt(rs, "last_frontend_port"),
                nullableInt(rs, "last_backend_port"),
                timestamp(rs, "created_at"),
                timestamp(rs, "updated_at")
        );
    }

    private void syncNodeContribution(
            UUID codebaseId,
            DevelopmentCodeProjectResponse codeProject,
            Path absoluteRoot
    ) {
        if (codebaseId == null || codeProject == null) {
            return;
        }

        Map<String, Object> node = findNodeInManifest(absoluteRoot, codeProject.moduleId());
        String previewRoute = stringValue(node.get("previewRoute"), "/");
        int fileCount = countFiles(node);

        upsertNodeContribution(
                codebaseId,
                codeProject.moduleId(),
                codeProject.nodeType(),
                codeProject.moduleName(),
                previewRoute,
                fileCount,
                codeProject.proposalChecksum()
        );
    }

    private void syncManifestContributions(UUID codebaseId, Path absoluteRoot) {
        if (codebaseId == null || absoluteRoot == null) {
            return;
        }

        Map<String, Object> manifest = readManifest(absoluteRoot);
        Object nodesObject = manifest.get("nodes");

        if (!(nodesObject instanceof List<?> nodes)) {
            return;
        }

        jdbcTemplate.update(
                """
                UPDATE development_codebase_node_contribution
                SET status = 'STALE',
                    updated_at = CURRENT_TIMESTAMP
                WHERE codebase_id = :codebaseId
                """,
                new MapSqlParameterSource().addValue("codebaseId", codebaseId)
        );

        String sourcePlanChecksum = stringValue(manifest.get("packageChecksum"), null);

        for (Object nodeObject : nodes) {
            if (!(nodeObject instanceof Map<?, ?> map)) {
                continue;
            }

            Map<String, Object> node = castMap(map);
            UUID moduleId = parseUuid(stringValue(node.get("nodeId"), null));

            if (moduleId == null) {
                continue;
            }

            upsertNodeContribution(
                    codebaseId,
                    moduleId,
                    stringValue(node.get("nodeType"), null),
                    stringValue(node.get("nodeName"), "未命名节点"),
                    stringValue(node.get("previewRoute"), "/"),
                    countFiles(node),
                    sourcePlanChecksum
            );
        }
    }

    private void upsertNodeContribution(
            UUID codebaseId,
            UUID moduleId,
            String nodeType,
            String nodeName,
            String previewRoute,
            int fileCount,
            String sourcePlanChecksum
    ) {
        if (codebaseId == null || moduleId == null) {
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO development_codebase_node_contribution (
                    id,
                    codebase_id,
                    module_id,
                    node_type,
                    node_name,
                    preview_route,
                    file_count,
                    source_plan_checksum,
                    status,
                    last_generated_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :codebaseId,
                    :moduleId,
                    :nodeType,
                    :nodeName,
                    :previewRoute,
                    :fileCount,
                    :sourcePlanChecksum,
                    'ACTIVE',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (codebase_id, module_id)
                DO UPDATE SET
                    node_type = EXCLUDED.node_type,
                    node_name = EXCLUDED.node_name,
                    preview_route = EXCLUDED.preview_route,
                    file_count = EXCLUDED.file_count,
                    source_plan_checksum = EXCLUDED.source_plan_checksum,
                    status = 'ACTIVE',
                    last_generated_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("codebaseId", codebaseId)
                        .addValue("moduleId", moduleId)
                        .addValue("nodeType", nodeType)
                        .addValue("nodeName", nodeName)
                        .addValue("previewRoute", previewRoute)
                        .addValue("fileCount", fileCount)
                        .addValue("sourcePlanChecksum", sourcePlanChecksum)
        );
    }

    private Map<String, Object> readManifest(Path absoluteRoot) {
        Path manifest = absoluteRoot.resolve("codemind.generated.json");

        if (!Files.isRegularFile(manifest)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    manifest.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> findNodeInManifest(Path absoluteRoot, UUID moduleId) {
        Path manifest = absoluteRoot.resolve("codemind.generated.json");

        if (!Files.isRegularFile(manifest)) {
            return Map.of();
        }

        Map<String, Object> content = readManifest(absoluteRoot);
        Object nodesObject = content.get("nodes");

        if (!(nodesObject instanceof List<?> nodes)) {
            return Map.of();
        }

        String targetNodeId = String.valueOf(moduleId);

        for (Object nodeObject : nodes) {
            if (!(nodeObject instanceof Map<?, ?> map)) {
                continue;
            }

            Map<String, Object> node = castMap(map);
            if (targetNodeId.equals(String.valueOf(node.get("nodeId")))) {
                return node;
            }
        }

        return Map.of();
    }

    private int countFiles(Map<String, Object> node) {
        if (node == null || node.isEmpty()) {
            return 0;
        }

        return listSize(node.get("ownedFiles")) + listSize(node.get("contributedFiles"));
    }

    private int listSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }

        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    private String resolveTargetStack(String targetStack) {
        try {
            return DevelopmentTargetStack.resolve(targetStack).stackCode();
        } catch (IllegalArgumentException e) {
            return "NODEJS_FULLSTACK";
        }
    }

    private String stringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }

        return String.valueOf(value);
    }


    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
