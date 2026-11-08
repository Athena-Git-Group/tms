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

/**
 * 通知中心句型的 Given（dsl.md 通知中心段落）。只建立/確認權威狀態，不含任何斷言——
 * 「必查」清單在 {@link PushThenSteps}。升級鏈／未接提醒的既有句型在
 * {@link PushManagerActionSteps} 等既有類別，本類別只放通知中心的句型。
 *
 * <p>notifications 沒有對應的 TestFixtures 入口（{@code bdd/support} 範圍不包含通知中心
 * 專屬資料），比照 {@code RepairGivenSteps} 對 repair_tickets 的做法，直接注入
 * {@link JdbcClient} 落地（表已由 {@code V16__notifications.sql} 建好）。
 */
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

    /**
     * dsl.md 怎麼做：透過後端測試資料在指定時刻為該員工建立通知紀錄。權威狀態落地：
     * 待 {@code /data-plan} 補持久通知紀錄表（已由 {@code V16__notifications.sql} 落地）；
     * 本句型假設每筆都綁定建立時刻與已讀旗標。Data Table 支援：標題（必填）、已讀
     * （選填，是或否，預設否）；已讀時 {@code read_at} 直接落在與 {@code created_at}
     * 相同的時刻——這句不關心已讀動作本身的時間點，只要旗標成立。
     *
     * <p>{@code target} 不是本句型要落地的語意（dsl.md 的 Data Table 參數沒有列
     * target），一律落 {@code kind=home} 佔位，滿足資料表 NOT NULL 與 contracts
     * PushTarget 的最小合法形狀。{@code event} 同理：這句型描述的是「通知在清單裡
     * 怎麼排、怎麼標已讀」，不是哪一種事件落下來的，所以一律落 pool_new_request
     * 佔位，滿足 NOT NULL 與資料層的事件值 CHECK。事件值真正有意義的地方在 App
     * 的通知中心（它決定每一列的分類圖示與顏色），那邊的 Given 會依標題反推真正
     * 的事件值。
     */
    /** 見 {@link #staffReceivedNotifications}：事件值在這句型裡沒有語意，用合法值佔位。 */
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

    /**
     * dsl.md 時刻參數：{@code 今天 HH:mm} 或 {@code {日期} HH:mm}。日期可以是相對詞
     * （今天／昨天／前天／明天）或 ISO 日期（{@code yyyy-MM-dd}）；時間一律 24 小時制
     * {@code HH:mm}，當地時區比照 {@link MutableClock#getZone()}。
     */
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
                // feature 檔的日期 token 寫「11/02」這種 月/日 省年形式；年份取 Clock 當年，
                // 若算出來在今天之後（例如 1 月的場景寫 12/31），表示講的是去年，往前退一年。
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
