package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import com.athena.tms.service.JwtService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class StaffFixtures {

    public static final String PLAN_ROOM_PREFIX = "PLANROOM-F";

    public static final String DEFAULT_PASSWORD = "demo1234";

    // 測試員工的密碼用最低強度雜湊，登入照樣走正式的編碼器驗證，因為 BCrypt 雜湊自帶強度。
    // 用正式強度每位員工要算約 77 毫秒，整套測試建上千位員工會多花兩分多鐘。
    private static final PasswordEncoder FIXTURE_ENCODER = new BCryptPasswordEncoder(4);
    private static final String DEFAULT_PASSWORD_HASH = FIXTURE_ENCODER.encode(DEFAULT_PASSWORD);

    public static final String DEFAULT_SHIFT_END = "17:00";

    private static final String FIXTURE_FRONTDESK_NAME = "測試資料櫃檯";

    private static final Set<String> SUPPORTED_DEPTS = Set.of("housekeeping", "engineering", "frontdesk");

    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final JwtService jwtService;

    public StaffFixtures(
            JdbcClient jdbcClient,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            JwtService jwtService) {
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
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

    public CreatedStaff createFrontdeskStaff(String name, String stateCode) {
        return createFieldStaff(name, "frontdesk", null, null, stateCode);
    }

    public CreatedStaff createDutyManager(String name, String stateCode) {
        return insertStaff(name, "duty_manager", null, stateCode);
    }

    public CreatedStaff createOwner(String name, String stateCode) {
        return insertStaff(name, "owner", null, stateCode);
    }

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
                .params(id, tenantId, name, loginId, FIXTURE_ENCODER.encode(rawPassword), now, now)
                .update();
        JwtService.IssuedToken issued = jwtService.issue(tenantId, id, "housekeeping", "housekeeping");
        return new CreatedStaff(tenantId, id, loginId, rawPassword, issued.token());
    }

    public String fixtureFrontdeskJwt() {
        long tenantId = tenantFixtures.testTenantId();
        UUID id = jdbcClient
                .sql("SELECT id FROM staff_accounts WHERE tenant_id = ? AND name = ?")
                .params(tenantId, FIXTURE_FRONTDESK_NAME)
                .query(UUID.class)
                .optional()
                .orElseGet(() -> insertStaff(FIXTURE_FRONTDESK_NAME, "frontdesk", "frontdesk", "active").id());
        return jwtService.issue(tenantId, id, "frontdesk", "frontdesk").token();
    }

    public void setCredentials(UUID staffId, String loginId, String rawPassword) {
        long tenantId = tenantFixtures.testTenantId();
        String passwordHash = FIXTURE_ENCODER.encode(rawPassword);
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

    public int failedLoginCount(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        return jdbcClient
                .sql("SELECT failed_login_count FROM staff_accounts WHERE tenant_id = ? AND id = ?")
                .params(tenantId, staffId)
                .query(Integer.class)
                .single();
    }

    public void setFailedLoginCount(UUID staffId, int count) {
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql(
                        "UPDATE staff_accounts SET failed_login_count = ?, updated_at = ?"
                                + " WHERE tenant_id = ? AND id = ?")
                .params(count, OffsetDateTime.now(), tenantId, staffId)
                .update();
    }

    public Optional<OffsetDateTime> lockedUntil(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        return jdbcClient
                .sql("SELECT locked_until FROM staff_accounts WHERE tenant_id = ? AND id = ?")
                .params(tenantId, staffId)
                .query((rs, rowNum) -> Optional.ofNullable(rs.getObject("locked_until", OffsetDateTime.class)))
                .single();
    }

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
        String loginId = "fx-" + id.toString().replace("-", "").substring(20);
        String passwordHash = DEFAULT_PASSWORD_HASH;
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
        String placeholderRoomNo = PLAN_ROOM_PREFIX + effectiveFloor;
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
