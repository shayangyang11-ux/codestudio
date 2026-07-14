package com.codemind.platform.agent.common.worker;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

@Component
public class AgentTaskWorkerIdentity {

    private final String workerId;

    public AgentTaskWorkerIdentity() {
        this.workerId = buildWorkerId();
    }

    public String workerId() {
        return workerId;
    }

    private String buildWorkerId() {
        String host;

        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }

        return host + "-" + UUID.randomUUID();
    }
}