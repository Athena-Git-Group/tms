package com.athena.tms.service;

import com.athena.tms.dto.desk.DeskCreateRequestsResult;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeskCreateRequestIdempotency {
    private final JdbcTemplate jdbcTemplate;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public DeskCreateRequestIdempotency(
            JdbcTemplate jdbcTemplate, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    public Outcome runOnce(long tenantId, String idempotencyKey, Supplier<DeskCreateRequestsResult> work) {
        this.jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?::text || ':' || ?, 0))::text",
                String.class, tenantId, idempotencyKey);
        Optional<IdempotencyService.StoredResult> replay = this.idempotencyService.findResult(tenantId, idempotencyKey);
        if (replay.isPresent()) {
            return new Outcome(null, replay.get());
        }
        DeskCreateRequestsResult created = work.get();
        this.idempotencyService.store(tenantId, idempotencyKey, HttpStatus.CREATED.value(),
                MediaType.APPLICATION_JSON_VALUE, this.objectMapper.writeValueAsString(created));
        return new Outcome(created, null);
    }

    public record Outcome(DeskCreateRequestsResult created, IdempotencyService.StoredResult replay) {}
}
