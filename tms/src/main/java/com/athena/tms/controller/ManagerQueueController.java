package com.athena.tms.controller;

import com.athena.tms.dto.ManagerQueues;
import com.athena.tms.service.ManagerQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ManagerQueueController {

    private final ManagerQueueService managerQueueService;

    public ManagerQueueController(ManagerQueueService managerQueueService) {
        this.managerQueueService = managerQueueService;
    }

    @GetMapping("/me/manager-queues")
    public ManagerQueues managerQueues() {
        return managerQueueService.queues();
    }
}
