package com.athena.tms.bdd.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * BDD 情境查「權威狀態」用的唯讀共用元件：把對 tasks / task_events / outbox
 * 的查詢集中在一處，讓 stepdef 只需要描述業務語意，不用各自組 SQL。
 *
 * <p>欄位名稱依 specs/truth/data/data-model.dbml 的 {@code Table tasks}、
 * {@code Table task_events}、{@code Table outbox}。只做唯讀查詢，不含任何
 * 斷言語意（「必查」清單是後續 Phase 的事）。
 */
public class DbProbe {

    private final JdbcClient jdbcClient;

    public DbProbe(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 泛用查詢：回傳所有符合的列，每列是欄位名 -> 值的 Map。 */
    public List<Map<String, Object>> queryMaps(String sql, Object... params) {
        return jdbcClient.sql(sql).params(params).query().listOfRows();
    }

    /** 泛用查詢：預期至多一列，沒有列回傳空 Optional；超過一列視為呼叫端 SQL 寫錯。 */
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

    /** 泛用查詢：筆數（例如 {@code SELECT count(*) FROM ...}）。 */
    public long count(String sql, Object... params) {
        Long result = jdbcClient.sql(sql).params(params).query(Long.class).single();
        return result == null ? 0L : result;
    }

    /** 任務目前的權威狀態（tasks 表整列）。 */
    public Optional<Map<String, Object>> taskRow(UUID taskId) {
        return queryOne("SELECT * FROM tasks WHERE id = ?", taskId);
    }

    /** 這個任務目前累積的所有事件，依 seq 排序（task_events 表）。 */
    public List<Map<String, Object>> eventsOf(UUID taskId) {
        return queryMaps("SELECT * FROM task_events WHERE task_id = ? ORDER BY seq", taskId);
    }

    /** 待送匣中某種 kind 的所有列，依 id 排序（outbox 表，kind：task_event|push|pms_status）。 */
    public List<Map<String, Object>> outboxRows(String kind) {
        return queryMaps("SELECT * FROM outbox WHERE kind = ? ORDER BY id", kind);
    }

    /** 待送匣中某種 kind、指定分區鍵（aggregate_key）的所有列，依 id 排序。 */
    public List<Map<String, Object>> outboxRows(String kind, String aggregateKey) {
        return queryMaps(
                "SELECT * FROM outbox WHERE kind = ? AND aggregate_key = ? ORDER BY id",
                kind, aggregateKey);
    }
}
