package com.athena.tms.service;

import com.athena.tms.entity.Tenant;
import com.athena.tms.dao.TenantRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 背景工作（relay、保留期清理、冪等簿）沒有 HTTP 請求脈絡，認證層的
 * per-request 租戶注入幫不到它們；但 outbox/processed_ops 有 RLS，tms_app
 * 連線不帶 app.tenant_id 時查詢回零列、寫入直接被 policy 拒絕，而且
 * 不會報錯。本類把「開交易＋注入租戶變數」收成一處。
 *
 * <p>用 set_config(..., true)（交易範圍）而不是 SET SESSION：連線來自池，
 * session 級設定會洩漏到下一個借走連線的請求，讓它看到別租戶的資料。
 */
@Component
public class TenantScopedExecutor {

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TenantScopedExecutor(TenantRepository tenantRepository,
                                JdbcTemplate jdbcTemplate,
                                PlatformTransactionManager transactionManager) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 在「已注入 app.tenant_id 的新交易」內執行並回傳結果。 */
    public <T> T callAsTenant(long tenantId, Supplier<T> work) {
        return transactionTemplate.execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class, String.valueOf(tenantId));
            return work.get();
        });
    }

    public void runAsTenant(long tenantId, Runnable work) {
        callAsTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /** 逐租戶輪一遍（tenants 是平台層主檔、無 RLS，tms_app 可讀全表）。 */
    public void forEachTenant(LongConsumer work) {
        for (Tenant tenant : tenantRepository.findAll()) {
            runAsTenant(tenant.getId(), () -> work.accept(tenant.getId()));
        }
    }

    /**
     * 只列出租戶 id，不開交易。給 OutboxRelay 這種「每個租戶內部要自己切很多段
     * 短交易」的呼叫者用——如果借用 {@link #forEachTenant}，work 整段（包含裡面
     * 每一段短交易）會被 forEachTenant 自己那層 runAsTenant 捲進同一個外層交易，
     * 短交易就變回長交易，等於沒修。
     */
    public List<Long> tenantIds() {
        return tenantRepository.findAll().stream().map(Tenant::getId).toList();
    }
}
