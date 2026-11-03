/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.codes;

import java.util.List;

public final class RoomHkState {
    public static final String DIRTY = "dirty";
    public static final String CLEANED = "cleaned";
    public static final String INSPECTED = "inspected";
    public static final String OUT_OF_ORDER = "out_of_order";
    public static final List<String> ALL = List.of("dirty", "cleaned", "inspected", "out_of_order");

    private RoomHkState() {
    }
}
