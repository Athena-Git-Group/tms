/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.dto;

import com.athena.tms.dto.GuestMenuItemView;
import com.athena.tms.service.GuestTenantConfigReader;
import java.time.OffsetDateTime;
import java.util.List;

public record ScanResponse(String sessionToken, OffsetDateTime expiresAt, String roomNo, List<GuestTenantConfigReader.InfoPage> infoPages, List<GuestMenuItemView> menu) {
}
