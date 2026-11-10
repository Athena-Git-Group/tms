package com.athena.tms.common;

/**
 * outbox relay 送上 Kafka 時附在每筆訊息上的 header 名稱。
 *
 * <p>Kafka 訊息的 key 是 outbox 的 aggregate_key（同一間房、同一張任務的訊息要落在同一個
 * partition 才能保序），payload 是業務內容，兩者都沒有地方放「這筆是哪個租戶的、冪等鍵是什麼」。
 * 消費者要靠冪等鍵認出重送、靠租戶 id 在正確的 Row Level Security 範圍內讀寫資料，
 * 所以 relay 把這兩個值放進 header，消費者從 header 讀，不用去猜 payload 的形狀。
 */
public final class OutboxKafkaHeaders {

    /** outbox.idempotency_key 原文。 */
    public static final String IDEMPOTENCY_KEY = "tms-idempotency-key";

    /** outbox.tenant_id，十進位字串。 */
    public static final String TENANT_ID = "tms-tenant-id";

    private OutboxKafkaHeaders() {
    }
}
