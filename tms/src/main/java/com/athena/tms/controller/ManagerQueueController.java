package com.athena.tms.controller;

import com.athena.tms.dto.ManagerQueues;
import com.athena.tms.service.ManagerQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主管工作台 API（contracts {@code groups/現場作業/主管工作台.yaml}）。路徑掛在
 * {@code /me/} 下與 getMyToday 同一慣例：內容依呼叫者而異。
 */
@RestController
@RequestMapping("/api/v1")
public class ManagerQueueController {

    private final ManagerQueueService managerQueueService;

    public ManagerQueueController(ManagerQueueService managerQueueService) {
        this.managerQueueService = managerQueueService;
    }

    /** 查看等呼叫者處理的待辦；判定公式與排序在 {@link ManagerQueueService#queues()}。 */
    @GetMapping("/me/manager-queues")
    public ManagerQueues managerQueues() {
        return managerQueueService.queues();
    }
}
