package com.athena.tms.controller;

import com.athena.tms.dto.IntegrationEventsResponse;
import com.athena.tms.service.IntegrationEventsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PMS 整合端點：`GET /integration/pms/events` 讓 PMS 用 cursor 增量拉取
 * 房況事件。進門資格（X-API-Key 的整合身分）由安全層的前綴矩陣把關，
 * 這裡不重複判。after 與 limit 收成字串讓 service 自己驗值域，
 * 因為參數錯誤契約上要回 400 的統一錯誤格式，不能走框架的型別綁定錯誤。
 */
@RestController
@RequestMapping("/integration/pms")
public class IntegrationEventsController {

    private final IntegrationEventsService integrationEventsService;

    public IntegrationEventsController(IntegrationEventsService integrationEventsService) {
        this.integrationEventsService = integrationEventsService;
    }

    /** cursor 增量拉取：after 省略從最早讀起，limit 省略預設 100。 */
    @GetMapping("/events")
    public IntegrationEventsResponse events(
            @RequestParam(name = "after", required = false) String after,
            @RequestParam(name = "limit", required = false) String limit) {
        return integrationEventsService.pullEvents(after, limit);
    }
}
