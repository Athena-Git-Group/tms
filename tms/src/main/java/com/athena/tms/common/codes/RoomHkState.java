package com.athena.tms.common.codes;

import java.util.List;

/** rooms.hk_state（TMS 擁有、僅任務引擎可改；＝V1 CHECK chk_rooms_hk_state）。 */
public final class RoomHkState {

    public static final String DIRTY = "dirty";
    public static final String CLEANED = "cleaned";
    public static final String INSPECTED = "inspected";
    public static final String OUT_OF_ORDER = "out_of_order";

    public static final List<String> ALL = List.of(DIRTY, CLEANED, INSPECTED, OUT_OF_ORDER);

    private RoomHkState() {
    }
}
