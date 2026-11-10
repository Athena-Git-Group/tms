package com.athena.tms.controller;

import com.athena.tms.dto.IntegrationEventsResponse;
import com.athena.tms.service.IntegrationEventsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/integration/pms"})
public class IntegrationEventsController {
    private final IntegrationEventsService integrationEventsService;

    public IntegrationEventsController(IntegrationEventsService integrationEventsService) {
        this.integrationEventsService = integrationEventsService;
    }

    @GetMapping(value={"/events"})
    public IntegrationEventsResponse events(@RequestParam(name="after", required=false) String after, @RequestParam(name="limit", required=false) String limit) {
        return this.integrationEventsService.pullEvents(after, limit);
    }
}
