package com.codemind.platform.agent.common.controller;

import com.codemind.platform.agent.common.gateway.AgentRuntimeGatewayService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/agent-runtime")
public class AgentRuntimeController {

    private final AgentRuntimeGatewayService agentRuntimeGatewayService;

    public AgentRuntimeController(
            AgentRuntimeGatewayService agentRuntimeGatewayService
    ) {
        this.agentRuntimeGatewayService = agentRuntimeGatewayService;
    }

    @GetMapping("/llm/status")
    public Map<String, Object> getLlmRuntimeStatus() {
        return agentRuntimeGatewayService.getLlmRuntimeStatus();
    }
    @GetMapping("/model-routing/status")
    public Map<String, Object> getModelRoutingStatus() {
        return agentRuntimeGatewayService.getModelRoutingStatus();
    }

}
