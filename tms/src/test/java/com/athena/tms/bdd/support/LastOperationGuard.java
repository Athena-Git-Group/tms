package com.athena.tms.bdd.support;

import java.util.Map;
import java.util.UUID;

public class LastOperationGuard {

    private final DbProbe dbProbe;
    private Snapshot snapshot;
    private RoomSnapshot roomSnapshot;

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
        return snapshot;
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
