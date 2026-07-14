package com.codemind.platform.agent.common.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "codemind.agent.worker")
public class AgentTaskWorkerProperties {

    private boolean enabled = true;

    private String queueName = "requirement-agent";

    private int maxAcquirePerPoll = 2;

    private long lockSeconds = 900;

    private long heartbeatSeconds = 30;

    private long retryBaseSeconds = 30;

    private long retryMaxSeconds = 900;

    private boolean idleBackoffEnabled = true;

    private int idleBackoffThreshold = 5;

    private int idleBackoffMaxSkip = 15;

    private boolean moduleSerialExecutionEnabled = true;

    private long moduleBusyRetrySeconds = 20;

    private boolean dependencyAutoUnblockEnabled = true;

    private int dependencyBlockedScanBatchSize = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public int getMaxAcquirePerPoll() {
        return maxAcquirePerPoll;
    }

    public void setMaxAcquirePerPoll(int maxAcquirePerPoll) {
        this.maxAcquirePerPoll = maxAcquirePerPoll;
    }

    public long getLockSeconds() {
        return lockSeconds;
    }

    public void setLockSeconds(long lockSeconds) {
        this.lockSeconds = lockSeconds;
    }

    public long getHeartbeatSeconds() {
        return heartbeatSeconds;
    }

    public void setHeartbeatSeconds(long heartbeatSeconds) {
        this.heartbeatSeconds = heartbeatSeconds;
    }

    public long getRetryBaseSeconds() {
        return retryBaseSeconds;
    }

    public void setRetryBaseSeconds(long retryBaseSeconds) {
        this.retryBaseSeconds = retryBaseSeconds;
    }

    public long getRetryMaxSeconds() {
        return retryMaxSeconds;
    }

    public void setRetryMaxSeconds(long retryMaxSeconds) {
        this.retryMaxSeconds = retryMaxSeconds;
    }

    public boolean isIdleBackoffEnabled() {
        return idleBackoffEnabled;
    }

    public void setIdleBackoffEnabled(boolean idleBackoffEnabled) {
        this.idleBackoffEnabled = idleBackoffEnabled;
    }

    public int getIdleBackoffThreshold() {
        return idleBackoffThreshold;
    }

    public void setIdleBackoffThreshold(int idleBackoffThreshold) {
        this.idleBackoffThreshold = idleBackoffThreshold;
    }

    public int getIdleBackoffMaxSkip() {
        return idleBackoffMaxSkip;
    }

    public void setIdleBackoffMaxSkip(int idleBackoffMaxSkip) {
        this.idleBackoffMaxSkip = idleBackoffMaxSkip;
    }

    public boolean isModuleSerialExecutionEnabled() {
        return moduleSerialExecutionEnabled;
    }

    public void setModuleSerialExecutionEnabled(boolean moduleSerialExecutionEnabled) {
        this.moduleSerialExecutionEnabled = moduleSerialExecutionEnabled;
    }

    public long getModuleBusyRetrySeconds() {
        return moduleBusyRetrySeconds;
    }

    public void setModuleBusyRetrySeconds(long moduleBusyRetrySeconds) {
        this.moduleBusyRetrySeconds = moduleBusyRetrySeconds;
    }

    public boolean isDependencyAutoUnblockEnabled() {
        return dependencyAutoUnblockEnabled;
    }

    public void setDependencyAutoUnblockEnabled(boolean dependencyAutoUnblockEnabled) {
        this.dependencyAutoUnblockEnabled = dependencyAutoUnblockEnabled;
    }

    public int getDependencyBlockedScanBatchSize() {
        return dependencyBlockedScanBatchSize;
    }

    public void setDependencyBlockedScanBatchSize(int dependencyBlockedScanBatchSize) {
        this.dependencyBlockedScanBatchSize = dependencyBlockedScanBatchSize;
    }
}