package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import java.util.UUID;

final class RepairTicketRefs {

    private RepairTicketRefs() {
    }

    // 櫃檯派給工務時是後端自己開單，案例裡可能沒有任何一步拿到過這個單號，所以認不得的單號照單號回查一次。
    static UUID taskId(ScenarioWorld world, DbProbe dbProbe, long tenantId, String ticketNo) {
        if (!world.hasRepairTicket(ticketNo)) {
            dbProbe.queryOne(
                            "SELECT task_id FROM repair_tickets WHERE tenant_id = ? AND ticket_no = ?",
                            tenantId, ticketNo)
                    .ifPresent(row -> world.rememberRepairTicket(ticketNo, String.valueOf(row.get("task_id"))));
        }
        return UUID.fromString(world.repairTicketTaskId(ticketNo));
    }
}
