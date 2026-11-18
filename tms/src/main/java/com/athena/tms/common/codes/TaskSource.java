package com.athena.tms.common.codes;

import java.util.List;

public final class TaskSource {
    public static final String GUEST = "guest";
    public static final String DESK = "desk";
    public static final String SYSTEM = "system";
    public static final List<String> ALL = List.of("guest", "desk", "system");

    private TaskSource() {
    }
}
