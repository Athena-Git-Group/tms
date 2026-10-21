package com.athena.tms.auth;

import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.entity.Room;
import com.athena.tms.dao.RoomRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;

/**
 * 矩陣逐格測試需要「每個前綴×讀寫」都有真端點：Security 的授權判定發生在
 * dispatch 之前，拿不存在的路徑打，過了授權也只會 404，分不清是矩陣放行
 * 還是根本沒走到。為了不讓矩陣測試依賴各業務模組的真端點，這裡用
 * 探針端點代位，只進測試 context、不進正式程式。
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthTestEndpointsConfiguration {

    // 不另寫 @Bean 方法：@Import 本設定類時，巢狀 @RestController 會被
    // member-class 處理註冊一次，再加 @Bean 就是同一 handler 映射兩次（Ambiguous mapping）
    @RestController
    static class ProbeController {

        private final RoomRepository roomRepository;
        private final JdbcTemplate jdbcTemplate;

        ProbeController(RoomRepository roomRepository, DataSource dataSource) {
            this.roomRepository = roomRepository;
            // 手組而非注入 JdbcTemplate bean：主 pom 沒拉 Boot 的 jdbc 自動設定模組，
            // 這裡不想讓探針依賴它存在與否
            this.jdbcTemplate = new JdbcTemplate(dataSource);
        }

        @GetMapping("/staff/probe")
        Map<String, String> staffRead() {
            return Map.of("probe", "staff-read");
        }

        @PostMapping("/staff/probe")
        Map<String, String> staffWrite() {
            return Map.of("probe", "staff-write");
        }

        @GetMapping("/desk/probe")
        Map<String, String> deskRead() {
            return Map.of("probe", "desk-read");
        }

        @PostMapping("/desk/probe")
        Map<String, String> deskWrite() {
            return Map.of("probe", "desk-write");
        }

        @GetMapping("/admin/probe")
        Map<String, String> adminRead() {
            return Map.of("probe", "admin-read");
        }

        @PostMapping("/admin/probe")
        Map<String, String> adminWrite() {
            return Map.of("probe", "admin-write");
        }

        @GetMapping("/owner/probe")
        Map<String, String> ownerRead() {
            return Map.of("probe", "owner-read");
        }

        @PostMapping("/owner/probe")
        Map<String, String> ownerWrite() {
            return Map.of("probe", "owner-write");
        }

        @PostMapping("/guest/probe")
        Map<String, String> guestWrite() {
            return Map.of("probe", "guest-write");
        }

        /** 驗收「session 驗過即綁定」：房與訂單必須已在請求上下文裡，下游取用不再查 DB。 */
        @GetMapping("/guest/context")
        Map<String, Object> guestContext() {
            CurrentActor actor = CurrentActor.require();
            return Map.of(
                    "tenantId", actor.tenantId(),
                    "roomId", String.valueOf(actor.roomId()),
                    "reservationId", String.valueOf(actor.reservationId()));
        }

        /** 驗收跨租戶語意：tenant-scoped 查無 → NotFoundException（404，不是 403）。 */
        @GetMapping("/staff/rooms/{id}")
        Map<String, String> room(@PathVariable UUID id) {
            Room room = roomRepository.findByTenantIdAndId(TenantContext.require(), id)
                    .orElseThrow(() -> new NotFoundException("房間不存在"));
            return Map.of("id", room.getId().toString(), "roomNo", room.getRoomNo());
        }

        /**
         * 租戶隔離第二道牆（RLS，由資料庫自動濾掉別租戶資料列）的探針：
         * SQL 故意不帶 WHERE tenant_id——應用層 scope
         * 全繞開，可見列數仍只剩本租戶，才證明 RLS＋session 變數注入真的在擋。
         */
        @GetMapping("/staff/rls-room-count")
        Map<String, Object> rlsRoomCount() {
            Integer visible = jdbcTemplate.queryForObject("SELECT count(*) FROM rooms", Integer.class);
            return Map.of("visibleRooms", visible);
        }
    }
}
