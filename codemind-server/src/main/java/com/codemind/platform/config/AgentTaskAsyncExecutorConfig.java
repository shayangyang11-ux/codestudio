package com.codemind.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AgentTaskAsyncExecutorConfig {

    @Bean(name = "requirementAgentTaskExecutor")
    public Executor requirementAgentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix("requirement-agent-task-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(contextCopyingTaskDecorator());

        executor.initialize();
        return executor;
    }

    private TaskDecorator contextCopyingTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = org.slf4j.MDC.getCopyOfContextMap();

            return () -> {
                Map<String, String> previous = org.slf4j.MDC.getCopyOfContextMap();

                try {
                    if (contextMap != null) {
                        org.slf4j.MDC.setContextMap(contextMap);
                    } else {
                        org.slf4j.MDC.clear();
                    }

                    runnable.run();
                } finally {
                    if (previous != null) {
                        org.slf4j.MDC.setContextMap(previous);
                    } else {
                        org.slf4j.MDC.clear();
                    }
                }
            };
        };
    }
}