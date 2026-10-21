package com.athena.tms.common.codes;

import java.util.List;

/** room_qr_tokens.status（＝V1 CHECK chk_room_qr_tokens_status）。 */
public final class QrTokenStatus {

    public static final String ACTIVE = "active";
    public static final String REVOKED = "revoked";

    public static final List<String> ALL = List.of(ACTIVE, REVOKED);

    private QrTokenStatus() {
    }
}
