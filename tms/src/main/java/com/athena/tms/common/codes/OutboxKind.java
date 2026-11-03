/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.codes;

import java.util.List;

public final class OutboxKind {
    public static final String TASK_EVENT = "task_event";
    public static final String PUSH = "push";
    public static final List<String> ALL = List.of("task_event", "push");

    private OutboxKind() {
    }
}
