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

/**
 * 組出「查一下」開啟當下的推薦問題（contracts 的 listKbSuggestions）。
 *
 * <p>推薦要貼著員工此刻最可能想問的事，所以依任務脈絡用固定規則排序，不經語言模型：
 * 打掃任務有剛跳過的 SOP 項時，先推薦「那一項要怎麼做」；沒有跳過但這間房今天住 VIP 時，
 * 先推薦 VIP 備品問題。之後固定補上這間房的維修記錄與住客要求兩問。
 * 如果不按脈絡排序，員工每次都得自己打字問，推薦清單就失去存在的意義。
 */
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

    /** 查無任務時由 {@link TaskRowLookup} 直接回 404，不回空清單。 */
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
                // 最近跳過的排最前面：員工通常是對剛剛那一項有疑問才打開查一下。
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

    /**
     * 這間房今天是否住著 VIP：看訂房快取裡有沒有一筆未被取代、住期涵蓋今天、
     * 且屬性標了 vip 的訂單。用住期涵蓋判斷而不是只比入住日，否則昨天入住、
     * 今天還沒退房的 VIP 會被漏掉。
     */
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
