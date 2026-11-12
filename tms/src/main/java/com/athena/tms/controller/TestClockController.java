package com.athena.tms.controller;

import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.config.AdjustableClock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 端到端測試撥動後端時鐘的入口，只在啟動參數 {@code tms.test-clock.enabled=true} 時存在；
 * 沒開時這個類別不會註冊，路徑一律 404。
 *
 * <p>不需要權杖：測試工具在建立任何員工之前就要能把時間撥好，而且開關本身已經把它限定在
 * 測試用的後端。正式環境不帶這個參數，端點就不存在。
 *
 * <ul>
 *   <li>{@code POST /api/v1/test/clock}，body {@code {"isoTime":"2026-11-11T18:00:00+08:00"}}：
 *       把「現在」固定在該時刻，之後不會自己往前走。</li>
 *   <li>{@code DELETE /api/v1/test/clock}：回到系統時鐘。</li>
 *   <li>{@code GET /api/v1/test/clock}：查目前的「現在」與是否固定，給就緒檢查用。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/test/clock")
@ConditionalOnProperty(name = "tms.test-clock.enabled", havingValue = "true")
public class TestClockController {

    private final AdjustableClock clock;

    public TestClockController(AdjustableClock clock) {
        this.clock = clock;
    }

    public record SetClockRequest(String isoTime) {}

    @GetMapping
    public Map<String, Object> current() {
        return snapshot();
    }

    @PostMapping
    public Map<String, Object> set(@RequestBody SetClockRequest request) {
        if (request == null || request.isoTime() == null || request.isoTime().isBlank()) {
            throw new UnprocessableEntityException("isoTime 必填，格式如 2026-11-11T18:00:00+08:00");
        }
        try {
            clock.fixAt(OffsetDateTime.parse(request.isoTime()).toInstant());
        } catch (DateTimeParseException e) {
            throw new UnprocessableEntityException("isoTime 不是帶時差的 ISO-8601 時刻：" + request.isoTime());
        }
        return snapshot();
    }

    @DeleteMapping
    public Map<String, Object> reset() {
        clock.reset();
        return snapshot();
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("isoTime", OffsetDateTime.now(clock).toString());
        body.put("fixed", clock.isFixed());
        return body;
    }
}
