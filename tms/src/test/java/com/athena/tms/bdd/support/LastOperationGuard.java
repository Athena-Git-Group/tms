package com.athena.tms.bdd.support;

import java.util.Map;
import java.util.UUID;

/**
 * dsl.md「這次操作被拒絕」／「這次操作被拒絕，提示」兩句共用的「操作前快照」。
 *
 * <p>這兩句的「必查」除了回應是 4xx，還要求「tasks、task_events、outbox……的資料筆數和
 * 內容，都跟這次操作前一樣」，但負責發出那次（被拒絕的）寫入呼叫的 When stepdef，往往
 * 不是本類別所在的 common 套件（例如 taskexecution、repair、pool 各模組自己的
 * 「中斷」「請假」「報修」等 When）。這些 stepdef 類別彼此是 Cucumber 各自獨立的
 * glue 物件（即使同一個 scenario 也不共用實例欄位），而 {@code ScenarioWorld} 與
 * {@code BddSpringConfiguration} 不在本任務的改動範圍內，沒辦法加一個 scenario-scoped
 * bean 來傳遞這個快照。
 *
 * <p>改用全域靜態、以執行緒隔離的快照持有者：任何 When stepdef（不限 common 套件）
 * 在送出「可能被拒絕」的寫入呼叫前，呼叫 {@link #captureFor} 記下目標任務當下的
 * 權威狀態；本套件的「這次操作被拒絕」Then 再呼叫 {@link #current()} 取出比對。
 * Cucumber 本套件測試一律單執行緒依序執行（{@code RunTruthFeaturesTest} 沒有開
 * 任何平行設定），{@link ThreadLocal} 只是額外的保險，不是假設一定會平行執行。
 */
public final class LastOperationGuard {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private LastOperationGuard() {
    }

    /** 操作前的任務列快照：整列欄位、累積事件數、累積 outbox 數（task_event＋push 兩種 kind 合計）。 */
    public record Snapshot(UUID taskId, Map<String, Object> taskRow, int eventCount, int outboxCount) {
    }

    /** 查 {@link DbProbe} 落地一份快照並記下，供後續 {@link #current()} 取用。 */
    public static Snapshot captureFor(DbProbe dbProbe, UUID taskId) {
        Map<String, Object> row = dbProbe.taskRow(taskId).orElse(null);
        int eventCount = dbProbe.eventsOf(taskId).size();
        String aggregateKey = "task:" + taskId;
        int outboxCount = dbProbe.outboxRows("task_event", aggregateKey).size()
                + dbProbe.outboxRows("push", aggregateKey).size();
        Snapshot snapshot = new Snapshot(taskId, row, eventCount, outboxCount);
        HOLDER.set(snapshot);
        return snapshot;
    }

    /** 取出最近一次 {@link #captureFor} 記下的快照；沒有快照（忘了在 When 呼叫）時直接噴錯。 */
    public static Snapshot current() {
        Snapshot snapshot = HOLDER.get();
        if (snapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的操作前快照：前一個 When 步驟需要先呼叫 LastOperationGuard.captureFor()");
        }
        return snapshot;
    }

    /** 用完就清，避免下一個情境誤讀到上一個情境殘留的快照。 */
    public static void clear() {
        HOLDER.remove();
    }
}
