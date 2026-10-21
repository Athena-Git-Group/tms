package com.athena.tms.dto;

import com.athena.tms.service.GuestTenantConfigReader.InfoPage;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 掃碼成功的回應，「資訊頁＋選單」一次到位：住客掃完碼的第一屏
 * 就要能看能點，不逼前端再打一輪 /guest/home。expiresAt 讓前端能在退房時刻
 * 主動收攤，而不是等下一個請求吃 401 才發現。
 */
public record ScanResponse(
        String sessionToken,
        OffsetDateTime expiresAt,
        String roomNo,
        List<InfoPage> infoPages,
        List<GuestMenuItemView> menu) {
}
