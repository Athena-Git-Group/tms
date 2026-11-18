package com.athena.tms.common;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        long msb = (System.currentTimeMillis() & 0xFFFFFFFFFFFFL) << 16;
        msb |= 0x7000L;
        msb |= ((long)random[0] & 0xFL) << 8 | (long)random[1] & 0xFFL;
        long randB = 0L;
        for (int i = 2; i < 10; ++i) {
            randB = randB << 8 | (long)random[i] & 0xFFL;
        }
        long lsb = Long.MIN_VALUE | randB & 0x3FFFFFFFFFFFFFFFL;
        return new UUID(msb, lsb);
    }
}
