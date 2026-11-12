package com.athena.tms.bdd.support;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;

public final class OutboxAwait {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final DbProbe dbProbe;
    private final ObjectMapper objectMapper;
    private final Duration pollInterval;
    private final Duration timeout;

    public OutboxAwait(DbProbe dbProbe, ObjectMapper objectMapper) {
        this(dbProbe, objectMapper, DEFAULT_POLL_INTERVAL, DEFAULT_TIMEOUT);
    }

    private OutboxAwait(DbProbe dbProbe, ObjectMapper objectMapper, Duration pollInterval, Duration timeout) {
        this.dbProbe = dbProbe;
        this.objectMapper = objectMapper;
        this.pollInterval = pollInterval;
        this.timeout = timeout;
    }

    public OutboxAwait withPollInterval(Duration pollInterval) {
        return new OutboxAwait(dbProbe, objectMapper, pollInterval, timeout);
    }

    public OutboxAwait withTimeout(Duration timeout) {
        return new OutboxAwait(dbProbe, objectMapper, pollInterval, timeout);
    }

    public Map<String, Object> waitForRow(String what, String sql, Object... params) {
        return pollUntilFound(() -> dbProbe.queryOne(sql, params), what);
    }

    public Map<String, Object> waitForOutboxRow(String kind, Predicate<Map<String, Object>> predicate, String what) {
        return pollUntilFound(
                () -> dbProbe.outboxRows(kind).stream().filter(predicate).findFirst(),
                what);
    }

    public Map<String, Object> waitForSent(long outboxId) {
        return pollUntilFound(
                () -> dbProbe.queryOne(
                        "SELECT * FROM outbox WHERE id = ? AND (status = 'sent' OR sent_at IS NOT NULL)",
                        outboxId),
                "outbox id=" + outboxId + " 已被 relay 送出（status=sent 或 sent_at 非空）");
    }

    public Map<String, Object> waitForSent(String kind, Predicate<Map<String, Object>> predicate, String what) {
        return pollUntilFound(
                () -> dbProbe.outboxRows(kind).stream()
                        .filter(predicate)
                        .filter(OutboxAwait::isSent)
                        .findFirst(),
                what + "（已送出）");
    }

    public PushPayloadView waitForPush(Predicate<PushPayloadView> predicate, String what) {
        return pollUntilFound(
                () -> dbProbe.outboxRows("push").stream()
                        .map(this::parsePush)
                        .filter(predicate)
                        .findFirst(),
                what);
    }

    public PushPayloadView waitForPushTo(UUID recipientStaffId) {
        return waitForPush(
                view -> recipientStaffId.equals(view.recipientStaffId()),
                "推播送給 staffId=" + recipientStaffId);
    }

    private PushPayloadView parsePush(Map<String, Object> outboxRow) {
        String json = asJsonText(outboxRow.get("payload"));
        try {
            return objectMapper.readValue(json, PushPayloadView.class);
        } catch (RuntimeException e) {
            throw new AssertionError("解析 push payload 失敗：" + json, e);
        }
    }

    private static boolean isSent(Map<String, Object> outboxRow) {
        Object status = outboxRow.get("status");
        Object sentAt = outboxRow.get("sent_at");
        return "sent".equals(status) || sentAt != null;
    }

    /** jsonb 欄位經 JDBC 讀出來不保證是 String，但驅動包裝型別的 toString() 就是原始 JSON 文字。 */
    private static String asJsonText(Object payload) {
        if (payload == null) {
            throw new IllegalStateException("outbox 列沒有 payload");
        }
        return payload instanceof String s ? s : payload.toString();
    }

    private <T> T pollUntilFound(Supplier<Optional<T>> probe, String what) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> found = probe.get();
            if (found.isPresent()) {
                return found.get();
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待被中斷：" + what, e);
            }
        }
        throw new AssertionError(
                "等待逾時（" + timeout.toSeconds() + "s，每 " + pollInterval.toMillis() + "ms 查一次）：" + what);
    }
}
