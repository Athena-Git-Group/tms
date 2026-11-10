package com.athena.tms.common.codes;

import java.util.List;

public final class ActorRole {
    public static final String GUEST = "guest";
    public static final String FRONTDESK = "frontdesk";
    public static final String HOUSEKEEPING = "housekeeping";
    public static final String ENGINEERING = "engineering";
    public static final String MANAGER = "manager";
    public static final String SYSTEM = "system";
    public static final List<String> ALL = List.of("guest", "frontdesk", "housekeeping", "engineering", "manager", "system");

    private ActorRole() {
    }
}
