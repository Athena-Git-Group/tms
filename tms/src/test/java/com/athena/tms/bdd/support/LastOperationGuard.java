package com.athena.tms.bdd.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LastOperationGuard {

    private final DbProbe dbProbe;
    private Snapshot snapshot;
    private RoomSnapshot roomSnapshot;
    private List<Map<String, Object>> eventsBefore;
    private long tenantTaskCountBefore;

    public LastOperationGuard(DbProbe dbProbe) {
        this.dbProbe = dbProbe;
    }

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
        eventsBefore = dbProbe.eventsOf(taskId);
        tenantTaskCountBefore = row == null
                ? -1
                : dbProbe.count("SELECT count(*) FROM tasks WHERE tenant_id = ?", row.get("tenant_id"));
        return snapshot;
    }

    /**
     * 給不針對某一張單的讀取操作用，例如讀取整個看板。
     * 讀取被拒時共用的「這次操作被拒絕」仍要有一份操作前的記錄可以比對，
     * 否則會因為找不到記錄而失敗，蓋掉真正要看的拒絕結果。
     * 這份記錄掛在一個不屬於任何單的識別碼上，所以之後的比對不會檢查任何一張既有的單。
     */
    public Snapshot captureWithoutTask() {
        return captureFor(UUID.randomUUID());
    }

    /**
     * 操作前這張單的每一筆事件原樣。只數筆數看不出舊的事件有沒有被改寫，
     * 「歷程只增不改」要逐筆比對 seq、種類、操作者、內容與時間。
     */
    public List<Map<String, Object>> eventsBefore() {
        current();
        return eventsBefore;
    }

    /** 操作前全館（這張單所屬飯店）的單數，用來確認一次操作沒有順手多建出別的單。 */
    public long tenantTaskCountBefore() {
        current();
        return tenantTaskCountBefore;
    }

    public record RoomSnapshot(String roomNo, Map<String, Object> roomRow, long outboxMaxId) {
    }

    public RoomSnapshot captureRoom(long tenantId, String roomNo) {
        Map<String, Object> row = dbProbe
                .queryOne("SELECT * FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, roomNo)
                .orElseThrow(() -> new IllegalStateException("rooms 查無房號「" + roomNo + "」，無法記下操作前房況"));
        long outboxMaxId = dbProbe.count("SELECT COALESCE(MAX(id), 0) FROM outbox");
        roomSnapshot = new RoomSnapshot(roomNo, row, outboxMaxId);
        return roomSnapshot;
    }

    public RoomSnapshot currentRoom() {
        if (roomSnapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的操作前房況：前一個 When 步驟需要先呼叫 LastOperationGuard.captureRoom()");
        }
        return roomSnapshot;
    }

    public Snapshot current() {
        if (snapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的操作前快照：前一個 When 步驟需要先呼叫 LastOperationGuard.captureFor()");
        }
        return snapshot;
    }
}
