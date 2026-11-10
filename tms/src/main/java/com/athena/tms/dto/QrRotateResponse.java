package com.athena.tms.dto;

import java.time.OffsetDateTime;

public record QrRotateResponse(String roomNo, String token, int version, OffsetDateTime issuedAt, int revokedSessions) {
}
