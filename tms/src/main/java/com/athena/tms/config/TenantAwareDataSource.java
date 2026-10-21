package com.athena.tms.config;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import com.athena.tms.common.TenantContext;

/**
 * 租戶隔離第二道牆（Postgres RLS，列層級安全：由資料庫依當前租戶自動濾掉
 * 看不到的資料列，不靠應用層自律）的接線：每次取連線時把 TenantContext 注入
 * session 變數 `app.tenant_id`，RLS policy 讀 current_setting('app.tenant_id', true)。
 *
 * 為什麼在 DataSource 層而不是 @Transactional AOP：Hibernate 對 resource-local
 * 交易採延遲取連線（第一句 SQL 才拿），在這裡攔截保證「只要這條連線會跑 SQL，
 * 變數一定先就位」，不依賴切面順序。
 *
 * 為什麼用 session 級 SET 而非 SET LOCAL：autocommit 下 SET LOCAL 是 no-op，
 * 而池化連線會跨請求復用——所以污染防治做兩層：歸還（close）時洗掉，
 * 且每次取用時無條件重設，就算歸還那層失手也不會把上個租戶的
 * 變數帶進下個請求。
 *
 * 「無租戶」用哨兵值 '0' 而不是 RESET：自訂 GUC 被 RESET 後值是空字串而非
 * 未設，policy 的 ''::bigint 轉型直接炸 22P02，整條連線後續查詢全變 500；
 * '0' 走得過轉型、又永遠比不中任何租戶（tenants.id 是 IDENTITY 從 1 起跳），
 * 保住 RLS policy 設計時的語意「未注入時查到零列而非報錯」。
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bindTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bindTenant(super.getConnection(username, password));
    }

    /** tenants.id 由 IDENTITY 從 1 起跳，0 永遠比不中＝「查到零列」。 */
    private static final String NO_TENANT_SENTINEL = "0";

    private static Connection bindTenant(Connection connection) throws SQLException {
        Long tenantId = TenantContext.get();
        try (Statement statement = connection.createStatement()) {
            // tenantId 是 Long，不可能注入；字串插值比 set_config 少一次參數綁定
            statement.execute("SET app.tenant_id = '"
                    + (tenantId != null ? tenantId : NO_TENANT_SENTINEL) + "'");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return (Connection) Proxy.newProxyInstance(
                TenantAwareDataSource.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new ResetOnCloseHandler(connection));
    }

    private record ResetOnCloseHandler(Connection target) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName())) {
                try (Statement statement = target.createStatement()) {
                    statement.execute("SET app.tenant_id = '" + NO_TENANT_SENTINEL + "'");
                } catch (SQLException e) {
                    // 洗不掉也要歸還（連線可能已壞、Hikari 會驗活汰換）；
                    // 取用時的無條件 SET 擋住殘值外洩
                }
                target.close();
                return null;
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
