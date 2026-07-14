package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RequirementTaskExecutionRouter {

    private final Map<TaskType, RequirementTaskExecutor> executorMap;

    public RequirementTaskExecutionRouter(
            List<RequirementTaskExecutor> executors
    ) {
        this.executorMap = new EnumMap<>(TaskType.class);

        for (RequirementTaskExecutor executor : executors) {
            this.executorMap.put(executor.supportTaskType(), executor);
        }
    }

    public void execute(AgentTaskEntity task) {
        TaskType taskType = TaskType.valueOf(task.getTaskType());

        RequirementTaskExecutor executor = executorMap.get(taskType);

        if (executor == null) {
            throw new IllegalStateException("No RequirementTaskExecutor found for taskType: " + taskType);
        }

        executor.execute(task);
    }
}