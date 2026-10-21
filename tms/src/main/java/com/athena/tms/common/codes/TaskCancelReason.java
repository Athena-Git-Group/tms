package com.athena.tms.common.codes;

import java.util.List;

/** cancelled 事件 payload 的 reason 值（spec §5.1：cancelled payload 原因）。 */
public final class TaskCancelReason {

    public static final String GUEST_WITHDREW = "guest_withdrew";
    public static final String MANAGER_VOID = "manager_void";
    public static final String PLAN_SUPERSEDED = "plan_superseded";

    public static final List<String> ALL = List.of(GUEST_WITHDREW, MANAGER_VOID, PLAN_SUPERSEDED);

    private TaskCancelReason() {
    }
}
