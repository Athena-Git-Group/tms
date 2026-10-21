package com.athena.tms.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;

/**
 * 冪等簿 processed_ops 的存取。filter 在任何業務交易之外執行，而
 * processed_ops 有 RLS——若像一般 service 那樣直接開交易查，tms_app 連線
 * 會因缺 app.tenant_id 讀到零列、寫入被 policy 拒絕，冪等保護悄悄失效
 * 卻不報錯；所以讀寫一律包進 TenantScopedExecutor 的租戶交易。
 */
@Service
public class IdempotencyService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final ObjectMapper objectMapper;

    /** 回放憑據：result_ref jsonb 的欄位形狀（狀態碼＋Content-Type＋原樣 body）。 */
    public record StoredResult(int status, String contentType, String body) {
    }

    public IdempotencyService(JdbcTemplate jdbcTemplate,
                              TenantScopedExecutor tenantScopedExecutor,
                              ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.objectMapper = objectMapper;
    }

    public Optional<StoredResult> findResult(long tenantId, String opId) {
        return tenantScopedExecutor.callAsTenant(tenantId, () -> jdbcTemplate.query(
                        "SELECT result_ref FROM processed_ops WHERE tenant_id = ? AND op_id = ?",
                        (rs, rowNum) -> rs.getString("result_ref"), tenantId, opId)
                .stream().findFirst().map(this::parse));
    }

    /**
     * ON CONFLICT DO NOTHING 而不是接唯一鍵例外：兩個同鍵請求同時衝線時，
     * 例外會把交易標成 rollback-only、commit 再炸一次 UnexpectedRollback——
     * 先到者的結果已落簿，後到者安靜讓位即可。
     */
    public void store(long tenantId, String opId, int status, String contentType, String body) {
        String resultRef = toResultRef(status, contentType, body);
        tenantScopedExecutor.runAsTenant(tenantId, () -> jdbcTemplate.update("""
                INSERT INTO processed_ops (tenant_id, op_id, result_ref, created_at)
                VALUES (?, ?, ?::jsonb, now())
                ON CONFLICT (tenant_id, op_id) DO NOTHING
                """, tenantId, opId, resultRef));
    }

    private String toResultRef(int status, String contentType, String body) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", status);
        if (contentType != null) {
            node.put("content_type", contentType);
        }
        node.put("body", body == null ? "" : body);
        return node.toString();
    }

    private StoredResult parse(String resultRef) {
        try {
            JsonNode node = objectMapper.readTree(resultRef);
            String contentType = node.hasNonNull("content_type") ? node.get("content_type").asText() : null;
            return new StoredResult(node.get("status").asInt(), contentType, node.path("body").asText(""));
        } catch (JacksonException e) {
            throw new IllegalStateException("processed_ops.result_ref 不是預期的 JSON 形狀", e);
        }
    }
}
