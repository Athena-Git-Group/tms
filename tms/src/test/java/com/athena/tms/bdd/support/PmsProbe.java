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
 * <p>目前 main 完全沒有這個介面、也沒有 PMS 回寫消費者的資料落地：
 * {@code reservations_cache} 只被既有 fixture 以直接 SQL 寫入，不經任何 adapter；
 * outbox 的 {@code pms_status} kind 已在 V3 migration 清空且不再產生。本類別兩個方法
 * 先保留簽章讓 stepdef 及早串接，呼叫會直接失敗，等 Feature GREEN 把對應的
 * production 介面與回寫落地建起來後再補上真正的呼叫與 {@link OutboxAwait} 輪詢。
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
     * 固定丟出：沒有 PMS 回寫消費者落地資料，見本類別 class Javadoc。
     */
    public Map<String, Object> lastWritebackFor(String roomNo) {
        throw new UnsupportedOperationException(
                "main 尚未建立 PMS 回寫消費者的資料落地，等 Feature GREEN 建立後接上：roomNo=" + roomNo);
    }
}
