package com.athena.tms.dto;

import java.util.List;
import java.util.UUID;

public record CompleteTaskRequest(List<UUID> photoIds, String resolutionNote, String resolutionType) {
}
