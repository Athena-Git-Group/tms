package com.athena.tms.dto;

import java.time.OffsetDateTime;

/**
 * `POST /admin/rooms/{roomNo}/qr/rotate` 輪替 QR 的結果。
 * revokedSessions 回報連坐撤銷的住客 session 數——主管按下「重產」時
 * 最想知道的就是「有多少人會因此被登出」。
 */
public record QrRotateResponse(
        String roomNo,
        String token,
        int version,
        OffsetDateTime issuedAt,
        int revokedSessions) {
}
