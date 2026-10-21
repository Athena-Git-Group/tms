package com.athena.tms.common.codes;

import java.util.List;

/**
 * outbox.kind。這裡列的是程式會產生的 kind，為 V1 CHECK chk_outbox_kind 的子集：
 * 已退場的 kind（例如房況回寫）只從程式移除，CHECK 不跟著收緊，因為改 CHECK
 * 要鎖表、而多留一個值無害。minibar 啟用時再加 pms_minibar。
 */
public final class OutboxKind {

    public static final String TASK_EVENT = "task_event";
    public static final String PUSH = "push";

    public static final List<String> ALL = List.of(TASK_EVENT, PUSH);

    private OutboxKind() {
    }
}
