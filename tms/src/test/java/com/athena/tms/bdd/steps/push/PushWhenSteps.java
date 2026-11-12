package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.當;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PushWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final TenantFixtures tenantFixtures;
    private final JdbcClient jdbcClient;

    public PushWhenSteps(
            ScenarioWorld world, BddApiClient apiClient, TenantFixtures tenantFixtures, JdbcClient jdbcClient) {
        this.world = world;
        this.apiClient = apiClient;
        this.tenantFixtures = tenantFixtures;
        this.jdbcClient = jdbcClient;
    }

    @當("{string} 查看通知中心")
    public void staffViewsNotificationCenter(String staffName) {
        apiClient.get("/notifications", BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("{string} 將 {string} 的通知標為已讀")
    public void staffMarksNotificationRead(String staffName, String title) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        long tenantId = tenantFixtures.testTenantId();

        List<Map<String, Object>> rows = jdbcClient
                .sql(
                        "SELECT id FROM notifications WHERE tenant_id = ? AND recipient_staff_id = ? AND title = ? ORDER BY created_at DESC")
                .params(tenantId, staffId, title)
                .query()
                .listOfRows();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(staffName + " 名下找不到標題「" + title + "」的通知");
        }
        Object notificationId = rows.get(0).get("id");

        apiClient.post(
                "/notifications/" + notificationId + "/read", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 將所有通知標為已讀")
    public void staffMarksAllNotificationsRead(String staffName) {
        apiClient.post("/notifications/read-all", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }
}
