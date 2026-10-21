package com.athena.tms.common.codes;

import java.util.List;

/** rooms.occupancy（PMS 擁有、同步唯讀；＝V1 CHECK chk_rooms_occupancy）。 */
public final class RoomOccupancy {

    public static final String OCCUPIED = "occupied";
    public static final String VACANT = "vacant";

    public static final List<String> ALL = List.of(OCCUPIED, VACANT);

    private RoomOccupancy() {
    }
}
