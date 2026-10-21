package com.athena.tms.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;
import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;

/**
 * 一條 SSE 連線＝一個觀眾（住客 session、員工或 PMS 整合客戶端）＋一條連線層流水號。
 *
 * <p>stream_pos 是「這條連線」的計數器，不是全域事件編號：斷線
 * 重連從 0 重來，客戶端只拿它偵測這條連線上有沒有漏——跳號就重拉快照。
 * 伺服器不做斷線重放緩衝：SSE 容許掉事件，真相在資料庫。
 *
 * <p>所有 send 以 this 為鎖：SseEmitter 的底層寫出不保證執行緒安全，而
 * 心跳（排程執行緒）與任務事件（Kafka 消費執行緒）會同時打同一條連線；
 * 鎖也保證 stream_pos 取號與寫出是同一原子段，不會兩事件搶號後倒序送達。
 *
 * <p><b>對外契約（客戶端要照著做的事）</b>：連線一開會先收到 `init`
 * （只帶 stream_pos 基準 0），緊接著收到 `resync`（帶 reason 跟 refetch——
 * 一個相對路徑，告訴客戶端這次該打哪個端點重拉快照：住客是
 * `/guest/home`、員工是 `/staff/pool`）。客戶端收到 resync 就該去打那個
 * 端點把畫面蓋成最新狀態，再開始相信接下來進來的增量事件（`task`／
 * `heartbeat`／`degraded`）；跳過這一步、以為一連上就能接著舊狀態繼續疊
 * 增量，是誤用這個協定。之後若收到 `task` 的 stream_pos 跳號，代表這條
 * 連線中途漏了事件，客戶端要自己重拉快照（伺服器不提供重放）。
 *
 * <p><b>整合連線（PMS）走另一套事件協定</b>：不收 init／resync／task，
 * 只收 `room_status`（`id:` 帶資料庫事件 id，供斷線重連當 cursor）、
 * `heartbeat` 與 `degraded`。stream_pos 的跳號偵測對整合連線沒有意義——
 * 可靠性由「重連帶 cursor 回放」保證，不靠連線層計數器。
 */
final class StreamConnection {

    enum Audience { GUEST, STAFF, INTEGRATION }

    private final Audience audience;
    private final long tenantId;
    /** 住客路由鍵：本訂單。住客一律以訂單過濾、不按房號。 */
    private final UUID reservationId;
    /** 員工路由鍵之一：本人受派任務。 */
    private final UUID staffId;
    /** 員工路由鍵之二：本部門待接池；manager／frontdesk 無部門為 null。 */
    private final String dept;
    private final SseEmitter emitter;

    private long streamPos = 0;
    private volatile boolean closed = false;

    private StreamConnection(Audience audience, long tenantId, UUID reservationId,
                             UUID staffId, String dept, SseEmitter emitter) {
        this.audience = audience;
        this.tenantId = tenantId;
        this.reservationId = reservationId;
        this.staffId = staffId;
        this.dept = dept;
        this.emitter = emitter;
    }

    static StreamConnection guest(long tenantId, UUID reservationId, SseEmitter emitter) {
        return new StreamConnection(Audience.GUEST, tenantId, reservationId, null, null, emitter);
    }

    static StreamConnection staff(long tenantId, UUID staffId, String dept, SseEmitter emitter) {
        return new StreamConnection(Audience.STAFF, tenantId, null, staffId, dept, emitter);
    }

    /** 整合連線只綁租戶：PMS 收的是全店房況，沒有訂單或個人層級的路由鍵。 */
    static StreamConnection integration(long tenantId, SseEmitter emitter) {
        return new StreamConnection(Audience.INTEGRATION, tenantId, null, null, null, emitter);
    }

    SseEmitter emitter() {
        return emitter;
    }

    /**
     * 這條連線要不要收這筆變化。租戶先擋（topic 全租戶共用，消費端
     * 過租戶授權）；住客比訂單、員工比「部門相符或本人是受派人」。員工側用
     * 粗粒度的部門相符而不是「只有 pool 態才算待接池變化」：任務離開待接池
     * （被同事認走）也是待接池的變化，客戶端收鈴後重拉清單自然對齊。
     */
    boolean wants(TaskChange change) {
        if (change.tenantId() != tenantId) {
            return false;
        }
        return switch (audience) {
            case GUEST -> reservationId != null && reservationId.equals(change.reservationId());
            case STAFF -> (dept != null && dept.equals(change.dept()))
                    || (staffId != null && staffId.equals(change.assigneeId()));
            // 整合連線不走 task 事件協定（它收的是 room_status），一律不要
            case INTEGRATION -> false;
        };
    }

