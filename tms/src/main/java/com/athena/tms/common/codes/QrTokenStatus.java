package com.athena.tms.common.codes;

import java.util.List;

public final class QrTokenStatus {
    public static final String ACTIVE = "active";
    public static final String REVOKED = "revoked";
    public static final List<String> ALL = List.of("active", "revoked");

    private QrTokenStatus() {
    }
}
