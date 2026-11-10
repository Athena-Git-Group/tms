package com.athena.tms.config;

import com.athena.tms.common.TenantContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class TenantAwareDataSource
extends DelegatingDataSource {
    private static final String NO_TENANT_SENTINEL = "0";

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    public Connection getConnection() throws SQLException {
        return TenantAwareDataSource.bindTenant(super.getConnection());
    }

    public Connection getConnection(String username, String password) throws SQLException {
        return TenantAwareDataSource.bindTenant(super.getConnection(username, password));
    }

    private static Connection bindTenant(Connection connection) throws SQLException {
        Long tenantId = TenantContext.get();
        try (Statement statement = connection.createStatement();){
            statement.execute("SET app.tenant_id = '" + String.valueOf(tenantId != null ? tenantId : NO_TENANT_SENTINEL) + "'");
        }
        catch (SQLException e) {
            connection.close();
            throw e;
        }
        return (Connection)Proxy.newProxyInstance(TenantAwareDataSource.class.getClassLoader(), new Class[]{Connection.class}, (InvocationHandler)new ResetOnCloseHandler(connection));
    }

    private record ResetOnCloseHandler(Connection target) implements InvocationHandler
    {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName())) {
                try (Statement statement2 = this.target.createStatement();){
                    statement2.execute("SET app.tenant_id = '0'");
                }
                catch (SQLException statement2) {
                }
                this.target.close();
                return null;
            }
            try {
                return method.invoke(this.target, args);
            }
            catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
