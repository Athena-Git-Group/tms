package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.support.DbProbe;

/**
 * dsl.md「系統沒有建立工務單」「不該發生：沒有建立工務單」共用的「操作前筆數快照」。
 *
 * <p>本模組專屬（不動 {@code com.athena.tms.bdd.support.LastOperationGuard}）：那個共用守衛
 * 只比對單一 taskId 的整列快照，這裡要比的是「repair_tickets 與 type=repair 的 tasks 筆數」
 * 這種全租戶層級的計數，兩者快照形狀不同，不借用也不擴充共用元件。
 *
 * <p>同套件內的 When/Then stepdef 各自是 Cucumber 獨立的 glue 物件實例，彼此不共用欄位，
 * 因此一樣採全域靜態、執行緒隔離的持有者；本套件測試單執行緒依序執行
 * （{@code RunTruthFeaturesTest} 沒有開任何平行設定），ThreadLocal 只是額外保險。
 */
final class RepairCountGuard {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private RepairCountGuard() {
    }

    record Snapshot(long repairTicketCount, long repairTaskCount) {
    }

    static Snapshot captureFor(DbProbe dbProbe, long tenantId) {
        long ticketCount = dbProbe.count("SELECT count(*) FROM repair_tickets WHERE tenant_id = ?", tenantId);
        long taskCount = dbProbe.count(
                "SELECT count(*) FROM tasks WHERE tenant_id = ? AND type = 'repair'", tenantId);
        Snapshot snapshot = new Snapshot(ticketCount, taskCount);
        HOLDER.set(snapshot);
        return snapshot;
    }

    static Snapshot current() {
        Snapshot snapshot = HOLDER.get();
        if (snapshot == null) {
            throw new IllegalStateException(
                    "沒有可比對的工務單筆數快照：前一個 When 步驟需要先呼叫 RepairCountGuard.captureFor()");
        }
        return snapshot;
    }
}
