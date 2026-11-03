package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import com.athena.tms.service.JwtService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 員工帳號測試資料入口（{@code staff_accounts}，欄位依 data-model.dbml {@code Table staff_accounts}）。
 *
 * <p>兩種形狀：現場部門員工（房務／工務／櫃檯）role 與 dept 剛好同值；主管／值班經理
 * 這類不固定部門的角色 dept 必須是 NULL（見 {@link #createFieldStaff}／{@link #createManagerLikeStaff}）。
 * 值班經理對應的權威角色代碼 {@code duty_manager}（見 dsl.md 部門用語對照）目前不在
 * {@code staff_accounts.role} 的 CHECK 值域內（V1 schema 只有 frontdesk|housekeeping|
 * engineering|manager|owner）——這是 schema 落後 dsl.md 的已知缺口，呼叫
 * {@link #createManagerLikeStaff} 建值班經理時 INSERT 會違反 CHECK 直接噴錯，
 * 在 Feature GREEN 補上 schema 前，這是合法的 RED 訊號，不是本類別要蒙混過去的事。
 *
 * <p>班次與負責樓層查 {@code plan_rooms}（V4 已補建）：只有現場部門員工才有「負責樓層／班次」
 * 這個概念，這裡直接幫這位員工建一筆最小可用的 plan_rooms 列——帶一張 draft 狀態的
 * cleaning_plans（當日、這個部門常見的 shift 名稱）與一間用 floor 當樓層新建的房間，
 * est_min／route_seq 給固定預留值，因為它們對「查得到樓層」這個情境本身無關緊要；
 * plan_rooms.task_id 保持 NULL（發佈後才回填，不在這個 fixture 的職責內）。
 */
public class StaffFixtures {

    /** 固定明文密碼，僅供測試環境；比照舊 integration 測試（TestData.PASSWORD）的慣例。 */
    public static final String DEFAULT_PASSWORD = "demo1234";

    /** dsl.md「飯店有以下員工：」列的「班次結束」預設值。 */
    public static final String DEFAULT_SHIFT_END = "17:00";

    private static final Set<String> SUPPORTED_DEPTS = Set.of("housekeeping", "engineering", "frontdesk");

    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public StaffFixtures(
            JdbcClient jdbcClient,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** 現場部門員工（房務／工務／櫃檯）：role 與 dept 同值，支援負責樓層／班次結束。 */
    public CreatedStaff createFieldStaff(
            String name, String deptCode, Integer floor, String shiftEndHHmm, String stateCode) {
        if (!SUPPORTED_DEPTS.contains(deptCode)) {
            throw new IllegalArgumentException(
                    "createFieldStaff 只支援現場部門代碼 housekeeping|engineering|frontdesk，收到: " + deptCode);
        }
        String shiftEnd = shiftEndHHmm != null ? shiftEndHHmm : DEFAULT_SHIFT_END;
        CreatedStaff created = insertStaff(name, deptCode, deptCode, stateCode);
        assignPlanRoom(created.tenantId(), created.id(), floor, shiftEnd, OffsetDateTime.now());
        return created;
    }

    /**
     * 主管／值班經理：dept 固定 NULL，沒有負責樓層／班次的概念。{@code roleCode} 直接收
     * 權威角色代碼（manager|duty_manager，見 {@link Terms#role}），不在這裡做值域檢查——
     * 值班經理目前會在 INSERT 撞上 DB 的 CHECK（見本類別 class Javadoc），讓它自然噴錯。
     */
    public CreatedStaff createManagerLikeStaff(String name, String roleCode, String stateCode) {
        return insertStaff(name, roleCode, null, stateCode);
    }

    /** 停用員工（dsl.md「"{員工}" 已停用」）：沒有對應的 admin API，直接落地權威狀態。 */
    public void deactivate(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql("UPDATE staff_accounts SET active = false, updated_at = ? WHERE tenant_id = ? AND id = ?")
                .params(OffsetDateTime.now(), tenantId, staffId)
                .update();
    }

    private CreatedStaff insertStaff(String name, String roleCode, String deptCode, String stateCode) {
        boolean active = "active".equals(stateCode);
        requireKnownState(stateCode);

        long tenantId = tenantFixtures.testTenantId();
        UUID id = UuidV7.generate();
        String loginId = "fx-" + id.toString().replace("-", "").substring(0, 12);
        String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);
        OffsetDateTime now = OffsetDateTime.now();

        jdbcClient
                .sql(
                        """
                        INSERT INTO staff_accounts
                            (id, tenant_id, name, login_id, password_hash, role, dept, active, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, name, loginId, passwordHash, roleCode, deptCode, active, stateCode, now, now)
                .update();

        // 直接簽發 JWT 而不經過 /auth/login：登入本身是待測行為之一，
        // fixture 不應該依賴它才能把測試資料準備好。
        JwtService.IssuedToken issued = jwtService.issue(tenantId, id, roleCode, deptCode);
        return new CreatedStaff(tenantId, id, loginId, DEFAULT_PASSWORD, issued.token());
    }

    /**
     * 幫這位員工掛一筆最小可用的 {@code plan_rooms} 列，讓「查這位員工負責哪層樓／哪個班次」
     * 查得到資料。room_id 用專屬的佔位房號（樓層資訊以外，房況與房型對這個情境無關緊要，
     * 不借用情境自己建立的房間，避免兩邊互相汙染）；shift 直接存呼叫端給的 {@code HH:mm}，
     * 沒給時用固定佔位值；est_min／route_seq 是 plan_rooms 的 NOT NULL 欄位但在「查樓層／班次」
     * 這個情境本身無關緊要，給固定預留值。plan_rooms.task_id 保持 NULL：發佈後才回填，
     * 不在這個 fixture 的職責內。
     */
    private void assignPlanRoom(long tenantId, UUID staffId, Integer floor, String shiftEndHHmm, OffsetDateTime now) {
        int effectiveFloor = floor != null ? floor : 0;
        String shift = shiftEndHHmm != null ? shiftEndHHmm : "default";
        String placeholderRoomNo = "PLANROOM-F" + effectiveFloor;
        UUID roomId = roomFixtures.ensureRoom(placeholderRoomNo, effectiveFloor);

        UUID planId = UuidV7.generate();
        LocalDate workDate = now.toLocalDate();
        jdbcClient
                .sql(
                        """
                        INSERT INTO cleaning_plans
                            (id, tenant_id, work_date, shift, status, version, generated_by, algo_version, generated_at)
                        VALUES (?, ?, ?, ?, 'draft', 0, 'system', 'v1', ?)
                        """)
                .params(planId, tenantId, workDate, shift, now)
                .update();

        UUID planRoomId = UuidV7.generate();
        jdbcClient
                .sql(
                        """
                        INSERT INTO plan_rooms (id, tenant_id, plan_id, room_id, staff_id, est_min, route_seq)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(planRoomId, tenantId, planId, roomId, staffId, 30, 1)
                .update();
    }

    /**
     * 員工狀態三態寫進 staff_accounts.status（V10 新增欄位）。active 這個 boolean
     * 是「可登入、可被挑人、收推播」的衍生旗標，只有在職（active）為 true；
     * 待開通（invited）與停用（inactive）都還不能動作，所以同為 false。
     */
    private static void requireKnownState(String stateCode) {
        if (!Set.of("active", "invited", "inactive").contains(stateCode)) {
            throw new IllegalArgumentException("未知的員工狀態代碼: " + stateCode);
        }
    }

    public record CreatedStaff(long tenantId, UUID id, String loginId, String password, String jwt) {}
}
