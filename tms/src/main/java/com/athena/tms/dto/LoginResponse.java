package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 登入成功回應。staff 區塊回 DB 當下值而非 claim 複述——前端選單與
 * 部門畫面靠它初始化，不必先解 JWT；passwordHash 永不出現在任何回應。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String token,
        String tokenType,
        OffsetDateTime expiresAt,
        Staff staff) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Staff(UUID id, Long tenantId, String name, String role, String dept) {
    }
}
