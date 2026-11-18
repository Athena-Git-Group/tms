package com.athena.tms.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record NotificationResponse(
        UUID id,
        String event,
        String title,
        String body,
        JsonNode target,
        OffsetDateTime createdAt,
        OffsetDateTime readAt) {}
