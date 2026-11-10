package com.athena.tms.dto;

import java.util.UUID;

public record TaskChange(long tenantId, UUID taskId, int seq, String type, String occurredAt, String dept, UUID assigneeId, UUID reservationId) {
}
