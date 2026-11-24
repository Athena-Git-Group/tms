package com.athena.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.service.RoomRequestRateLimiter.Quota;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class GuestRequestQuotaHeadersTest {

    @Test
    void 釋出秒數無條件進位到整秒() {
        HttpHeaders headers = GuestRequestQuotaHeaders.of(new Quota(3, 1, Duration.ofMinutes(4).minusMillis(500)));

        assertThat(headers.getFirst("RateLimit-Limit")).isEqualTo("3");
        assertThat(headers.getFirst("RateLimit-Remaining")).isEqualTo("1");
        assertThat(headers.getFirst("RateLimit-Reset")).isEqualTo("240");
    }

    @Test
    void 沒有名額被佔用時釋出秒數是零() {
        HttpHeaders headers = GuestRequestQuotaHeaders.of(new Quota(3, 3, Duration.ZERO));

        assertThat(headers.getFirst("RateLimit-Remaining")).isEqualTo("3");
        assertThat(headers.getFirst("RateLimit-Reset")).isEqualTo("0");
    }
}
