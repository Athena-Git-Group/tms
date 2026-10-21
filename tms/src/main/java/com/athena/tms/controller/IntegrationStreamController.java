package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.service.IntegrationBadRequestException;
import com.athena.tms.service.IntegrationEventsService;
import com.athena.tms.service.StreamConnectionRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

/**
 * `GET /integration/pms/stream`：PMS 的房況 SSE 整合流。建線先回放
 * cursor 之後的歷史事件，再進入即時推送，斷線期間的事件因此補得回來。
 *
 * <p>cursor 來源有兩個：query param `cursor` 與 SSE 標準的 `Last-Event-ID`
 * header（瀏覽器型客戶端重連時自動帶上次收到的 `id:`）。兩者同時出現時
 * query param 優先，因為它是客戶端顯式指定的意圖，header 是傳輸層的慣性。
 * 兩者皆缺代表從最早的事件讀起。
 *
 * <p>回放與拉取端點（`/integration/pms/events`）共用同一條讀取邏輯，
 * 含三秒安全遲滯與對外白名單；遲滯內（最新三秒）的事件由建線後的
 * 即時推送與下次重連的回放補上。進門資格（X-API-Key 的整合身分）
 * 由安全層的前綴矩陣把關，這裡不重複判。
 */
@RestController
public class IntegrationStreamController {

    /** 回放分頁大小：沿用拉取端點的單頁上限，逐頁讀到不滿頁為止。 */
    private static final int REPLAY_PAGE_SIZE = 500;

    private final StreamConnectionRegistry registry;
    private final IntegrationEventsService integrationEventsService;

    public IntegrationStreamController(StreamConnectionRegistry registry,
                                       IntegrationEventsService integrationEventsService) {
        this.registry = registry;
        this.integrationEventsService = integrationEventsService;
    }

    @GetMapping(path = "/integration/pms/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        long tenantId = TenantContext.require();
        long afterId = parseCursor(cursor != null ? cursor : lastEventId);
        return registry.connectIntegration(tenantId, () -> replayAll(afterId));
    }

    /** 逐頁讀完 cursor 之後的全部事件：回放不分頁給客戶端，漏一頁就是漏推。 */
    private List<IntegrationRoomStatusEvent> replayAll(long afterId) {
        List<IntegrationRoomStatusEvent> all = new ArrayList<>();
        long nextAfter = afterId;
        while (true) {
            List<IntegrationRoomStatusEvent> page =
                    integrationEventsService.replayAfter(nextAfter, REPLAY_PAGE_SIZE);
            all.addAll(page);
            if (page.size() < REPLAY_PAGE_SIZE) {
                return all;
            }
            nextAfter = Long.parseLong(page.getLast().eventId());
        }
    }

    /** cursor 省略代表從最早讀起（等同 0）；必須是非負十進位整數，否則 400。 */
    private static long parseCursor(String cursor) {
        if (cursor == null) {
            return 0L;
        }
        try {
            long value = Long.parseLong(cursor);
            if (value < 0) {
                throw new IntegrationBadRequestException("cursor 必須是非負十進位整數");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IntegrationBadRequestException("cursor 必須是非負十進位整數");
        }
    }
}
