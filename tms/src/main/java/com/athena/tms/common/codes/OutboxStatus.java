package com.athena.tms.common.codes;

import java.util.List;

/** outbox.status（＝V1 CHECK chk_outbox_status；sent 3 天清、failed 等人工）。 */
public final class OutboxStatus {

    public static final String PENDING = "pending";
    public static final String SENT = "sent";
    public static final String FAILED = "failed";

    public static final List<String> ALL = List.of(PENDING, SENT, FAILED);

    private OutboxStatus() {
    }
}
