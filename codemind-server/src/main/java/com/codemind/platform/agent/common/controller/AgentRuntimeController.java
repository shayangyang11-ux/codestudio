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

    // A9.10.14.4.3.15.6.24J2_PROTOTYPE_EDIT_PLANNER_V1
    @PostMapping("/prototype-edit/plan")
    public Map<String, Object> planPrototypeEdit(@RequestBody Map<String, Object> payload) {
        return agentRuntimeGatewayService.planPrototypeEdit(payload);
    }


}
