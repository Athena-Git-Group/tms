package com.athena.tms.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 產生 UUIDv7。同一個程序產生的值嚴格遞增，可以拿來當「建立先後」的排序依據：
 * 業務時間相同的兩列（例如測試把時鐘固定住、或同一毫秒建立）照 id 排就是建立的先後。
 *
 * <p>做法是 RFC 9562 的「固定長度計數器」：前 48 位元是毫秒時間，接著 12 位元是同一毫秒內的序號。
 * 同一毫秒再產生時序號加一；序號用完或系統時間倒退時，借用上一個值的毫秒加一，不讓值變小。
 */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_SEQUENCE = 0xFFF;

    private static long lastMillis = -1L;
    private static int sequence;

    private UuidV7() {
    }

    public static UUID generate() {
        long millis;
        int seq;
        synchronized (UuidV7.class) {
            long now = System.currentTimeMillis();
            if (now > lastMillis) {
                lastMillis = now;
                sequence = RANDOM.nextInt(MAX_SEQUENCE / 2);
            } else if (sequence < MAX_SEQUENCE) {
                sequence++;
            } else {
                lastMillis++;
                sequence = 0;
            }
            millis = lastMillis;
            seq = sequence;
        }
        long msb = (millis & 0xFFFFFFFFFFFFL) << 16 | 0x7000L | seq;
        long lsb = Long.MIN_VALUE | RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        return new UUID(msb, lsb);
    }
}
