package com.athena.tms.common;

import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.StaffRole;
import java.util.Optional;

/**
 * 主管的種類：房務主管或工務主管。主管待辦要回哪幾類清單、誰可以查房，都由這裡判定。
 *
 * <p>種類由員工的 role 與 dept 當場推得，不另存欄位：role 是 manager 且 dept 是 engineering
 * 的是工務主管，其餘主管（含 dept 為空）都是房務主管。另存一個欄位就得在改部門時同步改，
 * 兩邊不一致時主管首頁畫出的格子會和後端回的類別對不上。App 用同一條規則畫格子，
 * 所以這條規則改了兩邊要一起改。
 */
public enum ManagerKind {
    HOUSEKEEPING,
    ENGINEERING;

    /** 不是主管時回空，由呼叫端決定要回 403 還是略過。 */
    public static Optional<ManagerKind> of(String role, String dept) {
        if (!StaffRole.MANAGER.equals(role)) {
            return Optional.empty();
        }
        return Optional.of(Dept.ENGINEERING.equals(dept) ? ENGINEERING : HOUSEKEEPING);
    }

    public static Optional<ManagerKind> of(CurrentActor actor) {
        return of(actor.role(), actor.dept());
    }

    /** 只有房務主管查房，也只有房務主管的待辦有待查房這一類。 */
    public boolean inspects() {
        return this == HOUSEKEEPING;
    }
}
