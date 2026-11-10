package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.service.StreamConnectionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StaffStreamController {
    private final StreamConnectionRegistry registry;

    public StaffStreamController(StreamConnectionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(path={"/staff/stream"}, produces={"text/event-stream"})
    public SseEmitter stream() {
        CurrentActor actor = CurrentActor.require();
        return this.registry.connectStaff(actor.tenantId(), actor.staffId(), actor.dept());
    }
}
