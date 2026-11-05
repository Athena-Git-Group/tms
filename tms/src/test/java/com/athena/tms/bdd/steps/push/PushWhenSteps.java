package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.當;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 通知中心句型的 When（dsl.md 通知中心段落）。只發動作、記錄回應，不含任何斷言——
 * 「必查」清單在 {@link PushThenSteps}。本類別只放通知中心的句型。
 */
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

    /**
     * dsl.md 怎麼做：以該員工身分查詢通知列表。待 /api-plan：目前沒有
     * GET /notifications，本句型假設該端點依時間新到舊回傳每筆的標題、送達時間
     * 與已讀狀態。回寫：通知清單（存進 {@link ScenarioWorld#setLastResponse}，由
     * apiClient.get 自動處理）。
     */
    @當("{string} 查看通知中心")
    public void staffViewsNotificationCenter(String staffName) {
        apiClient.get("/notifications", BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    /**
     * dsl.md 怎麼做：以該員工身分標記該筆通知已讀，呼叫 {@code markNotificationRead}
     * （{@code POST /notifications/{id}/read}）。權威狀態落地：該筆通知的 {@code read_at}
     * 已寫入。
     *
     * <p>dsl.md 這句只給標題，沒有另外給通知 id；先以該員工、該標題在
     * {@code notifications} 表查出最新一筆的 id，再呼叫端點標記已讀。
     */
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

    /**
     * dsl.md 怎麼做：以該員工身分呼叫批次已讀端點
     * （contracts/groups/知識與通知/通知中心.yaml 的 {@code markAllNotificationsRead}，
     * {@code POST /notifications/read-all}）。權威狀態落地：該員工名下每一則通知的
     * {@code read_at} 都已寫入目前時間；已經是已讀的不受影響。
     *
     * <p>dsl.md 殘留「待 /api-plan 補」字樣已過時——此端點已在 contracts 定案，
     * 不需要另外查表取通知 id，直接呼叫端點即可。
     */
    @當("{string} 將所有通知標為已讀")
    public void staffMarksAllNotificationsRead(String staffName) {
        apiClient.post("/notifications/read-all", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }
}
