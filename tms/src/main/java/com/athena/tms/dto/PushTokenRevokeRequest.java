package com.athena.tms.dto;

/** contracts revokePushToken（{@code DELETE /me/push-token}）的請求形狀：App 登出時解除這台手機的推播。 */
public record PushTokenRevokeRequest(String deviceFingerprint) {}
