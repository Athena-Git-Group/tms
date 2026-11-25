package com.athena.tms.controller;

import com.athena.tms.service.RoomRequestRateLimiter;
import org.springframework.http.HttpHeaders;

/**
 * 住客需求清單與送出需求的回應都帶住客這次住宿的送單名額，住客頁照著估算「還能送幾筆」，
 * 不必自己複製「10 分鐘 3 筆」的規則。後端擋單時數的是同一房所有住客送的需求，同一間房另一筆訂房送的不從這裡扣，
 * 所以名額不是 0 時後端仍可能回 429。標頭名稱沿用 IETF RateLimit 標頭的命名。
 */
final class GuestRequestQuotaHeaders {

    static final String LIMIT = "RateLimit-Limit";
    static final String REMAINING = "RateLimit-Remaining";
    static final String RESET = "RateLimit-Reset";

    private GuestRequestQuotaHeaders() {}

    static HttpHeaders of(RoomRequestRateLimiter.Quota quota) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LIMIT, Integer.toString(quota.limit()));
        headers.set(REMAINING, Integer.toString(quota.remaining()));
        headers.set(RESET, Long.toString(Math.max(0L, (quota.nextReleaseIn().toMillis() + 999L) / 1000L)));
        return headers;
    }
}
