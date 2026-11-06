package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiException;
import com.athena.tms.handover.HandoverService;
import com.athena.tms.repair.RepairService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * contracts listDeptPool 的 dept 參數分流與 DeptPool.activeStaffCount。
 * 待接池本身的排序與篩選由 specs/truth 的場景覆蓋，這裡只測「誰能看哪一池」
 * 這條判斷，所以資料庫查詢用假的，回傳固定一個空池。
 */
class DeptPoolDeptParamTest {

    private static final long TENANT_ID = 1L;

    private JdbcTemplate jdbcTemplate;
    private TaskExecutionService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        Mockito.when(jdbcTemplate.queryForList(ArgumentMatchers.anyString(), ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of());
        Mockito.when(jdbcTemplate.queryForObject(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.eq(Integer.class),
                        ArgumentMatchers.eq(TENANT_ID),
                        ArgumentMatchers.anyString()))
                .thenReturn(3);
        service = new TaskExecutionService(
                Mockito.mock(TaskEngine.class),
                jdbcTemplate,
                Mockito.mock(PushOutboxWriter.class),
                Mockito.mock(TaskRowLookup.class),
                Mockito.mock(RepairService.class),
                Mockito.mock(HandoverService.class),
                new ObjectMapper(),
                Clock.systemUTC());
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        CurrentActor.clear();
    }

    @Test
    @DisplayName("現場員工省略 dept：看自己部門的池，並帶回該部門在職員工數")
    void fieldStaffWithoutDeptSeesOwnPool() {
        CurrentActor.set(CurrentActor.staff(TENANT_ID, UUID.randomUUID(), "housekeeping", "housekeeping"));

        Map<String, Object> pool = service.deptPool(null, null, null, null);

        Assertions.assertEquals(3, pool.get("activeStaffCount"), "activeStaffCount 要在回應裡");
        Mockito.verify(jdbcTemplate).queryForList(
                ArgumentMatchers.contains("t.dept = ?"),
                ArgumentMatchers.any(UUID.class),
                ArgumentMatchers.eq(TENANT_ID),
                ArgumentMatchers.eq("housekeeping"));
        Mockito.verify(jdbcTemplate).queryForObject(
                ArgumentMatchers.contains("status = 'active'"),
                ArgumentMatchers.eq(Integer.class),
                ArgumentMatchers.eq(TENANT_ID),
                ArgumentMatchers.eq("housekeeping"));
    }

    @Test
    @DisplayName("現場員工帶別的部門：403 forbidden_dept_pool")
    void fieldStaffCannotSeeAnotherDeptPool() {
        CurrentActor.set(CurrentActor.staff(TENANT_ID, UUID.randomUUID(), "housekeeping", "housekeeping"));

        ApiException error = Assertions.assertThrows(
                ApiException.class, () -> service.deptPool("engineering", null, null, null));

        Assertions.assertEquals(403, error.getStatus());
        Assertions.assertEquals("forbidden_dept_pool", error.getCode());
    }

    @Test
    @DisplayName("主管帶 dept：看指定部門的池")
    void managerWithDeptSeesThatPool() {
        CurrentActor.set(CurrentActor.staff(TENANT_ID, UUID.randomUUID(), "manager", null));

        Map<String, Object> pool = service.deptPool("engineering", null, null, null);

        Assertions.assertEquals(3, pool.get("activeStaffCount"));
        Mockito.verify(jdbcTemplate).queryForList(
                ArgumentMatchers.contains("t.dept = ?"),
                ArgumentMatchers.any(UUID.class),
                ArgumentMatchers.eq(TENANT_ID),
                ArgumentMatchers.eq("engineering"));
    }

    @Test
    @DisplayName("主管省略 dept：422 dept_required")
    void managerWithoutDeptIsRejected() {
        CurrentActor.set(CurrentActor.staff(TENANT_ID, UUID.randomUUID(), "manager", null));

        ApiException error =
                Assertions.assertThrows(ApiException.class, () -> service.deptPool(null, null, null, null));

        Assertions.assertEquals(422, error.getStatus());
        Assertions.assertEquals("dept_required", error.getCode());
    }

    @Test
    @DisplayName("老闆省略 dept：同樣是 422 dept_required")
    void ownerWithoutDeptIsRejected() {
        CurrentActor.set(CurrentActor.staff(TENANT_ID, UUID.randomUUID(), "owner", null));

        ApiException error =
                Assertions.assertThrows(ApiException.class, () -> service.deptPool(null, null, null, null));

        Assertions.assertEquals("dept_required", error.getCode());
    }
}
