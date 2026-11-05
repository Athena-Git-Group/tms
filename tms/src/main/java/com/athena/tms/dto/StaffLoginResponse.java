package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * contracts 員工帳號與登入.yaml 的 {@code LoginResponse}：登入成功的回應形狀，銜接
 * techstack 已拍板的 JWT（TTL 12h）。{@code mustChangePassword}=true 表示這是臨時密碼
 * 登入，必須先設定新密碼才能繼續使用；完成改密前這組憑證呼叫其他需要身分的 API 一律被拒
 * （見 {@code BearerTokenAuthFilter}）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffLoginResponse(
        String token,
        OffsetDateTime expiresAt,
        UUID staffId,
        String name,
        String dept,
        String role,
        boolean mustChangePassword) {}
