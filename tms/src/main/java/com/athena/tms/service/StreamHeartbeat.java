package com.athena.tms.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSE 心跳：每 15 秒自發、不經 Kafka。心跳要證明的是「推送模組到客戶端」
 * 這段還活著；走 Kafka 的話 broker 故障會連心跳一起消失，客戶端就分不出
 * 「沒事件」與「連線已死」。排程器由 OutboxSchedulingConfig 全域開啟。
 *
 * <p>fixedDelay 而非 fixedRate：廣播對慢客戶端是同步寫出，一輪拖長時
 * fixedRate 會讓下一輪疊上來。
 */
@Component
public class StreamHeartbeat {

    private final StreamConnectionRegistry registry;

    public StreamHeartbeat(StreamConnectionRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${tms.stream.heartbeat-interval:15s}")
    public void beat() {
        registry.broadcastHeartbeat();
    }
}
