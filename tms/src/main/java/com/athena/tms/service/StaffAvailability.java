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
