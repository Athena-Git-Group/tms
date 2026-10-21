package com.athena.tms.common.codes;

import java.util.List;

/** 部門（tasks.dept／staff_accounts.dept，＝V1 CHECK；全寫不縮寫，不用 hk/mt）。 */
public final class Dept {

    public static final String HOUSEKEEPING = "housekeeping";
    public static final String ENGINEERING = "engineering";
    public static final String FRONTDESK = "frontdesk";

    public static final List<String> ALL = List.of(HOUSEKEEPING, ENGINEERING, FRONTDESK);

    private Dept() {
    }
}
