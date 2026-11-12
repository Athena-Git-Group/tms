package com.athena.tms.kb;

import com.athena.tms.common.TenantContext;
import com.athena.tms.service.TaskRowLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KbSuggestionService {

    private final JdbcTemplate jdbcTemplate;
    private final TaskRowLookup taskRowLookup;
    private final Clock clock;

    public KbSuggestionService(JdbcTemplate jdbcTemplate, TaskRowLookup taskRowLookup, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRowLookup = taskRowLookup;
        this.clock = clock;
    }

    public List<KbSuggestion> listSuggestions(UUID taskId) {
        long tenantId = TenantContext.require();
        var task = taskRowLookup.require(tenantId, taskId);
        String roomNo = String.valueOf(task.get("room_no"));
        UUID roomId = UUID.fromString(String.valueOf(task.get("room_id")));

        List<KbSuggestion> suggestions = new ArrayList<>();
        if ("cleaning".equals(String.valueOf(task.get("type")))) {
            List<String> skippedLabels = jdbcTemplate.queryForList(
                    """
                    SELECT label FROM task_sop_checks
                     WHERE tenant_id = ? AND task_id = ? AND skipped = true
                     ORDER BY checked_at DESC
                    """,
                    String.class, tenantId, taskId);
            if (!skippedLabels.isEmpty()) {
                for (String label : skippedLabels) {
                    suggestions.add(new KbSuggestion("「" + label + "」這項要怎麼做？", "SOP · 您剛剛跳過這項"));
                }
            } else if (isVipRoomToday(tenantId, roomId)) {
                suggestions.add(new KbSuggestion("VIP 房的備品要多放什麼？", "SOP"));
            }
        }
        suggestions.add(new KbSuggestion(roomNo + " 最近修過什麼？", "房間記錄"));
        suggestions.add(new KbSuggestion(roomNo + " 的住客有沒有特別要求？", "房間記錄"));
        return suggestions;
    }

    private boolean isVipRoomToday(long tenantId, UUID roomId) {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime startOfToday =
                today.atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime startOfTomorrow = startOfToday.plusDays(1);
        Integer hits = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM reservations_cache
                 WHERE tenant_id = ? AND room_id = ? AND superseded_at IS NULL
                   AND COALESCE((attrs ->> 'vip')::boolean, false)
                   AND checkin_at < ? AND checkout_at > ?
                """,
                Integer.class, tenantId, roomId, startOfTomorrow, startOfToday);
        return hits != null && hits > 0;
    }
}
