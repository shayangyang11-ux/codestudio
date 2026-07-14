package com.codemind.platform.agent.requirement.worker;

import com.codemind.platform.task.AgentTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentTaskTimeoutSweeper {

    private final AgentTaskService agentTaskService;

    @Value("${codemind.agent.worker.timeout-sweeper.enabled:true}")
    private boolean enabled;

    public AgentTaskTimeoutSweeper(
            AgentTaskService agentTaskService
    ) {
        this.agentTaskService = agentTaskService;
    }

    @Scheduled(fixedDelayString = "${codemind.agent.worker.timeout-sweeper-delay-ms:60000}")
    public void sweepExpiredRunningTasks() {
        if (!enabled) {
            return;
        }

        agentTaskService.markExpiredRunningTasksAsTimeout();
    }
}