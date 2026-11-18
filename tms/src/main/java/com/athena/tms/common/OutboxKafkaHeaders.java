package com.athena.tms.common;

public final class OutboxKafkaHeaders {

    public static final String IDEMPOTENCY_KEY = "tms-idempotency-key";

    public static final String TENANT_ID = "tms-tenant-id";

    private OutboxKafkaHeaders() {
    }
}
