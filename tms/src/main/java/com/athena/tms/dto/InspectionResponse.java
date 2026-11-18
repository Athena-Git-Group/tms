package com.athena.tms.dto;

import java.util.UUID;

public record InspectionResponse(UUID taskId, String result, String taskState, int redoCount, boolean returnedToPool) {
}
