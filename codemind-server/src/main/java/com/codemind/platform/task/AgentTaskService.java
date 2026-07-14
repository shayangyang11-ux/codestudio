package com.codemind.platform.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashMap;
import com.codemind.platform.prompt.PromptTemplateService;
import com.codemind.platform.prompt.PromptTemplateSnapshot;
import java.util.LinkedHashMap;

@Service
public class AgentTaskService {

    public static final String DEFAULT_REQUIREMENT_QUEUE = "requirement-agent";

    private static final List<String> ACTIVE_STATUSES = List.of(
            TaskStatus.QUEUED.name(),
            TaskStatus.RUNNING.name(),
            TaskStatus.RETRYING.name(),
            TaskStatus.BLOCKED.name()
    );

    private final AgentTaskRepository agentTaskRepository;
    private final AgentTaskErrorClassifier agentTaskErrorClassifier;
    private final PromptTemplateService promptTemplateService;

    public AgentTaskService(
            AgentTaskRepository agentTaskRepository,
            AgentTaskErrorClassifier agentTaskErrorClassifier,
            PromptTemplateService promptTemplateService
    ) {
        this.agentTaskRepository = agentTaskRepository;
        this.agentTaskErrorClassifier = agentTaskErrorClassifier;
        this.promptTemplateService = promptTemplateService;
    }

    @Transactional
    public AgentTaskEntity createQueuedTask(
            UUID projectId,
            UUID moduleId,
            TaskType taskType,
            Map<String, Object> requestPayload,
            String createdBy
    ) {
        return createQueuedTask(
                projectId,
                moduleId,
                taskType,
                requestPayload,
                createdBy,
                DEFAULT_REQUIREMENT_QUEUE,
                0,
                3
        );
    }

