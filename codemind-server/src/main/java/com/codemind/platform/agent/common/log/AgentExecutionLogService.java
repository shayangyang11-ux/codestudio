package com.codemind.platform.agent.common.log;

import com.codemind.platform.common.JsonbHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AgentExecutionLogService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentExecutionLogService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(
            UUID taskId,
            UUID projectId,
            UUID moduleId,
            String graphName,
            String nodeName,
            String eventType,
            String status,
            String modelName,
            String inputSummary,
            String outputSummary,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String errorCode,
            String errorMessage,
            Map<String, Object> metadata
    ) {
        String sql = """
                INSERT INTO agent_execution_log (
                    task_id,
                    project_id,
                    module_id,
                    graph_name,
                    node_name,
                    event_type,
                    status,
                    model_name,
                    input_summary,
                    output_summary,
                    prompt_tokens,
                    completion_tokens,
                    total_tokens,
                    cost_estimate,
                    error_code,
                    error_message,
                    metadata
                )
                VALUES (
                    :taskId,
                    :projectId,
                    :moduleId,
                    :graphName,
                    :nodeName,
                    :eventType,
                    :status,
                    :modelName,
                    :inputSummary,
                    :outputSummary,
                    :promptTokens,
                    :completionTokens,
                    :totalTokens,
                    NULL,
                    :errorCode,
                    :errorMessage,
                    :metadata
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("projectId", projectId)
                .addValue("moduleId", moduleId)
                .addValue("graphName", graphName)
                .addValue("nodeName", nodeName)
                .addValue("eventType", eventType)
                .addValue("status", status)
                .addValue("modelName", modelName)
                .addValue("inputSummary", inputSummary)
                .addValue("outputSummary", outputSummary)
                .addValue("promptTokens", promptTokens)
                .addValue("completionTokens", completionTokens)
                .addValue("totalTokens", totalTokens)
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", errorMessage)
                .addValue("metadata", JsonbHelper.toJsonb(
                        metadata == null ? Map.of() : metadata,
                        objectMapper
                ));

        jdbcTemplate.update(sql, params);
    }
}