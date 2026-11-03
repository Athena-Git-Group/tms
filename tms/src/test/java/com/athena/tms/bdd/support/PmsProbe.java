package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.service.AutomationRuleService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 模擬 PMS adapter 的測試入口（對應 dsl.md 的 When 句型
 * 「從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性」）。
 *
 * <p>這個方向的事件（PMS 推訂房進 TMS）設計上不走 HTTP：production 端是直接呼叫
 * {@link AutomationRuleService#applyTodayRoomState}，不是 controller。main 既有的
 * {@code IntegrationEventsController}／{@code IntegrationStreamController} 是反方向——
 * TMS 把房況往外推給 PMS 拉取或訂閱，兩者接不上本句的事件。
 */
public class PmsProbe {

    private final OutboxAwait outboxAwait;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final AutomationRuleService automationRuleService;
    private final ScenarioWorld world;
    private final MutableClock clock;

    public PmsProbe(
            OutboxAwait outboxAwait,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            AutomationRuleService automationRuleService,
            ScenarioWorld world,
            MutableClock clock) {
        this.outboxAwait = outboxAwait;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.automationRuleService = automationRuleService;
        this.world = world;
        this.clock = clock;
    }

    /**
     * 把一筆今日房態交給 production 的自動化規則引擎比對建單，並把建出的準備工單
     * 以房號登記進 {@link ScenarioWorld}——後續 Then 句（「{房號} 的準備工單…」）
     * 都以房號取任務 id，不登記就找不到規則引擎建出的那張單。
     */
    public void pushTodayRoomState(String roomNo, List<String> attrs, LocalDate checkinDate) {
        long tenantId = tenantFixtures.testTenantId();
        automationRuleService.applyTodayRoomState(tenantId, roomNo, attrs, checkinDate);

        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        Optional<Map<String, Object>> created = dbProbe.queryOne(
                """
                SELECT t.id FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.rule_hit IS NOT NULL
                   AND t.created_at >= ? AND t.created_at < ?
                 ORDER BY t.created_at DESC
                 LIMIT 1
                """,
                tenantId, roomNo,
                today.atStartOfDay(clock.getZone()).toOffsetDateTime(),
                today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime());
        created.ifPresent(row -> world.rememberTask(roomNo, String.valueOf(row.get("id"))));
    }

    /**
     * 模擬 PMS adapter 視角查「這間房最後收到的房況回寫」。非同步契約（shared.yaml
     * PmsRoomStatusWriteback）的邊界是 outbox kind={@code pms_status}：回寫消費者原樣把
     * payload 送到 PMS adapter，所以 adapter 會收到什麼＝這裡查到的 payload 內容
     * （roomNo＋status）。查無任何回寫時回傳空 Map——「有沒有回寫」的斷言責任在
     * stepdef，本方法只提供視角，不做斷言。
     */
    public Map<String, Object> lastWritebackFor(String roomNo) {
        return dbProbe
                .queryOne(
                        """
                        SELECT payload ->> 'roomNo' AS room_no, payload ->> 'status' AS status
                          FROM outbox
                         WHERE tenant_id = ? AND kind = 'pms_status' AND payload ->> 'roomNo' = ?
                         ORDER BY id DESC
                         LIMIT 1
                        """,
                        tenantFixtures.testTenantId(), roomNo)
                .orElse(Map.of());
    }
}
