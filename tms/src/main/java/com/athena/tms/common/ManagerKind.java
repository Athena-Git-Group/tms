package com.athena.tms.common;

import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.StaffRole;
import java.util.Optional;

public enum ManagerKind {
    HOUSEKEEPING(Dept.HOUSEKEEPING),
    ENGINEERING(Dept.ENGINEERING);

    private final String poolDept;

    ManagerKind(String poolDept) {
        this.poolDept = poolDept;
    }

    public static Optional<ManagerKind> of(String role, String dept) {
        if (!StaffRole.MANAGER.equals(role)) {
            return Optional.empty();
        }
        return Optional.of(Dept.ENGINEERING.equals(dept) ? ENGINEERING : HOUSEKEEPING);
    }

    public static Optional<ManagerKind> of(CurrentActor actor) {
        return of(actor.role(), actor.dept());
    }

    public String poolDept() {
        return poolDept;
    }

    public boolean inspects() {
        return this == HOUSEKEEPING;
    }
}
