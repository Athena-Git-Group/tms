package com.athena.tms.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;

/**
 * 連線簿：controller 進線、Kafka 消費與心跳排程出事件，三方在這裡會合。
 *
 * <p>廣播是逐條同步寫出：一個慢客戶端會拖住同一輪其他連線（MVP 規模可承受；
 * 門檻到了再換 per-connection queue）。寫失敗＝連線已死，當場移除——SSE
 * 伺服器只有寫出時才會發現客戶端走了，心跳（15 秒）因此兼任死連線清掃。
 */
@Component
public class StreamConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(StreamConnectionRegistry.class);

    private final Set<StreamConnection> connections = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final Duration connectionTimeout;

    public StreamConnectionRegistry(ObjectMapper objectMapper,
                                    @Value("${tms.stream.connection-timeout:30m}") String connectionTimeout) {
        this.objectMapper = objectMapper;
        this.connectionTimeout = DurationStyle.detectAndParse(connectionTimeout);
    }

    /** 住客連線只綁訂單：路由鍵在連線建立時定死，之後事件只比對、不再查 session。 */
    public SseEmitter connectGuest(long tenantId, UUID reservationId) {
        return connect(StreamConnection.guest(tenantId, reservationId, newEmitter()));
    }

    /** 員工連線綁本人＋部門（manager/frontdesk 無部門時只收本人任務）。 */
    public SseEmitter connectStaff(long tenantId, UUID staffId, String dept) {
        return connect(StreamConnection.staff(tenantId, staffId, dept, newEmitter()));
    }

    /**
     * 整合連線（PMS）：先註冊再回放，缺口才會變成重複而不是漏推。
     *
     * <p>回放（送出客戶端 cursor 之後的歷史事件）與即時廣播若先回放後註冊，
     * 兩步之間廣播出去的事件這條連線永遠收不到；先註冊則同一筆事件可能
     * 既被廣播又被回放到——傳遞語意是 at-least-once，客戶端以 (task_id, seq)
     * 去重，重複無害、漏推不可復原。replaySupplier 在呼叫端的請求執行緒上
     * 同步執行，租戶脈絡（TenantContext）沿用認證層立好的值。
     *
     * <p>回放查詢失敗時把連線拆掉再往外拋：讓呼叫端回錯誤給 PMS 重連，
     * 而不是留下一條「沒回放過、以為自己沒漏」的半套連線。
     */
    public SseEmitter connectIntegration(long tenantId,
                                         Supplier<List<IntegrationRoomStatusEvent>> replaySupplier) {
        StreamConnection connection = StreamConnection.integration(tenantId, newEmitter());
        SseEmitter emitter = register(connection);
        try {
            for (IntegrationRoomStatusEvent event : replaySupplier.get()) {
                if (!connection.sendRoomStatus(event, objectMapper)) {
                    drop(connection);
                    return emitter;
                }
            }
        } catch (RuntimeException e) {
            drop(connection);
            throw e;
        }
        return emitter;
    }

    /** 房況事件只進整合連線（同租戶）；guest/staff 的 task 廣播與此互不相干。 */
    public void broadcastRoomStatus(long tenantId, IntegrationRoomStatusEvent event) {
        for (StreamConnection connection : connections) {
            if (connection.wantsIntegration(tenantId)
                    && !connection.sendRoomStatus(event, objectMapper)) {
                drop(connection);
            }
        }
    }

    /** guest/staff 的進線協定：註冊後立刻送 init＋resync（整合連線不走這段，改走 cursor 回放）。 */
    private SseEmitter connect(StreamConnection connection) {
        SseEmitter emitter = register(connection);
        if (!connection.sendInit(objectMapper) || !connection.sendResync(objectMapper)) {
            drop(connection);
        }
        return emitter;
    }

    private SseEmitter register(StreamConnection connection) {
        SseEmitter emitter = connection.emitter();
        emitter.onCompletion(() -> connections.remove(connection));
        emitter.onTimeout(() -> connections.remove(connection));
        emitter.onError(t -> connections.remove(connection));
        connections.add(connection);
        return emitter;
    }

    /** 任務變化走 wants() 過濾（租戶→身分）；init/心跳/degraded 不經此路。 */
    public void broadcastTask(TaskChange change) {
        for (StreamConnection connection : connections) {
            if (connection.wants(change) && !connection.sendTask(change, objectMapper)) {
                drop(connection);
            }
        }
    }

    public void broadcastHeartbeat() {
        for (StreamConnection connection : connections) {
            if (!connection.sendHeartbeat(objectMapper)) {
                drop(connection);
            }
        }
    }

    /** 消費延遲是推送模組整體的病，不分租戶不分身分——所有連線都該知道要降級輪詢。 */
    public void broadcastDegraded() {
        log.warn("Kafka 消費延遲超門檻，向 {} 條 SSE 連線推送 degraded（客戶端轉 5 秒輪詢）",
                connections.size());
        for (StreamConnection connection : connections) {
            if (!connection.sendDegraded(objectMapper)) {
                drop(connection);
            }
        }
    }

    private void drop(StreamConnection connection) {
        connections.remove(connection);
        connection.completeQuietly();
    }

    /**
     * 有限逾時而不是無限掛著：寫出失敗只偵測得到「寫的時候已斷」的連線，
     * 半開連線（NAT 悄悄斷、對端沒 RST）的寫入會進 kernel buffer 假裝成功，
     * 永遠不觸發移除——逾時是這類殭屍連線唯一的回收點。
     */
    private SseEmitter newEmitter() {
        return new SseEmitter(connectionTimeout.toMillis());
    }

    @PreDestroy
    void closeAll() {
        for (StreamConnection connection : connections) {
            connection.completeQuietly();
        }
        connections.clear();
    }
}
