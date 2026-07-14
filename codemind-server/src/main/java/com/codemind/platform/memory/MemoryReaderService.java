package com.codemind.platform.memory;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MemoryReaderService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MemoryReaderService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MemoryContextResult readForContextPackage(
            UUID projectId,
            UUID moduleId,
            String taskType
    ) {
        List<Map<String, Object>> projectMemory = readProjectMemory(projectId);
        List<Map<String, Object>> moduleMemory = moduleId == null
                ? List.of()
                : readModuleMemory(projectId, moduleId);
        List<Map<String, Object>> memoryChunks = readMemoryChunks(projectId, moduleId);

        List<UUID> memoryIds = new ArrayList<>();
        List<UUID> chunkIds = new ArrayList<>();

        for (Map<String, Object> item : projectMemory) {
            collectUuid(item, "id", memoryIds);
        }

        for (Map<String, Object> item : moduleMemory) {
            collectUuid(item, "id", memoryIds);
        }

        for (Map<String, Object> item : memoryChunks) {
            collectUuid(item, "id", chunkIds);
        }

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("task_type", taskType);
        memory.put("project_memory", projectMemory);
        memory.put("module_memory", moduleMemory);
        memory.put("memory_chunks", memoryChunks);

        return new MemoryContextResult(memory, memoryIds, chunkIds);
    }

    private List<Map<String, Object>> readProjectMemory(UUID projectId) {
        String sql = """
                SELECT
                    id,
                    memory_type,
                    title,
                    summary,
                    content,
                    source_artifact_id,
                    source_task_id,
                    confidence,
                    is_confirmed,
                    status,
                    version_no,
                    created_at
                FROM project_memory
                WHERE project_id = :projectId
                  AND status = 'ACTIVE'
                ORDER BY is_confirmed DESC, version_no DESC, created_at DESC
                LIMIT 20
                """;

        return jdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource("projectId", projectId)
        );
    }

    private List<Map<String, Object>> readModuleMemory(
            UUID projectId,
            UUID moduleId
    ) {
        String sql = """
                SELECT
                    id,
                    module_id,
                    responsibility,
                    summary,
                    key_rules,
                    provided_apis,
                    consumed_apis,
                    owned_entities,
                    risk_points,
                    source_artifact_id,
                    source_task_id,
                    confidence,
                    is_confirmed,
                    status,
                    version_no,
                    created_at
                FROM module_memory
                WHERE project_id = :projectId
                  AND module_id = :moduleId
                  AND status = 'ACTIVE'
                ORDER BY is_confirmed DESC, version_no DESC, created_at DESC
                LIMIT 20
                """;

        return jdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("moduleId", moduleId)
        );
    }

    private List<Map<String, Object>> readMemoryChunks(
            UUID projectId,
            UUID moduleId
    ) {
        String sql;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId);

        if (moduleId == null) {
            sql = """
                    SELECT
                        id,
                        source_type,
                        source_id,
                        source_artifact_id,
                        chunk_index,
                        chunk_summary,
                        chunk_text,
                        tags,
                        metadata,
                        status,
                        created_at
                    FROM memory_chunk
                    WHERE project_id = :projectId
                      AND status = 'ACTIVE'
                    ORDER BY created_at DESC
                    LIMIT 10
                    """;
        } else {
            sql = """
                    SELECT
                        id,
                        source_type,
                        source_id,
                        source_artifact_id,
                        chunk_index,
                        chunk_summary,
                        chunk_text,
                        tags,
                        metadata,
                        status,
                        created_at
                    FROM memory_chunk
                    WHERE project_id = :projectId
                      AND status = 'ACTIVE'
                      AND (module_id = :moduleId OR module_id IS NULL)
                    ORDER BY
                        CASE WHEN module_id = :moduleId THEN 0 ELSE 1 END,
                        created_at DESC
                    LIMIT 10
                    """;

            params.addValue("moduleId", moduleId);
        }

        return jdbcTemplate.queryForList(sql, params);
    }

    private void collectUuid(
            Map<String, Object> item,
            String key,
            List<UUID> target
    ) {
        Object value = item.get(key);
        if (value instanceof UUID uuid) {
            target.add(uuid);
        } else if (value != null) {
            target.add(UUID.fromString(String.valueOf(value)));
        }
    }

    public static class MemoryContextResult {

        private final Map<String, Object> memory;
        private final List<UUID> memoryIds;
        private final List<UUID> chunkIds;

        public MemoryContextResult(
                Map<String, Object> memory,
                List<UUID> memoryIds,
                List<UUID> chunkIds
        ) {
            this.memory = memory;
            this.memoryIds = memoryIds;
            this.chunkIds = chunkIds;
        }

        public Map<String, Object> getMemory() {
            return memory;
        }

        public List<UUID> getMemoryIds() {
            return memoryIds;
        }

        public List<UUID> getChunkIds() {
            return chunkIds;
        }
    }
}