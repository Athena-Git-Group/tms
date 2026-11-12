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
