package com.athena.tms.kb;

import java.util.UUID;

public record KbEscalationView(
        UUID id,
        UUID answerId,
        String question,
        UUID taskId,
        UUID askedBy,
        String status,
        String reply,
        UUID repliedBy) {
}
