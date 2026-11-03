/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.dto;

import java.time.OffsetDateTime;

public record RoomQrResponse(String roomNo, String token, int version, OffsetDateTime issuedAt) {
}
