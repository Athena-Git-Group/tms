package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 房間測試資料入口（{@code rooms}，欄位依 data-model.dbml {@code Table rooms}）。
 */
public class RoomFixtures {

    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;

    public RoomFixtures(JdbcClient jdbcClient, TenantFixtures tenantFixtures) {
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
    }

    /**
     * 確保房間存在，回傳 rooms.id。樓層由呼叫端直接給值——「樓層＝房號首位」只是
     * 多數情境下的巧合，不是資料模型的規則，寫死在 fixture 裡會讓用非典型房號
     * （地下室、公共區）的情境無從表達。
     *
     * <p>room_type／hk_state／occupancy 不開放參數：這三欄的初始值對大多數情境無關緊要，
     * 需要特定房況的情境應該透過任務引擎跑出對應狀態，而不是在 fixture 直接竄改
     * hk_state——hk_state 的權威來源是任務引擎，繞過它會讓房況跟任務歷程對不上。
     */
    public UUID ensureRoom(String roomNo, int floor) {
        long tenantId = tenantFixtures.testTenantId();
        Optional<UUID> existing = jdbcClient
                .sql("SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?")
                .params(tenantId, roomNo)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient
                .sql(
                        """
                        INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, roomNo, floor, "standard_double", "dirty", "vacant", now)
                .update();
        return id;
    }

    /**
     * 由房號推導樓層：取房號第一位數字，對應 dsl.md 多處 Data Table 的「樓層」預設值
     * （「房號第一位數字」）。房號不是以數字開頭時（例如公共區域的佔位房號）回傳 0。
     */
    public static int floorFromRoomNo(String roomNo) {
        if (roomNo == null || roomNo.isEmpty() || !Character.isDigit(roomNo.charAt(0))) {
            return 0;
        }
        return Character.getNumericValue(roomNo.charAt(0));
    }

    /** 查已建立房間的 id；找不到就直接噴錯，不要讓呼叫端拿到 null 去撞外鍵。 */
    public UUID roomId(String roomNo) {
        long tenantId = tenantFixtures.testTenantId();
        return jdbcClient
                .sql("SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?")
                .params(tenantId, roomNo)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "房號「" + roomNo + "」尚未建立，請先呼叫 ensureRoom()"));
    }

    /**
     * 在 {@code room_memory}（V4 已補建）插入一筆往事。{@code title} 目前沒有對應欄位可落地
     * ——room_memory 只有單一 {@code summary} 文字欄——所以併進 summary，格式
     * {@code "{title}：{content}"}；title 為空則 summary 就是 content 本身。
     * {@code source_task_id} 固定留空：這個 fixture 入口不預設往事一定來自某張任務，
     * 需要「追溯來源任務」的情境應該直接呼叫 TaskFixtures 建好任務後再用 jdbcClient 補寫。
     */
    public UUID addRoomMemory(String roomNo, String kind, String title, String content, LocalDate occurredOn) {
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomId(roomNo);
        UUID id = UuidV7.generate();
        String summary = (title == null || title.isBlank()) ? content : title + "：" + content;
        OffsetDateTime occurredAt = occurredOn.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        jdbcClient
                .sql(
                        """
                        INSERT INTO room_memory (id, tenant_id, room_id, kind, summary, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, roomId, kind, summary, occurredAt)
                .update();
        return id;
    }
}
