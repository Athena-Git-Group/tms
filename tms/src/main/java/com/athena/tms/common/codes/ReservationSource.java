package com.athena.tms.common.codes;

import java.util.List;

/** reservations_cache.source（＝V1 CHECK chk_reservations_cache_source）。 */
public final class ReservationSource {

    public static final String PMS = "pms";
    public static final String EXCEL = "excel";

    public static final List<String> ALL = List.of(PMS, EXCEL);

    private ReservationSource() {
    }
}
