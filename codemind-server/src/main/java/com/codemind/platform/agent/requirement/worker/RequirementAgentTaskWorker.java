package com.codemind.platform.agent.requirement.worker;

import com.codemind.platform.agent.common.worker.AgentTaskExecutionUserResolver;
import com.codemind.platform.agent.common.worker.AgentTaskHeartbeatManager;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerIdentity;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerProperties;
import com.codemind.platform.agent.common.worker.AgentTaskWorkerRuntimeMetrics;
import com.codemind.platform.agent.requirement.dependency.RequirementTaskDependencyCheckResult;
import com.codemind.platform.agent.requirement.dependency.RequirementTaskDependencyGuard;
import com.codemind.platform.agent.requirement.executor.RequirementTaskExecutionRouter;
import com.codemind.platform.identity.AgentExecutionUserContextHolder;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import com.codemind.platform.task.TaskStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RequirementAgentTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(RequirementAgentTaskWorker.class);

    private final AgentTaskService agentTaskService;
    private final RequirementTaskExecutionRouter executionRouter;
    private final AgentTaskHeartbeatManager heartbeatManager;
    private final AgentTaskWorkerIdentity workerIdentity;
    private final AgentTaskExecutionUserResolver executionUserResolver;
    private final AgentTaskWorkerProperties properties;
    private final AgentTaskWorkerRuntimeMetrics runtimeMetrics;
    private final Executor requirementAgentTaskExecutor;
    private final RequirementTaskDependencyGuard dependencyGuard;

    private final AtomicInteger continuousIdlePolls = new AtomicInteger(0);
    private final AtomicInteger skippedIdleTicks = new AtomicInteger(0);

    public RequirementAgentTaskWorker(
            AgentTaskService agentTaskService,
            RequirementTaskExecutionRouter executionRouter,
            AgentTaskHeartbeatManager heartbeatManager,
            AgentTaskWorkerIdentity workerIdentity,
            AgentTaskExecutionUserResolver executionUserResolver,
            AgentTaskWorkerProperties properties,
            AgentTaskWorkerRuntimeMetrics runtimeMetrics,
            RequirementTaskDependencyGuard dependencyGuard,
            @Qualifier("requirementAgentTaskExecutor") Executor requirementAgentTaskExecutor
    ) {
        this.agentTaskService = agentTaskService;
        this.executionRouter = executionRouter;
        this.heartbeatManager = heartbeatManager;
        this.workerIdentity = workerIdentity;
        this.executionUserResolver = executionUserResolver;
        this.properties = properties;
        this.runtimeMetrics = runtimeMetrics;
        this.requirementAgentTaskExecutor = requirementAgentTaskExecutor;
        this.dependencyGuard = dependencyGuard;
    }

    @Scheduled(fixedDelayString = "${codemind.agent.worker.poll-delay-ms:2000}")
    public void pollAndDispatch() {
        if (!properties.isEnabled()) {
            return;
        }

        if (shouldSkipThisPollBecauseIdle()) {
            runtimeMetrics.recordSkippedPoll();
            return;
        }

        runtimeMetrics.recordPoll();

        int acquiredInThisPoll = 0;

        for (int i = 0; i < properties.getMaxAcquirePerPoll(); i++) {
            Optional<AgentTaskEntity> optionalTask = agentTaskService.acquireNextQueuedTask(
                    properties.getQueueName(),
                    workerIdentity.workerId(),
                    Duration.ofSeconds(properties.getLockSeconds())
            );

            if (optionalTask.isEmpty()) {
                handleEmptyAcquire(acquiredInThisPoll);
                return;
            }

            AgentTaskEntity task = optionalTask.get();
            acquiredInThisPoll++;
            runtimeMetrics.recordAcquiredTask();
            resetIdleBackoff();

            dispatchTask(task);
        }
    }

    private void dispatchTask(
            AgentTaskEntity task
    ) {
        try {
            log.info(
                    "Requirement agent task acquired. taskId={}, taskType={}, projectId={}, moduleId={}, queueName={}, attemptCount={}, workerId={}, acquiredAt={}",
                    task.getId(),
                    task.getTaskType(),
                    task.getProjectId(),
                    task.getModuleId(),
                    task.getQueueName(),
                    task.getAttemptCount(),
                    workerIdentity.workerId(),
                    LocalDateTime.now()
            );

            requirementAgentTaskExecutor.execute(
                    () -> executeTask(task.getId())
            );

        } catch (RejectedExecutionException e) {
            runtimeMetrics.recordDispatchRejected();

            log.warn(
                    "Requirement agent executor rejected task. taskId={}, taskType={}, workerId={}, reason={}",
                    task.getId(),
                    task.getTaskType(),
                    workerIdentity.workerId(),
                    e.getMessage()
            );

            agentTaskService.releaseLockedTaskToQueue(
                    task.getId(),
                    workerIdentity.workerId(),
                    Duration.ofSeconds(15),
                    "Worker executor is busy. Task released back to queue."
            );
        }
    }

    private void executeTask(UUID taskId) {
        AgentTaskEntity task = agentTaskService.getById(taskId);

        if (TaskStatus.CANCELLED.name().equals(task.getStatus())) {
            log.info(
                    "Requirement agent task skipped because it is already cancelled. taskId={}, taskType={}, workerId={}",
                    task.getId(),
                    task.getTaskType(),
                    workerIdentity.workerId()
            );
            return;
        }

        if (shouldDeferBecauseSameModuleTaskIsRunning(task)) {
            return;
        }

        if (shouldBlockBecauseDependenciesMissing(task)) {
            return;
        }

        task = agentTaskService.markExecutionAttemptStarted(
                taskId,
                workerIdentity.workerId(),
                Duration.ofSeconds(properties.getLockSeconds())
        );

        CurrentUser executionUser = executionUserResolver.resolve(task);

        long startedNanos = System.nanoTime();

        try (
                AgentExecutionUserContextHolder.Scope userScope =
                        AgentExecutionUserContextHolder.open(executionUser);
                AgentTaskHeartbeatManager.HeartbeatLease ignored =
                        heartbeatManager.startHeartbeat(
                                taskId,
                                workerIdentity.workerId(),
                                Duration.ofSeconds(properties.getLockSeconds()),
                                Duration.ofSeconds(properties.getHeartbeatSeconds())
                        )
        ) {
            log.info(
                    "Requirement agent task started. taskId={}, taskType={}, projectId={}, moduleId={}, workerId={}, executionUser={}, attemptCount={}",
                    task.getId(),
                    task.getTaskType(),
                    task.getProjectId(),
                    task.getModuleId(),
                    workerIdentity.workerId(),
                    executionUser.username(),
                    task.getAttemptCount()
            );

            executionRouter.execute(task);

            AgentTaskEntity latestTask = agentTaskService.getById(taskId);

            if (TaskStatus.SUCCESS.name().equals(latestTask.getStatus())
                    || TaskStatus.PARTIAL_SUCCESS.name().equals(latestTask.getStatus())) {
                runtimeMetrics.recordExecutionSuccess();

                log.info(
                        "Requirement agent task finished. taskId={}, taskType={}, finalStatus={}, workerId={}, durationMs={}",
                        latestTask.getId(),
                        latestTask.getTaskType(),
                        latestTask.getStatus(),
                        workerIdentity.workerId(),
                        elapsedMillis(startedNanos)
                );
                return;
            }

            if (TaskStatus.CANCELLED.name().equals(latestTask.getStatus())) {
                log.info(
                        "Requirement agent task execution completed but final status is CANCELLED. taskId={}, taskType={}, workerId={}, durationMs={}",
                        latestTask.getId(),
                        latestTask.getTaskType(),
                        workerIdentity.workerId(),
                        elapsedMillis(startedNanos)
                );
                return;
            }

            log.warn(
                    "Requirement agent task execution returned with non-success status. taskId={}, taskType={}, finalStatus={}, workerId={}, durationMs={}",
                    latestTask.getId(),
                    latestTask.getTaskType(),
                    latestTask.getStatus(),
                    workerIdentity.workerId(),
                    elapsedMillis(startedNanos)
            );

        } catch (Exception e) {
            runtimeMetrics.recordExecutionFailure();

            log.error(
                    "Requirement agent task failed. taskId={}, taskType={}, workerId={}, durationMs={}, error={}",
                    task.getId(),
                    task.getTaskType(),
                    workerIdentity.workerId(),
                    elapsedMillis(startedNanos),
                    e.getMessage(),
                    e
            );

            handleExecutionFailure(taskId, e);
        }
    }

    private boolean shouldDeferBecauseSameModuleTaskIsRunning(
            AgentTaskEntity task
    ) {
        if (!properties.isModuleSerialExecutionEnabled()) {
            return false;
        }

        if (task.getProjectId() == null || task.getModuleId() == null) {
            return false;
        }

        boolean canRunNow = agentTaskService.canRunNowWithinModuleSerialPolicy(
                task.getProjectId(),
                task.getModuleId(),
                task.getId()
        );

        if (canRunNow) {
            return false;
        }

        runtimeMetrics.recordModuleSerialDeferred();

        Duration delay = Duration.ofSeconds(
                Math.max(1, properties.getModuleBusyRetrySeconds())
        );

        log.info(
                "Requirement agent task deferred by module serial policy. taskId={}, taskType={}, projectId={}, moduleId={}, workerId={}, retryDelaySeconds={}",
                task.getId(),
                task.getTaskType(),
                task.getProjectId(),
                task.getModuleId(),
                workerIdentity.workerId(),
                delay.toSeconds()
        );

        agentTaskService.releaseLockedTaskToQueue(
                task.getId(),
                workerIdentity.workerId(),
                delay,
                "Another task in the same module is running. Task deferred by module serial policy."
        );

        return true;
    }

    private boolean shouldBlockBecauseDependenciesMissing(
            AgentTaskEntity task
    ) {
        RequirementTaskDependencyCheckResult checkResult = dependencyGuard.check(task);

        if (checkResult.passed()) {
            return false;
        }

        log.warn(
                "Requirement agent task blocked by dependency guard. taskId={}, taskType={}, projectId={}, moduleId={}, missingArtifacts={}, workerId={}",
                task.getId(),
                task.getTaskType(),
                task.getProjectId(),
                task.getModuleId(),
                checkResult.missingArtifactTypes(),
                workerIdentity.workerId()
        );

        agentTaskService.markBlocked(
                task.getId(),
                checkResult.message()
        );

        return true;
    }

    private void handleExecutionFailure(
            UUID taskId,
            Exception e
    ) {
        AgentTaskEntity latestTask = agentTaskService.getById(taskId);

        if (TaskStatus.SUCCESS.name().equals(latestTask.getStatus())
                || TaskStatus.PARTIAL_SUCCESS.name().equals(latestTask.getStatus())
                || TaskStatus.CANCELLED.name().equals(latestTask.getStatus())) {
            return;
        }

        int attemptCount = latestTask.getAttemptCount() == null ? 0 : latestTask.getAttemptCount();
        int maxAttempts = latestTask.getMaxAttempts() == null ? 1 : latestTask.getMaxAttempts();

        if (attemptCount < maxAttempts) {
            Duration retryDelay = calculateRetryDelay(attemptCount);
            runtimeMetrics.recordRetryScheduled();

            log.warn(
                    "Requirement agent task scheduled for retry. taskId={}, taskType={}, attemptCount={}, maxAttempts={}, retryDelaySeconds={}, error={}",
                    latestTask.getId(),
                    latestTask.getTaskType(),
                    attemptCount,
                    maxAttempts,
                    retryDelay.toSeconds(),
                    e.getMessage()
            );

            agentTaskService.markRetrying(
                    taskId,
                    "AGENT_TASK_EXECUTION_FAILED",
                    e.getMessage(),
                    retryDelay
            );
            return;
        }

        if (!TaskStatus.FAILED.name().equals(latestTask.getStatus())) {
            log.error(
                    "Requirement agent task exhausted retries. taskId={}, taskType={}, attemptCount={}, maxAttempts={}, error={}",
                    latestTask.getId(),
                    latestTask.getTaskType(),
                    attemptCount,
                    maxAttempts,
                    e.getMessage()
            );

            agentTaskService.markFailed(
                    taskId,
                    "AGENT_TASK_EXECUTION_FAILED",
                    e.getMessage()
            );
        }
    }

    private void handleEmptyAcquire(
            int acquiredInThisPoll
    ) {
        if (acquiredInThisPoll == 0) {
            runtimeMetrics.recordEmptyPoll();
            int idle = continuousIdlePolls.incrementAndGet();

            if (idle == properties.getIdleBackoffThreshold()) {
                log.info(
                        "Requirement agent worker entered idle backoff. queueName={}, workerId={}, idlePolls={}",
                        properties.getQueueName(),
                        workerIdentity.workerId(),
                        idle
                );
            }
        }
    }

    private boolean shouldSkipThisPollBecauseIdle() {
        if (!properties.isIdleBackoffEnabled()) {
            return false;
        }

        int idlePolls = continuousIdlePolls.get();

        if (idlePolls < properties.getIdleBackoffThreshold()) {
            return false;
        }

        int maxSkip = Math.max(1, properties.getIdleBackoffMaxSkip());
        int skipWindow = Math.min(maxSkip, idlePolls - properties.getIdleBackoffThreshold() + 1);

        int currentSkipped = skippedIdleTicks.incrementAndGet();

        if (currentSkipped < skipWindow) {
            return true;
        }

        skippedIdleTicks.set(0);
        return false;
    }

    private void resetIdleBackoff() {
        int previousIdle = continuousIdlePolls.getAndSet(0);
        skippedIdleTicks.set(0);

        if (previousIdle >= properties.getIdleBackoffThreshold()) {
            log.info(
                    "Requirement agent worker exited idle backoff. queueName={}, workerId={}, previousIdlePolls={}",
                    properties.getQueueName(),
                    workerIdentity.workerId(),
                    previousIdle
            );
        }
    }

    private Duration calculateRetryDelay(int attemptCount) {
        long multiplier = (long) Math.pow(2, Math.max(0, attemptCount - 1));
        long delaySeconds = properties.getRetryBaseSeconds() * multiplier;
        return Duration.ofSeconds(Math.min(delaySeconds, properties.getRetryMaxSeconds()));
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}