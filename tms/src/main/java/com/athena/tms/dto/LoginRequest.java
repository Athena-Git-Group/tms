package com.athena.tms.dto;

/**
 * POST /auth/login 請求體。tenantId 必填：login_id 的唯一性是租戶內
 * （UNIQUE (tenant_id, login_id)），沒有租戶就無法定位帳號——SaaS 多店
 * 共用一庫，兩家店都有 hk1 是常態不是衝突。
 * deviceFingerprint 選填，登入不綁裝置；有帶才登記推播地址簿。
 */
public record LoginRequest(
        Long tenantId,
        String loginId,
        String password,
        String deviceFingerprint,
        String pushToken) {
}
