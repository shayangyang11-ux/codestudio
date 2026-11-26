package com.codemind.platform.agent.common.gateway;

import com.codemind.platform.agent.common.client.PythonAgentClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentRuntimeGatewayService {

    private final PythonAgentClient pythonAgentClient;

    public AgentRuntimeGatewayService(
            PythonAgentClient pythonAgentClient
    ) {
        this.pythonAgentClient = pythonAgentClient;
    }

    public Map<String, Object> getLlmRuntimeStatus() {
        long startedNanos = System.nanoTime();
        LocalDateTime fetchedAt = LocalDateTime.now();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "python-agent");
        result.put("endpoint", "/agent/runtime/llm/status");
        result.put("fetchedAt", fetchedAt);

        try {
            Map<String, Object> pythonStatus = pythonAgentClient.getLlmRuntimeStatus();

            result.put("available", true);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("status", pythonStatus);

            return result;

        } catch (RestClientException exception) {
            result.put("available", false);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("errorCode", "PYTHON_AGENT_RUNTIME_STATUS_UNAVAILABLE");
            result.put("errorMessage", exception.getMessage());
            result.put("exceptionType", exception.getClass().getName());

            return result;

        } catch (Exception exception) {
            result.put("available", false);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("errorCode", "AGENT_RUNTIME_STATUS_PROXY_FAILED");
            result.put("errorMessage", exception.getMessage());
            result.put("exceptionType", exception.getClass().getName());

            return result;
        }
    }


    public Map<String, Object> getModelRoutingStatus() {
        long startedNanos = System.nanoTime();
        LocalDateTime fetchedAt = LocalDateTime.now();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "python-agent");
        result.put("endpoint", "/agent/runtime/model-routing/status");
        result.put("fetchedAt", fetchedAt);

        try {
            Map<String, Object> pythonStatus = pythonAgentClient.getModelRoutingStatus();

            result.put("available", true);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("status", pythonStatus);

            return result;

        } catch (RestClientException exception) {
            result.put("available", false);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("errorCode", "PYTHON_AGENT_MODEL_ROUTING_STATUS_UNAVAILABLE");
            result.put("errorMessage", exception.getMessage());
            result.put("exceptionType", exception.getClass().getName());

            return result;

        } catch (Exception exception) {
            result.put("available", false);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("errorCode", "AGENT_MODEL_ROUTING_STATUS_PROXY_FAILED");
            result.put("errorMessage", exception.getMessage());
            result.put("exceptionType", exception.getClass().getName());

            return result;
        }
    }


    // A9.10.14.4.3.15.6.24J2_PROTOTYPE_EDIT_PLANNER_V1
    public Map<String, Object> planPrototypeEdit(Map<String, Object> payload) {
        long startedNanos = System.nanoTime();
        LocalDateTime fetchedAt = LocalDateTime.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "python-agent");
        result.put("endpoint", "/agent/runtime/prototype-edit/plan");
        result.put("fetchedAt", fetchedAt);
        try {
            Map<String, Object> pythonResult = pythonAgentClient.planPrototypeEdit(payload);
            result.put("available", true);
            result.put("durationMs", elapsedMs(startedNanos));
            result.putAll(pythonResult);
            return result;
        } catch (RestClientException exception) {
            result.put("available", false);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("errorCode", "PYTHON_AGENT_PROTOTYPE_EDIT_PLAN_UNAVAILABLE");
            result.put("errorMessage", exception.getMessage());
            result.put("exceptionType", exception.getClass().getName());
            return result;
        } catch (Exception exception) {
            result.put("available", false);
            result.put("durationMs", elapsedMs(startedNanos));
            result.put("errorCode", "PROTOTYPE_EDIT_PLAN_PROXY_FAILED");
            result.put("errorMessage", exception.getMessage());
            result.put("exceptionType", exception.getClass().getName());
            return result;
        }
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(
                0,
                (System.nanoTime() - startedNanos) / 1_000_000
        );
    }
}
