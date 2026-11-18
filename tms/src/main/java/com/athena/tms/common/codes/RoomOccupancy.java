package com.athena.tms.common.codes;

import java.util.List;

public final class RoomOccupancy {
    public static final String OCCUPIED = "occupied";
    public static final String VACANT = "vacant";
    public static final List<String> ALL = List.of("occupied", "vacant");

    private RoomOccupancy() {
    }
}
