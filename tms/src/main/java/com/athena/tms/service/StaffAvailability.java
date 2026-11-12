package com.athena.tms.service;

import com.athena.tms.dao.LeaveRepository;
import com.athena.tms.dao.TenantConfigRepository;
import com.athena.tms.dto.ShiftTimes;
import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 判斷一位員工此刻還接不接得了退回給他的單。查房退回（原清房者）與工務單驗收不通過
 * （原處理人）都用這一支決定單要回到本人名下，還是退回部門待認領，兩邊對「人已經不在」
 * 才會是同一個口徑。
 *
 * <p>接不了的情況有三種：請假中、此刻落在飯店所有班別時段之外、已過他自己排班的下班時間。
 * 第三項看的是排房計畫的班次，只有房務有；工務沒有個人排班，實際上只看前兩項。
 * 「此刻」一律取自注入的 {@link Clock}，和任務狀態轉移用同一個時間來源。
 */
@Component
public class StaffAvailability {

    private final LeaveRepository leaveRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StaffAvailability(LeaveRepository leaveRepository, TenantConfigRepository tenantConfigRepository,
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.leaveRepository = leaveRepository;
        this.tenantConfigRepository = tenantConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 這位員工此刻接不了退回的單時回 true。 */
    public boolean unavailable(long tenantId, UUID staffId) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        boolean onLeave = this.leaveRepository.existsByTenantIdAndStaffIdAndFromAtLessThanEqualAndToAtGreaterThan(tenantId, staffId, now, now);
        if (onLeave) {
            return true;
        }
        JsonNode shiftTimes = this.loadShiftTimes(tenantId);
        LocalTime at = now.toLocalTime();
        if (!ShiftTimes.coversTime(shiftTimes, at)) {
            return true;
        }
        return this.pastOwnShiftEnd(tenantId, staffId, shiftTimes, at);
    }

    /** 以這位員工最近一份排班表上的班次判斷是否已過下班時間；沒有排班或解析不出下班時間時視為還在班。 */
    private boolean pastOwnShiftEnd(long tenantId, UUID staffId, JsonNode shiftTimes, LocalTime at) {
        List<String> shifts = this.jdbcTemplate.queryForList("""
                SELECT cp.shift
                  FROM plan_rooms pr
                  JOIN cleaning_plans cp ON cp.tenant_id = pr.tenant_id AND cp.id = pr.plan_id
                 WHERE pr.tenant_id = ? AND pr.staff_id = ?
                 ORDER BY cp.generated_at DESC
                 LIMIT 1
                """, String.class, tenantId, staffId);
        if (shifts.isEmpty()) {
            return false;
        }
        LocalTime shiftEnd = ShiftTimes.endOf(shifts.get(0), shiftTimes);
        return shiftEnd != null && !at.isBefore(shiftEnd);
    }

    private JsonNode loadShiftTimes(long tenantId) {
        return this.tenantConfigRepository.findByTenantIdAndKey(tenantId, "shift_times").map(config -> {
            try {
                return this.objectMapper.readTree(config.getValue());
            }
            catch (JacksonException e) {
                return null;
            }
        }).orElse(null);
    }
}
