package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.athena.tms.service.StreamConnectionRegistry;

/**
 * `GET /guest/stream`：只推本住客工作階段綁定訂單的任務變化。
 * 角色授權在 SecurityConfig 的前綴矩陣（/guest→guest）已擋好；這裡取
 * CurrentActor 時身分必然是 GUEST 且綁好房＋訂單（session 驗證即綁定）。
 * 路由鍵在連線建立當下定死——session 中途被撤銷的連線最多活到心跳逾時，
 * 與 token 失效的語意一致（SSE 容許掉事件，安全邊界在資料讀取端點）。
 */
@RestController
public class GuestStreamController {

    private final StreamConnectionRegistry registry;

    public GuestStreamController(StreamConnectionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(path = "/guest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        CurrentActor actor = CurrentActor.require();
        return registry.connectGuest(actor.tenantId(), actor.reservationId());
    }
}
