package com.athena.tms.dto;

import com.athena.tms.common.codes.ActorRole;

import java.util.UUID;

/**
 * 這次狀態轉移是「誰」做的——落入 task_events.actor_role / actor_id。
 * role 值域＝common.codes.ActorRole（guest/system 的 id 為空）。
 */
public record Actor(String role, UUID id) {

    public static Actor guest() {
        return new Actor(ActorRole.GUEST, null);
    }

    public static Actor system() {
        return new Actor(ActorRole.SYSTEM, null);
    }

    public static Actor staff(String role, UUID staffId) {
        return new Actor(role, staffId);
    }
}
