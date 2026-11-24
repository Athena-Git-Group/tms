package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import java.util.UUID;

final class RepairTicketRefs {

    private RepairTicketRefs() {
    }

    static UUID taskId(ScenarioWorld world, String ticketNo) {
        return UUID.fromString(world.repairTicketTaskId(ticketNo));
    }
}
