package com.athena.tms.dto;

import java.security.SecureRandom;

/**
 * 房間 QR 權杖產生器，token 格式的唯一擁有者。
 *
 * <p>為什麼是「22 個 base62 字元」：62^22 ≈ 2^131，剛好跨過 spec 要求的
 * 128-bit 隨機下限；字元集只有英數、沒有 '+/=' 之類的符號，印在貼紙上的
 * URL 不需要再做百分比編碼。token 整串來自 SecureRandom，不含房號、租戶、
 * 時間戳任何可推導資訊——撿到貼紙的人知道的只有 token 本身（掃碼
 * 守門靠 DB 列的 status，不靠 token 藏什麼秘密）。
 *
 * <p>全域唯一由 V1 的 uq_room_qr_tokens_token 兜底：131-bit 隨機撞上既有列
 * 的機率低於硬體故障，程式層不做「查重再插」。那其實是先查詢再寫入，而
 * 查詢與寫入之間仍可能有另一個請求插進同一個值，查過仍擋不住真正的撞號，
 * 唯一能保證「全域唯一」的只有 DB 的唯一約束，自己加一層查詢只是假安全感。
 */
public final class QrTokens {

    private static final String BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 22 × log2(62) ≈ 131 bit，跨過 spec 要求的 128 bit 隨機下限。 */
    private static final int LENGTH = 22;

    private static final SecureRandom RANDOM = new SecureRandom();

    private QrTokens() {
    }

    public static String generate() {
        StringBuilder token = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            token.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return token.toString();
    }
}
