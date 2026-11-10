package com.athena.tms.dto;

public record LoginRequest(Long tenantId, String loginId, String password, String deviceFingerprint, String pushToken) {
}
