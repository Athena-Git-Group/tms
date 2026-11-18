package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import com.athena.tms.service.JwtService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

public class StaffFixtures {

    public static final String DEFAULT_PASSWORD = "demo1234";

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

    public CreatedStaff createManagerLikeStaff(String name, String roleCode, String stateCode) {
        return insertStaff(name, roleCode, null, stateCode);
    }

    public CreatedStaff createDeptManager(
            String name, String deptCode, Integer floor, String shiftEndHHmm, String stateCode) {
        if (!SUPPORTED_DEPTS.contains(deptCode)) {
            throw new IllegalArgumentException(
                    "createDeptManager 只支援現場部門代碼 housekeeping|engineering|frontdesk，收到: " + deptCode);
        }
        String shiftEnd = shiftEndHHmm != null ? shiftEndHHmm : DEFAULT_SHIFT_END;
        CreatedStaff created = insertStaff(name, "manager", deptCode, stateCode);
        assignPlanRoom(created.tenantId(), created.id(), floor, shiftEnd, OffsetDateTime.now());
        return created;
    }

    /**
     * 建立櫃檯人員：角色與部門都是 {@code frontdesk}。
     * 今日班次與負責樓層都照其他現場人員沒指定時的預設建立。
     */
    public CreatedStaff createFrontdeskStaff(String name, String stateCode) {
        return createFieldStaff(name, "frontdesk", null, null, stateCode);
    }

    /** 建立值班主管：角色是 {@code duty_manager}，沒有部門。 */
    public CreatedStaff createDutyManager(String name, String stateCode) {
        return insertStaff(name, "duty_manager", null, stateCode);
    }

    /** 建立老闆：角色是 {@code owner}，沒有部門。 */
    public CreatedStaff createOwner(String name, String stateCode) {
        return insertStaff(name, "owner", null, stateCode);
    }

    /**
     * 在指定的飯店建一位在職的房務員工，員工編號與密碼直接用給的值。
     * 只建帳號，不排班、不建負責樓層，用在只驗登入的案例；其他讀寫入口都假設員工在「測試飯店」，不能拿來操作這位員工。
     */
    public CreatedStaff createHousekeeperInTenant(long tenantId, String name, String loginId, String rawPassword) {
        UUID id = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient
                .sql(
                        """
                        INSERT INTO staff_accounts
                            (id, tenant_id, name, login_id, password_hash, role, dept, active, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'housekeeping', 'housekeeping', true, 'active', ?, ?)
                        """)
                .params(id, tenantId, name, loginId, passwordEncoder.encode(rawPassword), now, now)
                .update();
        JwtService.IssuedToken issued = jwtService.issue(tenantId, id, "housekeeping", "housekeeping");
        return new CreatedStaff(tenantId, id, loginId, rawPassword, issued.token());
    }

    public void setCredentials(UUID staffId, String loginId, String rawPassword) {
        long tenantId = tenantFixtures.testTenantId();
        String passwordHash = passwordEncoder.encode(rawPassword);
        jdbcClient
                .sql(
                        "UPDATE staff_accounts SET login_id = ?, password_hash = ?, updated_at = ?"
                                + " WHERE tenant_id = ? AND id = ?")
                .params(loginId, passwordHash, OffsetDateTime.now(), tenantId, staffId)
                .update();
    }

    public String loginId(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        return jdbcClient
                .sql("SELECT login_id FROM staff_accounts WHERE tenant_id = ? AND id = ?")
                .params(tenantId, staffId)
                .query(String.class)
                .single();
    }

    public void deactivate(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql("UPDATE staff_accounts SET active = false, updated_at = ? WHERE tenant_id = ? AND id = ?")
                .params(OffsetDateTime.now(), tenantId, staffId)
                .update();
    }

    public void markMustChangePassword(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql(
                        "UPDATE staff_accounts SET must_change_password = true, updated_at = ?"
                                + " WHERE tenant_id = ? AND id = ?")
                .params(OffsetDateTime.now(), tenantId, staffId)
                .update();
    }

    /** 讀這個帳號目前累計的連續輸入錯誤密碼次數。 */
    public int failedLoginCount(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        return jdbcClient
                .sql("SELECT failed_login_count FROM staff_accounts WHERE tenant_id = ? AND id = ?")
                .params(tenantId, staffId)
                .query(Integer.class)
                .single();
    }

    /** 直接把連續輸入錯誤密碼的次數寫成指定的值，不經過登入。 */
    public void setFailedLoginCount(UUID staffId, int count) {
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql(
                        "UPDATE staff_accounts SET failed_login_count = ?, updated_at = ?"
                                + " WHERE tenant_id = ? AND id = ?")
                .params(count, OffsetDateTime.now(), tenantId, staffId)
                .update();
    }

    /** 讀這個帳號鎖定到什麼時候；沒有被鎖定時是空的。 */
    public Optional<OffsetDateTime> lockedUntil(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        return jdbcClient
                .sql("SELECT locked_until FROM staff_accounts WHERE tenant_id = ? AND id = ?")
                .params(tenantId, staffId)
                .query((rs, rowNum) -> Optional.ofNullable(rs.getObject("locked_until", OffsetDateTime.class)))
                .single();
    }

    /** 直接寫入鎖定到期時間；傳 {@code null} 代表解除鎖定。 */
    public void setLockedUntil(UUID staffId, OffsetDateTime lockedUntil) {
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql("UPDATE staff_accounts SET locked_until = ?, updated_at = ? WHERE tenant_id = ? AND id = ?")
                .params(lockedUntil, OffsetDateTime.now(), tenantId, staffId)
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

        JwtService.IssuedToken issued = jwtService.issue(tenantId, id, roleCode, deptCode);
        return new CreatedStaff(tenantId, id, loginId, DEFAULT_PASSWORD, issued.token());
    }

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

    private static void requireKnownState(String stateCode) {
        if (!Set.of("active", "invited", "inactive").contains(stateCode)) {
            throw new IllegalArgumentException("未知的員工狀態代碼: " + stateCode);
        }
    }

    public record CreatedStaff(long tenantId, UUID id, String loginId, String password, String jwt) {}
}
