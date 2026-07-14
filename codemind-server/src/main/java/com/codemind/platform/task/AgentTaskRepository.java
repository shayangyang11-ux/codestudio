package com.codemind.platform.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, UUID> {

    List<AgentTaskEntity> findTop20ByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(UUID projectId);

    List<AgentTaskEntity> findTop20ByProjectIdAndModuleIdAndArchivedAtIsNullOrderByCreatedAtDesc(
            UUID projectId,
            UUID moduleId
    );

    Optional<AgentTaskEntity> findFirstByProjectIdAndModuleIdAndTaskTypeAndStatusInAndArchivedAtIsNullOrderByCreatedAtDesc(
            UUID projectId,
            UUID moduleId,
            String taskType,
            List<String> statuses
    );

    @Query(
            value = """
                SELECT *
                FROM agent_task
                WHERE queue_name = :queueName
                  AND status IN ('QUEUED', 'RETRYING')
                  AND archived_at IS NULL
                  AND COALESCE(next_run_at, created_at) <= :now
                ORDER BY priority DESC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """,
            nativeQuery = true
    )
    Optional<AgentTaskEntity> findNextQueuedTaskForUpdate(
            @Param("queueName") String queueName,
            @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AgentTaskEntity t where t.id = :taskId")
    Optional<AgentTaskEntity> findByIdForUpdate(
            @Param("taskId") UUID taskId
    );

    @Modifying
    @Query("""
            update AgentTaskEntity t
            set t.status = 'TIMEOUT',
                t.errorCode = 'AGENT_TASK_TIMEOUT',
                t.errorMessage = :errorMessage,
                t.finishedAt = :now,
                t.lockedBy = null,
                t.lockedUntil = null
            where t.status = 'RUNNING'
              and t.archivedAt is null
              and t.lockedUntil is not null
              and t.lockedUntil < :now
            """)
    int markExpiredRunningTasksAsTimeout(
            @Param("now") LocalDateTime now,
            @Param("errorMessage") String errorMessage
    );

    List<AgentTaskEntity> findTop20ByQueueNameAndArchivedAtIsNullOrderByCreatedAtDesc(String queueName);

    @Query("""
        select
            t.status as status,
            count(t.id) as taskCount
        from AgentTaskEntity t
        where t.queueName = :queueName
          and t.archivedAt is null
        group by t.status
        """)
    List<AgentTaskStatusCountProjection> countTasksByStatusInQueue(
            @Param("queueName") String queueName
    );

    @Query("""
        select count(t.id)
        from AgentTaskEntity t
        where t.queueName = :queueName
          and t.archivedAt is null
          and t.status in ('QUEUED', 'RETRYING')
          and (
                t.nextRunAt is null
                or t.nextRunAt <= :now
          )
        """)
    long countRunnableTasksInQueue(
            @Param("queueName") String queueName,
            @Param("now") LocalDateTime now
    );

    @Query("""
        select count(t.id)
        from AgentTaskEntity t
        where t.queueName = :queueName
          and t.archivedAt is null
          and t.status = 'RUNNING'
          and t.lockedUntil is not null
          and t.lockedUntil < :now
        """)
    long countExpiredRunningTasksInQueue(
            @Param("queueName") String queueName,
            @Param("now") LocalDateTime now
    );

    List<AgentTaskEntity> findByProjectIdAndModuleIdAndStatusAndArchivedAtIsNullOrderByPriorityDescCreatedAtAsc(
            UUID projectId,
            UUID moduleId,
            String status
    );

    List<AgentTaskEntity> findTop50ByQueueNameAndStatusAndArchivedAtIsNullOrderByCreatedAtAsc(
            String queueName,
            String status
    );
}