/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.codes;

import java.util.List;

public final class TaskCancelReason {
    public static final String GUEST_WITHDREW = "guest_withdrew";
    public static final String MANAGER_VOID = "manager_void";
    public static final String PLAN_SUPERSEDED = "plan_superseded";
    public static final List<String> ALL = List.of("guest_withdrew", "manager_void", "plan_superseded");

    private TaskCancelReason() {
    }
}
