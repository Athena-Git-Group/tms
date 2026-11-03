/*
 * Decompiled with CFR 0.152.
 */
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
            throw new IllegalStateException("CurrentActor \u672a\u5efa\u7acb\u2014\u2014\u6b64\u8def\u5f91\u4e0d\u8a72\u5728\u8a8d\u8b49\u4e4b\u524d\u53d6\u64cd\u4f5c\u8005");
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
