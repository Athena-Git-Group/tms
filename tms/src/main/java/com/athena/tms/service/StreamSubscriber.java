package com.athena.tms.service;

import com.athena.tms.common.codes.RolePermissions;
import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.dto.TaskChange;
import java.util.UUID;

/**
 * 一條串流連線是誰在看：哪家飯店、住客還是員工、員工的角色與部門。只靠這些資料決定一筆單的變化要不要送出、
 * 連上之後建議重抓哪裡，不碰資料庫，也不碰連線本身。
 *
 * <p>住客連線只有 {@code reservationId}；員工連線有 {@code staffId}、{@code role}、{@code dept}；
 * 對接系統的連線只認飯店。沒有的欄位是 null。
 */
record StreamSubscriber(Audience audience, long tenantId, UUID reservationId, UUID staffId, String role, String dept) {

    static StreamSubscriber guest(long tenantId, UUID reservationId) {
        return new StreamSubscriber(Audience.GUEST, tenantId, reservationId, null, null, null);
    }

    static StreamSubscriber staff(long tenantId, UUID staffId, String role, String dept) {
        return new StreamSubscriber(Audience.STAFF, tenantId, null, staffId, role, dept);
    }

    static StreamSubscriber integration(long tenantId) {
        return new StreamSubscriber(Audience.INTEGRATION, tenantId, null, null, null, null);
    }

    /**
     * 這筆單的變化要不要送給這條連線。別家飯店的一律不送；住客只收自己這筆訂房的單；
     * 員工照角色與部門判斷；對接系統的連線不收單的變化。
     */
    boolean wants(TaskChange change) {
        if (change.tenantId() != this.tenantId) {
            return false;
        }
        return switch (this.audience) {
            case GUEST -> this.reservationId != null && this.reservationId.equals(change.reservationId());
            case STAFF -> this.staffWants(change);
            case INTEGRATION -> false;
        };
    }

    private boolean staffWants(TaskChange change) {
        // 櫃檯、主管、值班主管要看全館的看板，同一家飯店每一張單的事件都收。
        if (RolePermissions.canUseFrontdeskWeb(this.role)) {
            return true;
        }
        // 復原之後單已經回到櫃檯，被收回的部門要靠這筆訊號把單從自己的待認領拿掉。
        if (TaskEventType.TRIAGE_UNDONE.equals(change.type()) && this.dept != null && this.dept.equals(change.recalledFromDept())) {
            return true;
        }
        return this.dept != null && this.dept.equals(change.dept()) || this.staffId != null && this.staffId.equals(change.assigneeId());
    }

    /**
     * 這條連線的 {@code task} 訊號要不要帶櫃檯看板上的卡片。只有用櫃檯畫面的員工角色帶：
     * 卡片是櫃檯視角，含住客姓名與 VIP，房務與工務本來就讀不到櫃檯看板，串流不該從旁邊送出同樣的內容。
     */
    boolean seesDeskBoard() {
        return this.audience == Audience.STAFF && RolePermissions.canUseFrontdeskWeb(this.role);
    }

    // 連上之後建議客戶端重抓的資料：住客重抓首頁，用櫃檯畫面的角色重抓櫃檯看板，房務與工務重抓部門待認領。
    String refetchPath() {
        if (this.audience == Audience.GUEST) {
            return "/guest/home";
        }
        if (RolePermissions.canUseFrontdeskWeb(this.role)) {
            return "/api/v1/desk/board";
        }
        return "/api/v1/pool";
    }

    enum Audience {
        GUEST,
        STAFF,
        INTEGRATION;
    }
}
