package com.athena.tms.bdd.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 模擬 PMS adapter 的測試入口（對應 dsl.md 的 When 句型
 * 「從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性」）。
 *
 * <p>這個方向的事件（PMS 推訂房進 TMS）設計上不走 HTTP：production 端是直接呼叫
 * 一個 {@code PmsAdapter} 介面，不是 controller。main 既有的
 * {@code IntegrationEventsController}／{@code IntegrationStreamController} 是反方向——
 * TMS 把房況往外推給 PMS 拉取或訂閱，兩者接不上本句的事件。
 *
 * <p>拉訂房方向（PMS 推訂房進 TMS）main 仍然沒有任何入口：{@code reservations_cache}
 * 只被既有 fixture 以直接 SQL 寫入，不經任何 adapter。{@link #pushTodayRoomState}
 * 因此保留簽章但拒絕執行——若改成假裝成功，依賴這句的情境會在沒有任何
 * production 行為的情況下通過，失敗訊號就消失了。
 */
public class PmsProbe {

    private final OutboxAwait outboxAwait;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;

    public PmsProbe(
            OutboxAwait outboxAwait,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures) {
        this.outboxAwait = outboxAwait;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
    }

    /**
     * 固定丟出：沒有 {@code PmsAdapter} 介面可以注入模擬實作，見本類別 class Javadoc。
     */
    public void pushTodayRoomState(String roomNo, List<String> attrs, LocalDate checkinDate) {
        throw new UnsupportedOperationException(
                "main 尚未建立批次拉取今日房態的 PmsAdapter，等 Feature GREEN 建立後接上："
                        + "roomNo=" + roomNo + ", attrs=" + attrs + ", checkinDate=" + checkinDate);
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
