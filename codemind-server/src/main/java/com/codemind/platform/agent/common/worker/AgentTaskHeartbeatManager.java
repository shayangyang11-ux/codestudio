package com.codemind.platform.agent.common.worker;

import com.codemind.platform.task.AgentTaskService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class AgentTaskHeartbeatManager {

    private final AgentTaskService agentTaskService;
    private final ScheduledExecutorService heartbeatExecutor;

    public AgentTaskHeartbeatManager(
            AgentTaskService agentTaskService
    ) {
        this.agentTaskService = agentTaskService;
        this.heartbeatExecutor = Executors.newScheduledThreadPool(2);
    }

    public HeartbeatLease startHeartbeat(
            UUID taskId,
            String workerId,
            Duration lockDuration,
            Duration heartbeatInterval
    ) {
        ScheduledFuture<?> future = heartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        agentTaskService.heartbeat(
                                taskId,
                                workerId,
                                lockDuration
                        );
                    } catch (Exception ignored) {
                        // 心跳失败不直接中断任务，最终由 locked_until 超时机制兜底。
                    }
                },
                heartbeatInterval.toSeconds(),
                heartbeatInterval.toSeconds(),
                TimeUnit.SECONDS
        );

        return new HeartbeatLease(future);
    }

    public static class HeartbeatLease implements AutoCloseable {

        private final ScheduledFuture<?> future;

        public HeartbeatLease(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }
}