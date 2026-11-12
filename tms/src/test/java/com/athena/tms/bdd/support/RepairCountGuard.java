package com.athena.tms.bdd.support;

public class RepairCountGuard {

    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private Snapshot snapshot;

    public RepairCountGuard(DbProbe dbProbe, TenantFixtures tenantFixtures) {
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
    }

    public record Snapshot(long repairTicketCount, long repairTaskCount) {
    }

    public Snapshot capture() {
        long tenantId = tenantFixtures.testTenantId();
        long ticketCount = dbProbe.count("SELECT count(*) FROM repair_tickets WHERE tenant_id = ?", tenantId);
        long taskCount = dbProbe.count(
                "SELECT count(*) FROM tasks WHERE tenant_id = ? AND type = 'repair'", tenantId);
        snapshot = new Snapshot(ticketCount, taskCount);
        return snapshot;
    }

    public Snapshot current() {
        if (snapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的工務單筆數快照：前一個 When 步驟需要先呼叫 RepairCountGuard.capture()");
        }
        return snapshot;
    }
}
