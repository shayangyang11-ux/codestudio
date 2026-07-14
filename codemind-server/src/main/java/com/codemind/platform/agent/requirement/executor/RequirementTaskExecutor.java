package com.codemind.platform.agent.requirement.executor;

import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.TaskType;

public interface RequirementTaskExecutor {

    TaskType supportTaskType();

    void execute(AgentTaskEntity task);
}