package com.athena.tms.dto;

import java.time.OffsetDateTime;

public record RoomQrResponse(String roomNo, String token, int version, OffsetDateTime issuedAt) {
}
