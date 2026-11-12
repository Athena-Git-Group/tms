package com.athena.tms.bdd.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class DbProbe {

    private final JdbcClient jdbcClient;

    public DbProbe(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> queryMaps(String sql, Object... params) {
        return jdbcClient.sql(sql).params(params).query().listOfRows();
    }

    public Optional<Map<String, Object>> queryOne(String sql, Object... params) {
        List<Map<String, Object>> rows = queryMaps(sql, params);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "預期至多一列，實際 " + rows.size() + " 列：" + sql);
        }
        return Optional.of(rows.get(0));
    }

    public long count(String sql, Object... params) {
        Long result = jdbcClient.sql(sql).params(params).query(Long.class).single();
        return result;
    }

    public Optional<Map<String, Object>> taskRow(UUID taskId) {
        return queryOne("SELECT * FROM tasks WHERE id = ?", taskId);
    }

    public List<Map<String, Object>> eventsOf(UUID taskId) {
        return queryMaps("SELECT * FROM task_events WHERE task_id = ? ORDER BY seq", taskId);
    }

    public List<Map<String, Object>> outboxRows(String kind) {
        return queryMaps("SELECT * FROM outbox WHERE kind = ? ORDER BY id", kind);
    }

    public List<Map<String, Object>> outboxRows(String kind, String aggregateKey) {
        return queryMaps(
                "SELECT * FROM outbox WHERE kind = ? AND aggregate_key = ? ORDER BY id",
                kind, aggregateKey);
    }
}
