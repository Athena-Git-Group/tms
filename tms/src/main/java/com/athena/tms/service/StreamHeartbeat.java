package com.athena.tms.service;

import com.athena.tms.service.StreamConnectionRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StreamHeartbeat {
    private final StreamConnectionRegistry registry;

    public StreamHeartbeat(StreamConnectionRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelayString="${tms.stream.heartbeat-interval:15s}")
    public void beat() {
        this.registry.broadcastHeartbeat();
    }
}
