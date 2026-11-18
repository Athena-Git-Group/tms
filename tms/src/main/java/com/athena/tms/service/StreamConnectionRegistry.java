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
    private final StreamDegradation degradation = new StreamDegradation();

    public StreamConnectionRegistry(ObjectMapper objectMapper, @Value(value="${tms.stream.connection-timeout:30m}") String connectionTimeout) {
        this.objectMapper = objectMapper;
        this.connectionTimeout = DurationStyle.detectAndParse((String)connectionTimeout);
    }

    public SseEmitter connectGuest(long tenantId, UUID reservationId) {
        return this.connect(StreamConnection.guest(tenantId, reservationId, this.newEmitter()));
    }

    public SseEmitter connectStaff(long tenantId, UUID staffId, String role, String dept) {
        return this.connect(StreamConnection.staff(tenantId, staffId, role, dept, this.newEmitter()));
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
        return this.degradation.whileLocked(() -> {
            SseEmitter emitter = this.register(connection);
            if (!connection.sendInit(this.objectMapper) || !connection.sendResync(this.objectMapper)) {
                this.drop(connection);
                return emitter;
            }
            // 降級期間才連上的員工錯過了進入降級時的那則廣播，在重抓通知之後補送一則。
            StreamDegradation.Reason reason = this.degradation.current();
            if (reason != null && connection.isStaff() && !connection.sendDegraded(reason.code(), this.objectMapper)) {
                this.drop(connection);
            }
            return emitter;
        });
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
        boolean degraded = this.degradation.isDegraded();
        for (StreamConnection connection : this.connections) {
            if (connection.sendHeartbeat(this.objectMapper, degraded)) continue;
            this.drop(connection);
        }
    }

    /**
     * 進入降級，並對連線宣告接下來的訊號可能延遲。已經在降級時什麼都不做，同一次降級只宣告一次，原因也維持第一次的。
     *
     * <p>處理事件落後送給所有連線；連不上事件來源只送員工連線，住客與對接系統的連線不送。
     */
    public void enterDegraded(StreamDegradation.Reason reason) {
        this.degradation.enter(reason, this::announceDegraded);
    }

    private void announceDegraded(StreamDegradation.Reason reason) {
        log.warn("員工事件串流進入降級（原因 {}），向 {} 條 SSE 連線推送 degraded（客戶端轉定時重抓）", (Object)reason.code(), (Object)this.connections.size());
        boolean everyone = reason == StreamDegradation.Reason.CONSUMER_LAG;
        for (StreamConnection connection : this.connections) {
            if (!everyone && !connection.isStaff()) continue;
            if (connection.sendDegraded(reason.code(), this.objectMapper)) continue;
            this.drop(connection);
        }
    }

    /** 不論原因，回到正常並對員工連線送 recovered。不在降級時什麼都不做。 */
    public void leaveDegraded() {
        this.degradation.leave(this::announceRecovered);
    }

    /**
     * 只有目前的降級原因是 {@code reason} 時才回到正常。事件消費者重新啟動只代表事件來源接回來了，
     * 不代表落後已經追上，所以不能讓它把處理事件落後的降級一併解除。
     */
    public void leaveDegradedIf(StreamDegradation.Reason reason) {
        this.degradation.leaveIf(reason, this::announceRecovered);
    }

    private void announceRecovered(StreamDegradation.Reason was) {
        log.info("員工事件串流離開降級（原因原為 {}），向員工連線推送 recovered", (Object)was.code());
        for (StreamConnection connection : this.connections) {
            if (!connection.isStaff() || connection.sendRecovered(this.objectMapper)) continue;
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
