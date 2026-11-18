package com.athena.tms.common.codes;

import java.util.List;

public final class Dept {
    public static final String HOUSEKEEPING = "housekeeping";
    public static final String ENGINEERING = "engineering";
    public static final String FRONTDESK = "frontdesk";
    public static final List<String> ALL = List.of("housekeeping", "engineering", "frontdesk");

    private Dept() {
    }
}
