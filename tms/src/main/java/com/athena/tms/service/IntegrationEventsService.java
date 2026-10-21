package com.athena.tms.service;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.IntegrationEventsResponse;
import com.athena.tms.dto.IntegrationRoomStatusEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PMS 整合面的事件讀取：把 task_events 轉成 room_status.v1 事件。
 * cursor 拉取與 SSE 建線回放共用 {@link #replayAfter}——遲滯、白名單、
 * 租戶條件若在兩條路徑各寫一份，遲早漂移成「拉取拉得到、回放漏掉」
 * 這種最難查的缺口。
 *
 * <p>這條讀取路徑要保證「不漏事件」，靠三件事：
 * <ol>
 *   <li><b>cursor 單調</b>：cursor 就是 task_events.id（bigint 十進位字串），
 *       查詢語意固定 id &gt; :after ORDER BY id ASC，PMS 永遠帶上一頁的
 *       next_cursor 往前讀。</li>
 *   <li><b>3 秒安全遲滯</b>：只回 created_at &lt;= now() - 3 秒的事件。
 *       事件列的 id 在交易中就取號，但要到 commit 才可見；若不等這個
 *       可見窗口關閉，讀端可能先看到 id=101 再看到 101 之前才 commit 的
 *       id=100——cursor 已推進到 101，100 就永遠漏掉。</li>
 *   <li><b>租戶雙重圍欄</b>：查詢自帶 tenantId 條件（第一道牆），
 *       TenantAwareDataSource 的 RLS 是第二道牆，任一道失手另一道兜底。</li>
 * </ol>
 *
 * <p>外流種類由 {@link IntegrationRoomStatusWhitelist} 的封閉對映決定，
 * 本類只負責讀取與轉形。
 */
@Service
public class IntegrationEventsService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;

    /** 安全遲滯秒數：IDENTITY 取號先於 commit 的可見窗口上限估值。 */
    static final long SAFETY_LAG_SECONDS = 3;

    private final EntityManager entityManager;

    public IntegrationEventsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 拉取端點的完整流程：驗參數、回放、算 next_cursor。
     * after 與 limit 以原始字串進來，因為值域錯誤契約上要回 400；
     * 交給框架做型別綁定，轉換失敗會走框架自己的錯誤出口，
     * 格式與狀態碼都不受 GlobalExceptionHandler 控制。
     */
    public IntegrationEventsResponse pullEvents(String after, String limit) {
        long afterId = parseAfter(after);
        int pageSize = parseLimit(limit);
        List<IntegrationRoomStatusEvent> events = replayAfter(afterId, pageSize);
        return new IntegrationEventsResponse(events, nextCursor(events, after));
    }

    /**
     * 回放核心：afterId 之後、已過安全遲滯、命中白名單的事件，按 id
     * 升冪最多 limit 筆。租戶取自 TenantContext（由認證層從 API key 立好），
     * 呼叫前不需要也不可以另外傳租戶參數——多一個租戶來源就多一處
     * 可能與實際授權不一致的地方。
     *
     * <p>room_no 經 task join rooms 取得：task_events 只存 task_id，
     * 而 PMS 認房號不認內部 uuid。join 條件連 tenantId 一起比對，
     * 單比 uuid 雖然撞不上，但跨租戶 join 一旦成立就是靜默的資料外洩，
     * 多帶一欄換掉這個風險。
     */
    public List<IntegrationRoomStatusEvent> replayAfter(long afterId, int limit) {
        long tenantId = TenantContext.require();
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(SAFETY_LAG_SECONDS);
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT te.id, te.type, te.taskId, te.seq, te.createdAt, r.roomNo
                        FROM TaskEvent te, Task t, Room r
                        WHERE t.tenantId = te.tenantId AND t.id = te.taskId
                          AND r.tenantId = t.tenantId AND r.id = t.roomId
                          AND te.tenantId = :tenantId
                          AND te.id > :afterId
                          AND te.createdAt <= :cutoff
                          AND te.type IN :types
                        ORDER BY te.id ASC
                        """, Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("afterId", afterId)
                .setParameter("cutoff", cutoff)
                .setParameter("types", IntegrationRoomStatusWhitelist.OUTBOUND_TYPES)
                .setMaxResults(limit)
                .getResultList();
        return rows.stream().map(IntegrationEventsService::toRoomStatus).toList();
    }

    private static IntegrationRoomStatusEvent toRoomStatus(Object[] row) {
        Long id = (Long) row[0];
        String type = (String) row[1];
        return new IntegrationRoomStatusEvent(
                IntegrationRoomStatusEvent.SCHEMA_V1,
                Long.toString(id),
                (String) row[5],
                IntegrationRoomStatusWhitelist.hkStateFor(type),
                (UUID) row[2],
                (Integer) row[3],
                (OffsetDateTime) row[4]);
    }

    /**
     * next_cursor 三態：有事件取本頁最後一筆的 event_id；空頁回呼叫方的
     * after 原值；after 沒帶的空頁回 "0"。空頁回原值而不是回空，
     * PMS 的輪詢迴圈才能一律拿 next_cursor 當下一輪的 after，
     * 不必為空頁寫特例。
     */
    private static String nextCursor(List<IntegrationRoomStatusEvent> events, String after) {
        if (!events.isEmpty()) {
            return events.getLast().eventId();
        }
        return after == null ? "0" : after;
    }

    /** after 省略代表從最早讀起（等同 0）；必須是非負十進位整數，否則 400。 */
    private static long parseAfter(String after) {
        if (after == null) {
            return 0L;
        }
        try {
            long value = Long.parseLong(after);
            if (value < 0) {
                throw new IntegrationBadRequestException("after 必須是非負十進位整數");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IntegrationBadRequestException("after 必須是非負十進位整數");
        }
    }

    /** limit 省略給預設 100；值域 1 到 500，越界或非整數回 400。 */
    private static int parseLimit(String limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        int value;
        try {
            value = Integer.parseInt(limit);
        } catch (NumberFormatException e) {
            throw new IntegrationBadRequestException("limit 必須是 1 到 " + MAX_LIMIT + " 的整數");
        }
        if (value < 1 || value > MAX_LIMIT) {
            throw new IntegrationBadRequestException("limit 必須是 1 到 " + MAX_LIMIT + " 的整數");
        }
        return value;
    }
}
