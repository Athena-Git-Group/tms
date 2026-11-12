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
    private RoomSnapshot roomSnapshot;

    public LastOperationGuard(DbProbe dbProbe) {
        this.dbProbe = dbProbe;
    }

    /**
     * 操作前的任務列快照：整列欄位、累積事件數、累積 outbox 數（task_event＋push 兩種 kind 合計）、
     * 工務單整列（不是工務單時為 null；last_verify_failed_reason、verified_by、resolution_type 都在裡面）、
     * 累積查房紀錄數。
     */
    public record Snapshot(UUID taskId, Map<String, Object> taskRow, int eventCount, int outboxCount,
            Map<String, Object> repairTicketRow, long inspectionCount) {
    }

    public Snapshot captureFor(UUID taskId) {
        Map<String, Object> row = dbProbe.taskRow(taskId).orElse(null);
        int eventCount = dbProbe.eventsOf(taskId).size();
        String aggregateKey = "task:" + taskId;
        int outboxCount = dbProbe.outboxRows("task_event", aggregateKey).size()
                + dbProbe.outboxRows("push", aggregateKey).size();
        Map<String, Object> repairTicketRow = dbProbe
                .queryOne("SELECT * FROM repair_tickets WHERE task_id = ?", taskId)
                .orElse(null);
        long inspectionCount = dbProbe.count("SELECT count(*) FROM inspections WHERE task_id = ?", taskId);
        snapshot = new Snapshot(taskId, row, eventCount, outboxCount, repairTicketRow, inspectionCount);
        return snapshot;
    }

    /**
     * 操作前某一間房的 rooms 整列，加上當時 outbox 最大的 id。「房況沒有改變」類 Then 句用它比對：
     * 驗收、完修這類寫入不該動到房況時，操作後的 hk_state 等欄位要跟這一份一樣；
     * id 大於 {@code outboxMaxId} 的 outbox 列才算這次動作之後寫入的。
     */
    public record RoomSnapshot(String roomNo, Map<String, Object> roomRow, long outboxMaxId) {
    }

    /** 記下指定租戶、指定房號的 rooms 整列與當下 outbox 最大 id；房號不存在時直接噴錯，免得 Then 句拿空快照比出假的「沒有改變」。 */
    public RoomSnapshot captureRoom(long tenantId, String roomNo) {
        Map<String, Object> row = dbProbe
                .queryOne("SELECT * FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, roomNo)
                .orElseThrow(() -> new IllegalStateException("rooms 查無房號「" + roomNo + "」，無法記下操作前房況"));
        long outboxMaxId = dbProbe.count("SELECT COALESCE(MAX(id), 0) FROM outbox");
        roomSnapshot = new RoomSnapshot(roomNo, row, outboxMaxId);
        return roomSnapshot;
    }

    /** 取出最近一次 {@link #captureRoom} 記下的房況快照；沒有快照時直接噴錯。 */
    public RoomSnapshot currentRoom() {
        if (roomSnapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的操作前房況：前一個 When 步驟需要先呼叫 LastOperationGuard.captureRoom()");
        }
        return roomSnapshot;
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