    /** 整合廣播的路由：只看「是整合連線」且「同租戶」，事件種類已在上游以白名單篩過。 */
    boolean wantsIntegration(long tenantId) {
        return audience == Audience.INTEGRATION && this.tenantId == tenantId;
    }

    /** 連線建立即送：讓回應立刻 commit（客戶端不等第一筆資料才知道通了），並給 stream_pos 基準 0。 */
    synchronized boolean sendInit(ObjectMapper mapper) {
        ObjectNode data = mapper.createObjectNode();
        data.put("stream_pos", streamPos);
        return write("init", data.toString());
    }

    /**
     * 緊接著 init 送的第二筆：告訴客戶端「先重拉一次完整快照，再相信接下來的
     * 增量事件」。這條連線分不出自己是「第一次連」還是「剛好跨過一次部署
     * 重啟窗口」——重啟期間，新 instance 的 Kafka consumer group 是全新的、
     * 只從現在開始讀（見 TaskEventsSseConsumer 類別 Javadoc），重啟那段時間
     * 實際發生過的事件，這個 instance 永遠不會知道、也就永遠推不出去，若不
     * 主動提醒，客戶端會以為自己手上的狀態是連續、完整的。
     *
     * <p>refetch 給的是住客／員工各自查「目前完整狀態」的端點：光說「請重拉」
     * 不夠，客戶端還得自己猜要打哪一個——這裡直接把答案寫進事件的 payload，
     * 協定才算講完整。
     */
    synchronized boolean sendResync(ObjectMapper mapper) {
        ObjectNode data = mapper.createObjectNode();
        data.put("reason", "connected");
        data.put("refetch", audience == Audience.GUEST ? "/guest/home" : "/staff/pool");
        return write("resync", data.toString());
    }

    /** 任務變化：唯一會推進 stream_pos 的事件種類——跳號偵測只對任務事件有意義。 */
    synchronized boolean sendTask(TaskChange change, ObjectMapper mapper) {
        if (closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("stream_pos", ++streamPos);
        data.put("task_id", change.taskId().toString());
        data.put("seq", change.seq());
        data.put("type", change.type());
        if (change.occurredAt() != null) {
            data.put("occurred_at", change.occurredAt());
        }
        return write("task", data.toString());
    }

    /**
     * 房況事件（只送整合連線）：`id:` 帶資料庫事件 id，PMS 斷線重連時
     * 以它當 cursor 回放；payload 是對外契約 room_status.v1 的 JSON。
     */
    synchronized boolean sendRoomStatus(IntegrationRoomStatusEvent event, ObjectMapper mapper) {
        if (closed) {
            return false;
        }
        return write("room_status", event.eventId(), mapper.writeValueAsString(event));
    }

    /**
     * 心跳帶當前 stream_pos 不佔號：安靜期客戶端也能核對自己有沒有漏。
     * 整合連線沒有 stream_pos 的概念（見類別註解），心跳只留保活用的空物件。
     */
    synchronized boolean sendHeartbeat(ObjectMapper mapper) {
        if (closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        if (audience != Audience.INTEGRATION) {
            data.put("stream_pos", streamPos);
        }
        return write("heartbeat", data.toString());
    }

    synchronized boolean sendDegraded(ObjectMapper mapper) {
        if (closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("reason", "kafka_consumer_lag");
        return write("degraded", data.toString());
    }

    private boolean write(String event, String json) {
        return write(event, null, json);
    }

    /** @return false＝連線已死，呼叫端（registry）負責移除；這裡不自行解註冊，避免遍歷中改集合。 */
    private boolean write(String event, String id, String json) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event);
            if (id != null) {
                builder.id(id);
            }
            emitter.send(builder.data(json));
            return true;
        } catch (Exception e) {
            // 斷線的正常樣貌是 send 時 IOException——SSE 伺服器只有在寫出時才會發現客戶端走了
            closed = true;
            return false;
        }
    }

    void completeQuietly() {
        closed = true;
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 連線已經斷了才 complete 會丟例外，無事可做
        }
    }
}
