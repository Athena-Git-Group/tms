package com.athena.tms.common.codes;

import java.util.List;

/** task_events.actor_role（＝V1 CHECK chk_task_events_actor_role；比員工 role 多 guest/system）。 */
public final class ActorRole {

    public static final String GUEST = "guest";
    public static final String FRONTDESK = "frontdesk";
    public static final String HOUSEKEEPING = "housekeeping";
    public static final String ENGINEERING = "engineering";
    public static final String MANAGER = "manager";
    public static final String SYSTEM = "system";

    public static final List<String> ALL = List.of(GUEST, FRONTDESK, HOUSEKEEPING, ENGINEERING, MANAGER, SYSTEM);

    private ActorRole() {
    }
}
