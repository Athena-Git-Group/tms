package com.athena.tms.common.codes;

import java.util.List;

public final class ReservationSource {
    public static final String PMS = "pms";
    public static final String EXCEL = "excel";
    public static final List<String> ALL = List.of("pms", "excel");

    private ReservationSource() {
    }
}
