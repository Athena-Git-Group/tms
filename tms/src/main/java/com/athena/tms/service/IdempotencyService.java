/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  tools.jackson.core.JacksonException
 *  tools.jackson.databind.JsonNode
 *  tools.jackson.databind.ObjectMapper
 *  tools.jackson.databind.node.ObjectNode
 */
package com.athena.tms.service;

import com.athena.tms.service.TenantScopedExecutor;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final ObjectMapper objectMapper;

    public IdempotencyService(JdbcTemplate jdbcTemplate, TenantScopedExecutor tenantScopedExecutor, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.objectMapper = objectMapper;
    }

    public Optional<StoredResult> findResult(long tenantId, String opId) {
        return this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.jdbcTemplate.query("SELECT result_ref FROM processed_ops WHERE tenant_id = ? AND op_id = ?", (rs, rowNum) -> rs.getString("result_ref"), new Object[]{tenantId, opId}).stream().findFirst().map(this::parse));
    }

    public void store(long tenantId, String opId, int status, String contentType, String body) {
        String resultRef = this.toResultRef(status, contentType, body);
        this.tenantScopedExecutor.runAsTenant(tenantId, () -> this.jdbcTemplate.update("INSERT INTO processed_ops (tenant_id, op_id, result_ref, created_at)\nVALUES (?, ?, ?::jsonb, now())\nON CONFLICT (tenant_id, op_id) DO NOTHING\n", new Object[]{tenantId, opId, resultRef}));
    }

    private String toResultRef(int status, String contentType, String body) {
        ObjectNode node = this.objectMapper.createObjectNode();
        node.put("status", status);
        if (contentType != null) {
            node.put("content_type", contentType);
        }
        node.put("body", body == null ? "" : body);
        return node.toString();
    }

    private StoredResult parse(String resultRef) {
        try {
            JsonNode node = this.objectMapper.readTree(resultRef);
            String contentType = node.hasNonNull("content_type") ? node.get("content_type").asText() : null;
            return new StoredResult(node.get("status").asInt(), contentType, node.path("body").asText(""));
        }
        catch (JacksonException e) {
            throw new IllegalStateException("processed_ops.result_ref \u4e0d\u662f\u9810\u671f\u7684 JSON \u5f62\u72c0", e);
        }
    }

    public record StoredResult(int status, String contentType, String body) {
    }
}
