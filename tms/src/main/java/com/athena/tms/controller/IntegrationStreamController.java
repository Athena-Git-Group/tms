/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.RequestHeader
 *  org.springframework.web.bind.annotation.RequestParam
 *  org.springframework.web.bind.annotation.RestController
 *  org.springframework.web.servlet.mvc.method.annotation.SseEmitter
 */
package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.service.IntegrationBadRequestException;
import com.athena.tms.service.IntegrationEventsService;
import com.athena.tms.service.StreamConnectionRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class IntegrationStreamController {
    private static final int REPLAY_PAGE_SIZE = 500;
    private final StreamConnectionRegistry registry;
    private final IntegrationEventsService integrationEventsService;

    public IntegrationStreamController(StreamConnectionRegistry registry, IntegrationEventsService integrationEventsService) {
        this.registry = registry;
        this.integrationEventsService = integrationEventsService;
    }

    @GetMapping(path={"/integration/pms/stream"}, produces={"text/event-stream"})
    public SseEmitter stream(@RequestParam(name="cursor", required=false) String cursor, @RequestHeader(name="Last-Event-ID", required=false) String lastEventId) {
        long tenantId = TenantContext.require();
        long afterId = IntegrationStreamController.parseCursor(cursor != null ? cursor : lastEventId);
        return this.registry.connectIntegration(tenantId, () -> this.replayAll(afterId));
    }

    private List<IntegrationRoomStatusEvent> replayAll(long afterId) {
        ArrayList<IntegrationRoomStatusEvent> all = new ArrayList<IntegrationRoomStatusEvent>();
        long nextAfter = afterId;
        while (true) {
            List<IntegrationRoomStatusEvent> page = this.integrationEventsService.replayAfter(nextAfter, 500);
            all.addAll(page);
            if (page.size() < 500) {
                return all;
            }
            nextAfter = Long.parseLong(((IntegrationRoomStatusEvent)page.getLast()).eventId());
        }
    }

    private static long parseCursor(String cursor) {
        if (cursor == null) {
            return 0L;
        }
        try {
            long value = Long.parseLong(cursor);
            if (value < 0L) {
                throw new IntegrationBadRequestException("cursor 必須是非負十進位整數");
            }
            return value;
        }
        catch (NumberFormatException e) {
            throw new IntegrationBadRequestException("cursor 必須是非負十進位整數");
        }
    }
}
