package com.athena.tms.push;

import java.util.UUID;

public record PushMessage(
        String idempotencyKey,
        UUID recipientStaffId,
        String event,
        String title,
        String body,
        String targetJson) {
}
