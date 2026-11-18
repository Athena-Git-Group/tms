package com.athena.tms.common.codes;

import java.util.List;

public final class OutboxStatus {
    public static final String PENDING = "pending";
    public static final String SENT = "sent";
    public static final String FAILED = "failed";
    public static final List<String> ALL = List.of("pending", "sent", "failed");

    private OutboxStatus() {
    }
}
