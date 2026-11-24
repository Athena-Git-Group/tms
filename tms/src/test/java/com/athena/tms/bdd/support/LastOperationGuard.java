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
    private CreateRequestSnapshot createRequestSnapshot;
    private ExistingRows existingRowsBeforeCreate;
    private boolean createRequestIsLatest;

    public LastOperationGuard(DbProbe dbProbe) {
        this.dbProbe = dbProbe;
    }

    public record Snapshot(UUID taskId, Map<String, Object> taskRow, int eventCount, int outboxCount,
            Map<String, Object> repairTicketRow, long inspectionCount, long outboxMaxId) {
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
        long outboxMaxId = dbProbe.count("SELECT COALESCE(MAX(id), 0) FROM outbox");
        snapshot = new Snapshot(taskId, row, eventCount, outboxCount, repairTicketRow, inspectionCount, outboxMaxId);
        createRequestIsLatest = false;
        eventsBefore = dbProbe.eventsOf(taskId);
        tenantTaskCountBefore = row == null
                ? -1
                : dbProbe.count("SELECT count(*) FROM tasks WHERE tenant_id = ?", row.get("tenant_id"));
        return snapshot;
    }

    public Snapshot captureWithoutTask() {
        return captureFor(UUID.randomUUID());
    }

    public List<Map<String, Object>> eventsBefore() {
        current();
        return eventsBefore;
    }

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

    public record CreateRequestSnapshot(long tenantId, long tasks, long taskEvents, long repairTickets,
            long lastTaskNo, long lastTicketNo, long outbox, long outboxMaxId) {
    }

    public CreateRequestSnapshot captureCreateRequest(long tenantId) {
        createRequestSnapshot = createRequestCounts(tenantId);
        existingRowsBeforeCreate = existingRows(tenantId);
        createRequestIsLatest = true;
        return createRequestSnapshot;
    }

    public boolean createRequestIsLatest() {
        return createRequestIsLatest && createRequestSnapshot != null;
    }

    public record ExistingRows(List<Map<String, Object>> tasks, List<Map<String, Object>> taskEvents,
            List<Map<String, Object>> repairTickets) {
    }

    public ExistingRows existingRows(long tenantId) {
        return new ExistingRows(
                dbProbe.queryMaps("SELECT * FROM tasks WHERE tenant_id = ? ORDER BY id", tenantId),
                dbProbe.queryMaps("SELECT * FROM task_events WHERE tenant_id = ? ORDER BY id", tenantId),
                dbProbe.queryMaps("SELECT * FROM repair_tickets WHERE tenant_id = ? ORDER BY task_id", tenantId));
    }

    public ExistingRows existingRowsBeforeCreate() {
        currentCreateRequest();
        return existingRowsBeforeCreate;
    }

    public CreateRequestSnapshot createRequestCounts(long tenantId) {
        return new CreateRequestSnapshot(
                tenantId,
                dbProbe.count("SELECT count(*) FROM tasks WHERE tenant_id = ?", tenantId),
                dbProbe.count("SELECT count(*) FROM task_events WHERE tenant_id = ?", tenantId),
                dbProbe.count("SELECT count(*) FROM repair_tickets WHERE tenant_id = ?", tenantId),
                dbProbe.count("SELECT COALESCE(MAX(last_no), 0) FROM task_counters WHERE tenant_id = ?", tenantId),
                dbProbe.count("SELECT COALESCE(MAX(last_no), 0) FROM repair_ticket_counters WHERE tenant_id = ?", tenantId),
                dbProbe.count("SELECT count(*) FROM outbox WHERE tenant_id = ?", tenantId),
                dbProbe.count("SELECT COALESCE(MAX(id), 0) FROM outbox"));
    }

    public CreateRequestSnapshot currentCreateRequest() {
        if (createRequestSnapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的代開前快照：前一個 When 步驟需要先呼叫 LastOperationGuard.captureCreateRequest()");
        }
        return createRequestSnapshot;
    }
}
