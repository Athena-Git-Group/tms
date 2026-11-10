package com.athena.tms.dto;

/**
 * contracts registerPushToken（{@code PUT /me/push-token}）的請求形狀：App 取得推播地址後
 * 回報給後端。deviceFingerprint 識別「哪一台手機」，同一台重複回報只更新地址、不新增列。
 */
public record PushTokenRegisterRequest(String deviceFingerprint, String pushToken, String platform) {}
