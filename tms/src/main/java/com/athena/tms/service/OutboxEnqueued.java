package com.athena.tms.service;

/**
 * outbox 新增了一筆待送訊息。只由 {@link OutboxWriter} 發出，交易提交後 {@link OutboxRelay} 收到它，叫醒送出的執行緒馬上跑一輪。
 */
public record OutboxEnqueued() {
}
