/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.annotation.PreDestroy
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.boot.convert.DurationStyle
 *  org.springframework.stereotype.Component
 *  org.springframework.web.servlet.mvc.method.annotation.SseEmitter
 *  tools.jackson.databind.ObjectMapper
 */
package com.athena.tms.service;

import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;
import com.athena.tms.service.StreamConnection;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Component
public class StreamConnectionRegistry {
    private static final Logger log = LoggerFactory.getLogger(StreamConnectionRegistry.class);
    private final Set<StreamConnection> connections = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final Duration connectionTimeout;

    public StreamConnectionRegistry(ObjectMapper objectMapper, @Value(value="${tms.stream.connection-timeout:30m}") String connectionTimeout) {
        this.objectMapper = objectMapper;
        this.connectionTimeout = DurationStyle.detectAndParse((String)connectionTimeout);
    }

    public SseEmitter connectGuest(long tenantId, UUID reservationId) {
        return this.connect(StreamConnection.guest(tenantId, reservationId, this.newEmitter()));
    }

    public SseEmitter connectStaff(long tenantId, UUID staffId, String dept) {
        return this.connect(StreamConnection.staff(tenantId, staffId, dept, this.newEmitter()));
    }

    public SseEmitter connectIntegration(long tenantId, Supplier<List<IntegrationRoomStatusEvent>> replaySupplier) {
        StreamConnection connection = StreamConnection.integration(tenantId, this.newEmitter());
        SseEmitter emitter = this.register(connection);
        try {
            for (IntegrationRoomStatusEvent event : replaySupplier.get()) {
                if (connection.sendRoomStatus(event, this.objectMapper)) continue;
                this.drop(connection);
                return emitter;
            }
        }
        catch (RuntimeException e) {
            this.drop(connection);
            throw e;
        }
        return emitter;
    }

    public void broadcastRoomStatus(long tenantId, IntegrationRoomStatusEvent event) {
        for (StreamConnection connection : this.connections) {
            if (!connection.wantsIntegration(tenantId) || connection.sendRoomStatus(event, this.objectMapper)) continue;
            this.drop(connection);
        }
    }

    private SseEmitter connect(StreamConnection connection) {
        SseEmitter emitter = this.register(connection);
        if (!connection.sendInit(this.objectMapper) || !connection.sendResync(this.objectMapper)) {
            this.drop(connection);
        }
        return emitter;
    }

    private SseEmitter register(StreamConnection connection) {
        SseEmitter emitter = connection.emitter();
        emitter.onCompletion(() -> this.connections.remove(connection));
        emitter.onTimeout(() -> this.connections.remove(connection));
        emitter.onError(t -> this.connections.remove(connection));
        this.connections.add(connection);
        return emitter;
    }

    public void broadcastTask(TaskChange change) {
        for (StreamConnection connection : this.connections) {
            if (!connection.wants(change) || connection.sendTask(change, this.objectMapper)) continue;
            this.drop(connection);
        }
    }

    public void broadcastHeartbeat() {
        for (StreamConnection connection : this.connections) {
            if (connection.sendHeartbeat(this.objectMapper)) continue;
            this.drop(connection);
        }
    }

    public void broadcastDegraded() {
        log.warn("Kafka \u6d88\u8cbb\u5ef6\u9072\u8d85\u9580\u6abb\uff0c\u5411 {} \u689d SSE \u9023\u7dda\u63a8\u9001 degraded\uff08\u5ba2\u6236\u7aef\u8f49 5 \u79d2\u8f2a\u8a62\uff09", (Object)this.connections.size());
        for (StreamConnection connection : this.connections) {
            if (connection.sendDegraded(this.objectMapper)) continue;
            this.drop(connection);
        }
    }

    private void drop(StreamConnection connection) {
        this.connections.remove(connection);
        connection.completeQuietly();
    }

    private SseEmitter newEmitter() {
        return new SseEmitter(Long.valueOf(this.connectionTimeout.toMillis()));
    }

    @PreDestroy
    void closeAll() {
        for (StreamConnection connection : this.connections) {
            connection.completeQuietly();
        }
        this.connections.clear();
    }
}
