package com.athena.tms.common;

import java.util.UUID;

/**
 * 當前請求的操作者（員工或住客），auth filter 驗證通過後寫入。
 * 員工帶 staffId/role/dept；住客帶 guestSessionId＋驗證當下綁定的房與訂單
 * （session 驗過即把房＋訂單綁進請求上下文，下游不必再查）。
 * 其他模組只消費 get()/require()，不自行 set。
 */
public record CurrentActor(
        Kind kind,
        Long tenantId,
        UUID staffId,
        String role,
        String dept,
        UUID guestSessionId,
        UUID roomId,
        UUID reservationId) {

    public enum Kind { STAFF, GUEST }

    private static final ThreadLocal<CurrentActor> HOLDER = new ThreadLocal<>();

    public static CurrentActor staff(Long tenantId, UUID staffId, String role, String dept) {
        return new CurrentActor(Kind.STAFF, tenantId, staffId, role, dept, null, null, null);
    }

    public static CurrentActor guest(Long tenantId, UUID guestSessionId, UUID roomId, UUID reservationId) {
        return new CurrentActor(Kind.GUEST, tenantId, null, null, null, guestSessionId, roomId, reservationId);
    }

    public static void set(CurrentActor actor) {
        HOLDER.set(actor);
    }

    /** @return 當前操作者；未認證路徑（如 /auth/login）為 null。 */
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
}
