package com.athena.tms.bdd.support;

import java.util.Map;
import java.util.UUID;

/**
 * 「這次操作被拒絕」類 Then 句要比對的操作前快照，一個場景一份。
 * When 步驟在送出可能被拒絕的寫入前呼叫 {@link #captureFor}，記下目標任務當下的
 * 整列欄位、累積事件數與累積 outbox 數；Then 再以 {@link #current()} 取回，
 * 比對操作後是否一筆未動。
 */
public class LastOperationGuard {

    private final DbProbe dbProbe;
    private Snapshot snapshot;

    public LastOperationGuard(DbProbe dbProbe) {
        this.dbProbe = dbProbe;
    }

    /** 操作前的任務列快照：整列欄位、累積事件數、累積 outbox 數（task_event＋push 兩種 kind 合計）。 */
    public record Snapshot(UUID taskId, Map<String, Object> taskRow, int eventCount, int outboxCount) {
    }

    public Snapshot captureFor(UUID taskId) {
        Map<String, Object> row = dbProbe.taskRow(taskId).orElse(null);
        int eventCount = dbProbe.eventsOf(taskId).size();
        String aggregateKey = "task:" + taskId;
        int outboxCount = dbProbe.outboxRows("task_event", aggregateKey).size()
                + dbProbe.outboxRows("push", aggregateKey).size();
        snapshot = new Snapshot(taskId, row, eventCount, outboxCount);
        return snapshot;
    }

    /** 取出最近一次 {@link #captureFor} 記下的快照；沒有快照（忘了在 When 呼叫）時直接噴錯。 */
    public Snapshot current() {
        if (snapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的操作前快照：前一個 When 步驟需要先呼叫 LastOperationGuard.captureFor()");
        }
        return snapshot;
    }
}
