/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.athena.tms.dto.Actor
 */
package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.dto.Actor;
import java.util.UUID;

public final class TestData {
    public static final long TENANT_1 = 1L;
    public static final long TENANT_2 = 2L;
    public static final UUID ROOM_301 = UUID.fromString("00000000-0000-7000-8000-000000000301");
    public static final UUID ROOM_305 = UUID.fromString("00000000-0000-7000-8000-000000000305");
    public static final UUID ROOM_502 = UUID.fromString("00000000-0000-7000-8000-000000000502");
    public static final UUID STAFF_HK1 = UUID.fromString("00000000-0000-7000-8000-00000000a001");
    public static final UUID STAFF_MG1 = UUID.fromString("00000000-0000-7000-8000-00000000a003");
    public static final UUID STAFF_FD1 = UUID.fromString("00000000-0000-7000-8000-00000000a004");
    public static final UUID RESERVATION_B001 = UUID.fromString("00000000-0000-7000-8000-00000000b001");
    public static final UUID RESERVATION_B002 = UUID.fromString("00000000-0000-7000-8000-00000000b002");
    public static final String PASSWORD = "demo1234";
    public static final String SEED_BCRYPT = "$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW";
    public static final Actor HK1 = Actor.staff((String)"housekeeping", (UUID)STAFF_HK1);
    public static final Actor MG1 = Actor.staff((String)"manager", (UUID)STAFF_MG1);
    public static final Actor FD1 = Actor.staff((String)"frontdesk", (UUID)STAFF_FD1);

    private TestData() {
    }
}
