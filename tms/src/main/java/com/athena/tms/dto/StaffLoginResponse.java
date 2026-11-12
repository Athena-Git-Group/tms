package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffLoginResponse(
        String token,
        OffsetDateTime expiresAt,
        UUID staffId,
        String name,
        String dept,
        String role,
        boolean mustChangePassword) {}
