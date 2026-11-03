/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.RestController
 *  org.springframework.web.servlet.mvc.method.annotation.SseEmitter
 */
package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.service.StreamConnectionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class GuestStreamController {
    private final StreamConnectionRegistry registry;

    public GuestStreamController(StreamConnectionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(path={"/guest/stream"}, produces={"text/event-stream"})
    public SseEmitter stream() {
        CurrentActor actor = CurrentActor.require();
        return this.registry.connectGuest(actor.tenantId(), actor.reservationId());
    }
}
