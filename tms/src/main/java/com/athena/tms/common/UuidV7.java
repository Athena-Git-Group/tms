package com.athena.tms.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 產生器（RFC 9562）：48-bit Unix 毫秒時間戳＋version 7＋74-bit 隨機。
 * 實體表 PK 一律由應用層呼叫這裡產號（spec §5.1）：時間有序讓 btree 索引
 * 尾端追加而不是隨機打散，DB 不經手取號則多實例水平擴充沒有單點。
 * task_events / outbox / tenants 不走這裡，它們是資料模型文件明列的例外，用 bigint identity 由 DB 取號。
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        long msb = (System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL) << 16; // 48-bit unix ms
        msb |= 0x7000L;                                                    // version = 7
        msb |= ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);           // 12-bit rand_a

        long randB = 0L;
        for (int i = 2; i < 10; i++) {
            randB = (randB << 8) | (random[i] & 0xFFL);
        }
        long lsb = 0x8000_0000_0000_0000L | (randB & 0x3FFF_FFFF_FFFF_FFFFL); // variant 10 + 62-bit rand_b

        return new UUID(msb, lsb);
    }
}
