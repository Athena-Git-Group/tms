package com.athena.tms.repair;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RepairTicketNumbers {

    private final JdbcTemplate jdbcTemplate;

    public RepairTicketNumbers(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(long tenantId) {
        Integer lastNo = jdbcTemplate.queryForObject(
                """
                INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET last_no = repair_ticket_counters.last_no + 1
                RETURNING last_no
                """,
                Integer.class, tenantId);
        return String.format("W-%04d", lastNo);
    }
}
