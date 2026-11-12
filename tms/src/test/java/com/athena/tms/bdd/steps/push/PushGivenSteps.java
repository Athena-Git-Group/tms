package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.common.UuidV7;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

public class PushGivenSteps {

    private final ScenarioWorld world;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public PushGivenSteps(
            ScenarioWorld world,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            JdbcClient jdbcClient,
            ObjectMapper objectMapper) {
        this.world = world;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    private static final String PLACEHOLDER_EVENT = "pool_new_request";

    @假設("{string} 在 {string} 收到以下通知：")
    public void staffReceivedNotifications(String staffName, String momentExpr, DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        UUID staffId = UUID.fromString(world.staffId(staffName));
        OffsetDateTime createdAt = parseMoment(momentExpr);
        String targetJson = objectMapper.writeValueAsString(Map.of("kind", "home"));

        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String title = requireCell(row, "標題");
            boolean read = "是".equals(blankToNull(row.get("已讀")));
            OffsetDateTime readAt = read ? createdAt : null;

            jdbcClient
                    .sql(
                            """
                            INSERT INTO notifications
                                (id, tenant_id, recipient_staff_id, event, title, target, created_at, read_at)
                            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                            """)
                    .params(UuidV7.generate(), tenantId, staffId, PLACEHOLDER_EVENT, title, targetJson,
                            createdAt, readAt)
                    .update();
        }
    }

    private OffsetDateTime parseMoment(String momentExpr) {
        String[] parts = momentExpr.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "時刻格式須為「今天 HH:mm」或「{日期} HH:mm」，實際=" + momentExpr);
        }
        LocalDate date = parseDateToken(parts[0]);
        LocalTime time = LocalTime.parse(parts[1]);
        return ZonedDateTime.of(date, time, clock.getZone()).toOffsetDateTime();
    }

    private LocalDate parseDateToken(String token) {
        LocalDate today = LocalDate.now(clock);
        return switch (token) {
            case "今天" -> today;
            case "昨天" -> today.minusDays(1);
            case "前天" -> today.minusDays(2);
            case "明天" -> today.plusDays(1);
            default -> {
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("^(\\d{1,2})/(\\d{1,2})$").matcher(token);
                if (m.matches()) {
                    LocalDate candidate = LocalDate.of(
                            today.getYear(), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                    yield candidate.isAfter(today) ? candidate.minusYears(1) : candidate;
                }
                yield LocalDate.parse(token);
            }
        };
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
