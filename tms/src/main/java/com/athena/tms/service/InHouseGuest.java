package com.athena.tms.service;

final class InHouseGuest {
    static final String COLUMNS = "rc.guest_name, (rc.attrs ->> 'vip') = 'true' AS vip, rc.checkin_at, rc.checkout_at";
    static final String STAYING_AT = "rc.superseded_at IS NULL AND rc.checkin_at <= ? AND rc.checkout_at > ?";

    private InHouseGuest() {}
}
