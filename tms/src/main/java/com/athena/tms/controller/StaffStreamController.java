package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.athena.tms.service.StreamConnectionRegistry;

/**
 * `GET /staff/stream`：推本部門待接池＋本人任務的變化。
 * 前綴授權（GET /staff 開放 housekeeping/engineering/manager/frontdesk）
 * 由 SecurityConfig 矩陣生效；dept 取 DB 當下值（filter 每請求查），
 * manager／frontdesk 無部門時連線只收本人受派任務——部門池訂閱對他們
 * 沒有明確語意，寬放反而把全店事件灌進櫃檯畫面。
 */
@RestController
public class StaffStreamController {

    private final StreamConnectionRegistry registry;

    public StaffStreamController(StreamConnectionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(path = "/staff/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        CurrentActor actor = CurrentActor.require();
        return registry.connectStaff(actor.tenantId(), actor.staffId(), actor.dept());
    }
}
