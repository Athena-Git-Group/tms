package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(value=JsonInclude.Include.NON_NULL)
public record LoginResponse(String token, String tokenType, OffsetDateTime expiresAt, Staff staff) {

    @JsonInclude(value=JsonInclude.Include.NON_NULL)
    public record Staff(UUID id, Long tenantId, String name, String role, String dept) {
    }
}
