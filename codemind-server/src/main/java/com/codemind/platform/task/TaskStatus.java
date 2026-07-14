package com.codemind.platform.task;

public enum TaskStatus {

    PENDING,

    QUEUED,

    RUNNING,

    SUCCESS,

    FAILED,

    TIMEOUT,

    RETRYING,

    CANCELLED,

    CONFLICT,

    BLOCKED,

    PARTIAL_SUCCESS
}