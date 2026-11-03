/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.scheduling.annotation.Scheduled
 *  org.springframework.stereotype.Component
 */
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
