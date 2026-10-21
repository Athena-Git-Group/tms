package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.common.TenantContext;

import java.util.function.Supplier;

/**
 * 讓測試執行緒帶著租戶身分直呼 engine 或 repository 的工具。
 *
 * <p>測試用的資料庫帳號受列級安全（RLS，資料庫依租戶自動過濾每一列）管制。
 * 沒先設定租戶脈絡就查詢，任何資料表都只回零列，而且不報錯，紅燈很難追因。
 *
 * <p>必須先設定租戶脈絡、再進入交易：@Transactional 在交易開始的當下就取得連線，
 * 順序反了，租戶設定就落不到那條連線上。工作結束一定清除脈絡，
 * 避免殘留的租戶身分汙染同一條執行緒上的下一個場景。
 */
public final class Tenants {

    public static <T> T asTenant(long tenantId, Supplier<T> work) {
        try {
            TenantContext.set(tenantId);
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    public static void runAsTenant(long tenantId, Runnable work) {
        asTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    private Tenants() {
    }
}