    @Transactional
    public AgentTaskEntity createQueuedTask(
            UUID projectId,
            UUID moduleId,
            TaskType taskType,
            Map<String, Object> requestPayload,
            String createdBy,
            String queueName,
            int priority,
            int maxAttempts
    ) {
        PromptTemplateSnapshot promptTemplateSnapshot =
                promptTemplateService.resolveCurrentSnapshotForTaskType(
                        taskType.name()
                );

        Map<String, Object> governedRequestPayload =
                attachPromptTemplateToRequestPayload(
                        requestPayload,
                        promptTemplateSnapshot
                );
        AgentTaskEntity task = new AgentTaskEntity();
        task.setProjectId(projectId);
        task.setModuleId(moduleId);
        task.setTaskType(taskType.name());
        task.setStatus(TaskStatus.QUEUED.name());
        task.setRequestPayload(governedRequestPayload);
        applyPromptTemplateSnapshot(task, promptTemplateSnapshot);
        task.setCreatedBy(createdBy);
        task.setQueueName(queueName == null || queueName.isBlank() ? DEFAULT_REQUIREMENT_QUEUE : queueName);
        task.setPriority(priority);
        task.setAttemptCount(0);
        task.setMaxAttempts(Math.max(1, maxAttempts));
        task.setNextRunAt(LocalDateTime.now());

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity createRunningTask(
            UUID projectId,
            UUID moduleId,
            TaskType taskType,
            Map<String, Object> requestPayload,
            String createdBy
    ) {
        PromptTemplateSnapshot promptTemplateSnapshot =
                promptTemplateService.resolveCurrentSnapshotForTaskType(
                        taskType.name()
                );

        Map<String, Object> governedRequestPayload =
                attachPromptTemplateToRequestPayload(
                        requestPayload,
                        promptTemplateSnapshot
                );
        AgentTaskEntity task = new AgentTaskEntity();
        task.setProjectId(projectId);
        task.setModuleId(moduleId);
        task.setTaskType(taskType.name());
        task.setStatus(TaskStatus.RUNNING.name());
        task.setRequestPayload(governedRequestPayload);
        applyPromptTemplateSnapshot(task, promptTemplateSnapshot);
        task.setStartedAt(LocalDateTime.now());
        task.setCreatedBy(createdBy);
        task.setQueueName(DEFAULT_REQUIREMENT_QUEUE);
        task.setPriority(0);
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        task.setNextRunAt(LocalDateTime.now());

        return agentTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public Optional<AgentTaskEntity> findActiveTask(
            UUID projectId,
            UUID moduleId,
            TaskType taskType
    ) {
        return agentTaskRepository.findFirstByProjectIdAndModuleIdAndTaskTypeAndStatusInAndArchivedAtIsNullOrderByCreatedAtDesc(
                projectId,
                moduleId,
                taskType.name(),
                ACTIVE_STATUSES
        );
    }

    @Transactional
    public Optional<AgentTaskEntity> acquireNextQueuedTask(
            String queueName,
            String workerId,
            Duration lockDuration
    ) {
        LocalDateTime now = LocalDateTime.now();

        Optional<AgentTaskEntity> optionalTask = agentTaskRepository.findNextQueuedTaskForUpdate(
                queueName,
                now
        );

        if (optionalTask.isEmpty()) {
            return Optional.empty();
        }

        AgentTaskEntity task = optionalTask.get();

        task.setStatus(TaskStatus.RUNNING.name());
        task.setLockedBy(workerId);
        task.setLockedUntil(now.plus(lockDuration));
        task.setHeartbeatAt(now);
        task.setErrorCode(null);
        task.setErrorMessage(null);

        return Optional.of(agentTaskRepository.save(task));
    }

    @Transactional
    public AgentTaskEntity markRunning(UUID taskId) {
        AgentTaskEntity task = getByIdForUpdate(taskId);
        LocalDateTime now = LocalDateTime.now();

        task.setStatus(TaskStatus.RUNNING.name());
        task.setStartedAt(now);
        task.setHeartbeatAt(now);
        task.setErrorCode(null);
        task.setErrorMessage(null);

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markExecutionAttemptStarted(
            UUID taskId,
            String workerId,
            Duration lockDuration
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (isTerminalStatus(task.getStatus())) {
            return task;
        }

        if (task.getLockedBy() != null && !task.getLockedBy().equals(workerId)) {
            throw new IllegalStateException("Task is locked by another worker: " + task.getLockedBy());
        }

        LocalDateTime now = LocalDateTime.now();

        task.setStatus(TaskStatus.RUNNING.name());
        task.setStartedAt(now);
        task.setHeartbeatAt(now);
        task.setLockedBy(workerId);
        task.setLockedUntil(now.plus(lockDuration));
        task.setAttemptCount((task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1);
        task.setErrorCode(null);
        task.setErrorMessage(null);

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity heartbeat(
            UUID taskId,
            String workerId,
            Duration lockDuration
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);
        LocalDateTime now = LocalDateTime.now();

        if (!TaskStatus.RUNNING.name().equals(task.getStatus())) {
            return task;
        }

        if (task.getLockedBy() != null && !task.getLockedBy().equals(workerId)) {
            throw new IllegalStateException("Task is locked by another worker: " + task.getLockedBy());
        }

        task.setHeartbeatAt(now);
        task.setLockedBy(workerId);
        task.setLockedUntil(now.plus(lockDuration));

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markSuccess(
            UUID taskId,
            Map<String, Object> responsePayload,
            UUID resultArtifactId
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (isTerminalStatus(task.getStatus())) {
            return task;
        }

        task.setStatus(TaskStatus.SUCCESS.name());
        task.setResponsePayload(responsePayload);
        task.setResultArtifactId(resultArtifactId);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setFinishedAt(LocalDateTime.now());

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markPartialSuccess(
            UUID taskId,
            Map<String, Object> responsePayload,
            UUID resultArtifactId,
            String warningCode,
            String warningMessage
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (isTerminalStatus(task.getStatus())) {
            return task;
        }

        task.setStatus(TaskStatus.PARTIAL_SUCCESS.name());
        task.setResponsePayload(responsePayload);
        task.setResultArtifactId(resultArtifactId);
        task.setErrorCode(warningCode);
        task.setErrorMessage(warningMessage);
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setFinishedAt(LocalDateTime.now());

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markFailed(
            UUID taskId,
            String errorCode,
            String errorMessage
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (isTerminalStatus(task.getStatus())) {
            return task;
        }

        AgentTaskErrorDiagnosis diagnosis = agentTaskErrorClassifier.diagnose(
                errorCode,
                errorMessage
        );

        task.setStatus(TaskStatus.FAILED.name());
        task.setErrorCode(diagnosis.errorCode());
        task.setErrorMessage(diagnosis.userMessage());
        task.setLastErrorCode(diagnosis.originalErrorCode());
        task.setLastErrorMessage(diagnosis.originalErrorMessage());
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setFinishedAt(LocalDateTime.now());

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markRetrying(
            UUID taskId,
            String errorCode,
            String errorMessage,
            Duration delay
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (isTerminalStatus(task.getStatus())) {
            return task;
        }

        LocalDateTime now = LocalDateTime.now();

        task.setStatus(TaskStatus.RETRYING.name());
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setLastErrorCode(errorCode);
        task.setLastErrorMessage(errorMessage);
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setHeartbeatAt(null);
        task.setNextRunAt(now.plus(delay));

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity releaseLockedTaskToQueue(
            UUID taskId,
            String workerId,
            Duration delay,
            String reason
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);
        LocalDateTime now = LocalDateTime.now();

        if (task.getLockedBy() != null && !task.getLockedBy().equals(workerId)) {
            throw new IllegalStateException("Task is locked by another worker: " + task.getLockedBy());
        }

        task.setStatus(TaskStatus.QUEUED.name());
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setHeartbeatAt(null);
        task.setErrorCode("AGENT_TASK_RELEASED");
        task.setErrorMessage(reason);
        task.setNextRunAt(now.plus(delay));

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markTimeout(
            UUID taskId,
            String errorMessage
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (isTerminalStatus(task.getStatus())) {
            return task;
        }

        AgentTaskErrorDiagnosis diagnosis = agentTaskErrorClassifier.diagnose(
                "AGENT_TASK_TIMEOUT",
                errorMessage
        );

        task.setStatus(TaskStatus.TIMEOUT.name());
        task.setErrorCode(diagnosis.errorCode());
        task.setErrorMessage(diagnosis.userMessage());
        task.setLastErrorCode(diagnosis.originalErrorCode());
        task.setLastErrorMessage(diagnosis.originalErrorMessage());
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setFinishedAt(LocalDateTime.now());

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markBlocked(
            UUID taskId,
            String errorMessage
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (TaskStatus.SUCCESS.name().equals(task.getStatus())
                || TaskStatus.PARTIAL_SUCCESS.name().equals(task.getStatus())
                || TaskStatus.CANCELLED.name().equals(task.getStatus())) {
            return task;
        }

        LocalDateTime now = LocalDateTime.now();

        task.setStatus(TaskStatus.BLOCKED.name());
        task.setErrorCode("AGENT_TASK_DEPENDENCY_MISSING");
        task.setErrorMessage(errorMessage);
        task.setLastErrorCode("AGENT_TASK_DEPENDENCY_MISSING");
        task.setLastErrorMessage(errorMessage);
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setHeartbeatAt(null);
        task.setFinishedAt(now);

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity markDependencyResolvedAndRequeue(
            UUID taskId,
            String reason
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (!TaskStatus.BLOCKED.name().equals(task.getStatus())) {
            return task;
        }

        LocalDateTime now = LocalDateTime.now();

        String message = reason == null || reason.isBlank()
                ? "Blocked task dependencies are now satisfied. Task requeued automatically."
                : reason;

        task.setStatus(TaskStatus.QUEUED.name());
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setLastErrorCode("AGENT_TASK_DEPENDENCY_RESOLVED");
        task.setLastErrorMessage(message);
        task.setNextRunAt(now);
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setHeartbeatAt(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity cancelTask(
            UUID taskId,
            String cancelledBy,
            String reason
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (TaskStatus.SUCCESS.name().equals(task.getStatus())
                || TaskStatus.PARTIAL_SUCCESS.name().equals(task.getStatus())) {
            throw new IllegalStateException("Completed task cannot be cancelled: " + taskId);
        }

        if (TaskStatus.CANCELLED.name().equals(task.getStatus())) {
            return task;
        }

        LocalDateTime now = LocalDateTime.now();
        String message = reason == null || reason.isBlank()
                ? "Task was cancelled by " + cancelledBy + "."
                : reason;

        task.setStatus(TaskStatus.CANCELLED.name());
        task.setErrorCode("AGENT_TASK_CANCELLED");
        task.setErrorMessage(message);
        task.setLastErrorCode("AGENT_TASK_CANCELLED");
        task.setLastErrorMessage(message);
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setHeartbeatAt(null);
        task.setFinishedAt(now);

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity retryTask(
            UUID taskId,
            String requestedBy,
            String reason,
            Integer maxAttempts
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (!isRetryableTerminalStatus(task.getStatus())) {
            throw new IllegalStateException(
                    "Only FAILED, TIMEOUT or CANCELLED tasks can be retried. taskId="
                            + taskId
                            + ", status="
                            + task.getStatus()
            );
        }

        int resolvedMaxAttempts = maxAttempts == null
                ? Math.max(1, task.getMaxAttempts() == null ? 3 : task.getMaxAttempts())
                : Math.max(1, maxAttempts);

        String message = reason == null || reason.isBlank()
                ? "Task was manually retried by " + requestedBy + "."
                : reason;

        task.setStatus(TaskStatus.QUEUED.name());
        task.setAttemptCount(0);
        task.setMaxAttempts(resolvedMaxAttempts);
        task.setNextRunAt(LocalDateTime.now());
        task.setLockedBy(null);
        task.setLockedUntil(null);
        task.setHeartbeatAt(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setLastErrorCode("AGENT_TASK_MANUAL_RETRY");
        task.setLastErrorMessage(message);
        task.setResponsePayload(null);
        task.setResultArtifactId(null);

        return agentTaskRepository.save(task);
    }

    @Transactional
    public AgentTaskEntity archiveTask(
            UUID taskId,
            String archivedBy,
            String reason
    ) {
        AgentTaskEntity task = getByIdForUpdate(taskId);

        if (task.getArchivedAt() != null) {
            return task;
        }

        if (!isArchivableStatus(task.getStatus())) {
            throw new IllegalStateException(
                    "Only terminal, cancelled or blocked tasks can be archived. taskId="
                            + taskId
                            + ", status="
                            + task.getStatus()
            );
        }

        task.setArchivedAt(LocalDateTime.now());
        task.setArchivedBy(archivedBy);
        task.setArchiveReason(
                reason == null || reason.isBlank()
                        ? "Task was archived from project task center."
                        : reason
        );

        return agentTaskRepository.save(task);
    }

    @Transactional
    public int markExpiredRunningTasksAsTimeout() {
        return agentTaskRepository.markExpiredRunningTasksAsTimeout(
                LocalDateTime.now(),
                "Task lock expired. The worker may have stopped or timed out."
        );
    }

    @Transactional(readOnly = true)
    public AgentTaskEntity getById(UUID taskId) {
        return agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Agent task not found: " + taskId));
    }

    @Transactional(readOnly = true)
    public List<AgentTaskEntity> listRecentByProject(UUID projectId) {
        return agentTaskRepository.findTop20ByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public List<AgentTaskEntity> listRecentByProjectAndModule(
            UUID projectId,
            UUID moduleId
    ) {
        return agentTaskRepository.findTop20ByProjectIdAndModuleIdAndArchivedAtIsNullOrderByCreatedAtDesc(
                projectId,
                moduleId
        );
    }

    @Transactional(readOnly = true)
    public List<AgentTaskEntity> listRecentByQueue(
            String queueName
    ) {
        return agentTaskRepository.findTop20ByQueueNameAndArchivedAtIsNullOrderByCreatedAtDesc(queueName);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countTasksByStatusInQueue(
            String queueName
    ) {
        Map<String, Long> result = new LinkedHashMap<>();

        for (TaskStatus status : TaskStatus.values()) {
            result.put(status.name(), 0L);
        }

        List<AgentTaskStatusCountProjection> rows =
                agentTaskRepository.countTasksByStatusInQueue(queueName);

        for (AgentTaskStatusCountProjection row : rows) {
            result.put(row.getStatus(), row.getTaskCount() == null ? 0L : row.getTaskCount());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public long countRunnableTasksInQueue(
            String queueName
    ) {
        return agentTaskRepository.countRunnableTasksInQueue(
                queueName,
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public long countExpiredRunningTasksInQueue(
            String queueName
    ) {
        return agentTaskRepository.countExpiredRunningTasksInQueue(
                queueName,
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<AgentTaskEntity> listBlockedTasksByQueue(
            String queueName
    ) {
        return agentTaskRepository.findTop50ByQueueNameAndStatusAndArchivedAtIsNullOrderByCreatedAtAsc(
                queueName,
                TaskStatus.BLOCKED.name()
        );
    }

    @Transactional(readOnly = true)
    public boolean canRunNowWithinModuleSerialPolicy(
            UUID projectId,
            UUID moduleId,
            UUID taskId
    ) {
        if (projectId == null || moduleId == null || taskId == null) {
            return true;
        }

        List<AgentTaskEntity> runningTasks =
                agentTaskRepository.findByProjectIdAndModuleIdAndStatusAndArchivedAtIsNullOrderByPriorityDescCreatedAtAsc(
                        projectId,
                        moduleId,
                        TaskStatus.RUNNING.name()
                );

        if (runningTasks.isEmpty()) {
            return true;
        }

        AgentTaskEntity preferredTask = runningTasks.get(0);

        return taskId.equals(preferredTask.getId());
    }

    private AgentTaskEntity getByIdForUpdate(UUID taskId) {
        return agentTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Agent task not found: " + taskId));
    }

    private boolean isTerminalStatus(String status) {
        return TaskStatus.SUCCESS.name().equals(status)
                || TaskStatus.PARTIAL_SUCCESS.name().equals(status)
                || TaskStatus.FAILED.name().equals(status)
                || TaskStatus.TIMEOUT.name().equals(status)
                || TaskStatus.CANCELLED.name().equals(status);
    }

    private boolean isRetryableTerminalStatus(String status) {
        return TaskStatus.FAILED.name().equals(status)
                || TaskStatus.TIMEOUT.name().equals(status)
                || TaskStatus.CANCELLED.name().equals(status)
                || TaskStatus.BLOCKED.name().equals(status);
    }

    private boolean isArchivableStatus(String status) {
        return TaskStatus.SUCCESS.name().equals(status)
                || TaskStatus.PARTIAL_SUCCESS.name().equals(status)
                || TaskStatus.FAILED.name().equals(status)
                || TaskStatus.TIMEOUT.name().equals(status)
                || TaskStatus.CANCELLED.name().equals(status)
                || TaskStatus.BLOCKED.name().equals(status);
    }

    private Map<String, Object> attachPromptTemplateToRequestPayload(
            Map<String, Object> requestPayload,
            PromptTemplateSnapshot promptTemplateSnapshot
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (requestPayload != null && !requestPayload.isEmpty()) {
            result.putAll(requestPayload);
        }

        result.put(
                "prompt_template",
                promptTemplateSnapshot.toPayload()
        );

        return result;
    }

    private void applyPromptTemplateSnapshot(
            AgentTaskEntity task,
            PromptTemplateSnapshot promptTemplateSnapshot
    ) {
        task.setPromptTemplateId(promptTemplateSnapshot.templateId());
        task.setPromptTemplateVersionId(promptTemplateSnapshot.templateVersionId());
        task.setPromptTemplateKey(promptTemplateSnapshot.templateKey());
        task.setPromptTemplateVersion(promptTemplateSnapshot.version());
        task.setPromptTemplateChecksum(promptTemplateSnapshot.checksum());
    }
}