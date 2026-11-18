package com.athena.tms.common;

import java.util.UUID;

public record CurrentActor(Kind kind, Long tenantId, UUID staffId, String role, String dept, UUID guestSessionId, UUID roomId, UUID reservationId) {
    private static final ThreadLocal<CurrentActor> HOLDER = new ThreadLocal();

    public static CurrentActor staff(Long tenantId, UUID staffId, String role, String dept) {
        return new CurrentActor(Kind.STAFF, tenantId, staffId, role, dept, null, null, null);
    }

    public static CurrentActor guest(Long tenantId, UUID guestSessionId, UUID roomId, UUID reservationId) {
        return new CurrentActor(Kind.GUEST, tenantId, null, null, null, guestSessionId, roomId, reservationId);
    }

    public static void set(CurrentActor actor) {
        HOLDER.set(actor);
    }

    public static CurrentActor get() {
        return HOLDER.get();
    }

    public static CurrentActor require() {
        CurrentActor actor = HOLDER.get();
        if (actor == null) {
            throw new IllegalStateException("CurrentActor 未建立——此路徑不該在認證之前取操作者");
        }
        return actor;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static enum Kind {
        STAFF,
        GUEST;

    }
}
