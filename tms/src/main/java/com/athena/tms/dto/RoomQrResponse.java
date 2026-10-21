package com.athena.tms.dto;

import java.time.OffsetDateTime;

/**
 * `GET /admin/rooms/{roomNo}/qr` 的列印資料。
 * 刻意不回掃碼 URL：住客入口的路徑格式屬 /guest/scan 端點所有，
 * 這裡多猜一個 URL 格式就是兩處真相；列印端拿 token 自行組版。
 */
public record RoomQrResponse(
        String roomNo,
        String token,
        int version,
        OffsetDateTime issuedAt) {
}
