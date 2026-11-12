package com.athena.tms.bdd.support;

import java.util.UUID;

public record PushPayloadView(
        UUID recipientStaffId,
        String event,
        String title,
        String body,
        Target target) {

    public record Target(String kind, UUID taskId, String dept, UUID highlightTaskId) {
    }
}
