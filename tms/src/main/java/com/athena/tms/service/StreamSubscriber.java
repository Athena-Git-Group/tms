package com.athena.tms.service;

import com.athena.tms.common.codes.RolePermissions;
import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.dto.TaskChange;
import java.util.UUID;

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
        if (RolePermissions.canUseFrontdeskWeb(this.role)) {
            return true;
        }
        if (TaskEventType.TRIAGE_UNDONE.equals(change.type()) && this.dept != null && this.dept.equals(change.recalledFromDept())) {
            return true;
        }
        return this.dept != null && this.dept.equals(change.dept()) || this.staffId != null && this.staffId.equals(change.assigneeId());
    }

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
