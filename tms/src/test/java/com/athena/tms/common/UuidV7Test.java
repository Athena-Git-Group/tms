package com.athena.tms.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void 連續產生的值依位元組順序嚴格遞增() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            ids.add(UuidV7.generate());
        }
        for (int i = 1; i < ids.size(); i++) {
            assertThat(unsignedCompare(ids.get(i - 1), ids.get(i))).isNegative();
        }
        assertThat(ids.getLast().version()).isEqualTo(7);
        assertThat(ids.getLast().variant()).isEqualTo(2);
    }

    private static int unsignedCompare(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
