package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PushGivenSteps {

    private static final Pattern TASK_SCREEN = Pattern.compile("^(\\d+) 的任務頁$");
    private static final Pattern POOL_HIGHLIGHT = Pattern.compile("^待認領並標亮 (\\d+)$");
    private static final Pattern HOME = Pattern.compile("^首頁$");
    private static final Pattern POOL_PLAIN = Pattern.compile("^待認領$");
    private static final Pattern TICKET_DETAIL = Pattern.compile("^(\\S+) 的工務單詳情$");
    private static final Pattern DEPT_POOL_HIGHLIGHT = Pattern.compile("^(房務|工務)待認領並標亮 (\\S+)$");

    private final AppiumWorld world;

    public PushGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    @假設("{string} 收到一則推播 {string}，去處為 {string}")
    public void receivesPushWithDestination(String staffName, String title, String destination) {
        world.ensureLoggedInAs(world.staff(staffName));
        Matcher taskMatcher = TASK_SCREEN.matcher(destination);
        Matcher highlightMatcher = POOL_HIGHLIGHT.matcher(destination);
        Matcher ticketMatcher = TICKET_DETAIL.matcher(destination);
        Matcher deptPoolMatcher = DEPT_POOL_HIGHLIGHT.matcher(destination);

        if (deptPoolMatcher.matches()) {
            String dept = BackendChannel.dept(deptPoolMatcher.group(1));
            String key = deptPoolMatcher.group(2);
            String highlightTaskId;
            if ("engineering".equals(dept)) {
                highlightTaskId = world.backend().repairTicketRow(key).get("task_id").toString();
            } else {
                world.ensurePoolTask(key, title);
                highlightTaskId = poolTaskIdByRoom(key);
            }
            world.sendPush(title, "", "pool", null, null, key, dept, highlightTaskId);
        } else if (taskMatcher.matches()) {
            String room = taskMatcher.group(1);
            world.sendPush(title, "", "task", room, taskIdByRoom(room), null);
        } else if (highlightMatcher.matches()) {
            String room = highlightMatcher.group(1);
            world.ensurePoolTask(room, title);
            world.sendPush(title, "", "pool", null, null, room);
        } else if (ticketMatcher.matches()) {
            String ticketNo = ticketMatcher.group(1);
            String taskId = world.backend().repairTicketRow(ticketNo).get("task_id").toString();
            world.sendPush(title, "", "ticket", null, taskId, null);
        } else if (HOME.matcher(destination).matches()) {
            world.sendPush(title, "", "home", null, null, null);
        } else if (POOL_PLAIN.matcher(destination).matches()) {
            world.sendPush(title, "", "pool", null, null, null);
        } else {
            throw new IllegalArgumentException("認不得的去處字串：「" + destination + "」");
        }
    }

    private String poolTaskIdByRoom(String roomNo) {
        List<Map<String, Object>> tasks = world.backend().queryRows("""
                SELECT t.id FROM tasks t
                JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND r.room_no = ? AND t.state = 'pool'
                """, world.backend().ensureTenant(), roomNo);
        if (tasks.size() != 1) {
            throw new IllegalStateException("房號「" + roomNo + "」在待認領池裡應剛好一張任務，實際 " + tasks.size());
        }
        return tasks.get(0).get("id").toString();
    }

    private String taskIdByRoom(String roomNo) {
        List<Map<String, Object>> tasks = world.backend().queryRows("""
                SELECT t.id FROM tasks t
                JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND r.room_no = ?
                """, world.backend().ensureTenant(), roomNo);
        return tasks.size() == 1 ? tasks.get(0).get("id").toString() : null;
    }

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(6);

    @假設("{string} 的通知中心已有以下通知：")
    public void notificationCenterHasNotifications(String staffName, DataTable table) {
        var staff = world.staff(staffName);
        world.setNotificationViewer(staff);
        LocalDate today = world.deviceNow().toLocalDate();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String title = requireCell(row, "標題");
            OffsetDateTime createdAt = parseMoment(requireCell(row, "時刻"), today);
            boolean read = "是".equals(row.get("已讀"));
            world.backend().createNotification(
                    staff.id(), eventForTitle(title), title, createdAt, read, targetForTitle(title));
        }
    }

    private String eventForTitle(String title) {
        if (title.startsWith("新需求 · ") || title.endsWith("已附交代退回待接池")) {
            return "pool_new_request";
        }
        if (title.contains("查房通過")) {
            return "inspection_passed";
        }
        if (title.contains("查房退回")) {
            return "inspection_failed";
        }
        if (title.endsWith("已修好")) {
            return "repair_completed";
        }
        if (title.contains("急件報修")) {
            return "repair_urgent";
        }
        if (title.contains("還沒人接")) {
            return "unclaimed_reminder_staff";
        }
        if (title.endsWith("已通知主管")) {
            return "unclaimed_reminder_manager";
        }
        if (title.contains("已由住客撤回")) {
            return "guest_withdrew";
        }
        if (title.contains("已被主管作廢")) {
            return "task_cancelled";
        }
        if (title.contains("已改派給")) {
            return "reassigned_away";
        }
        if (title.contains("主管改派一單給您")) {
            return "reassigned_to_me";
        }
        if (title.contains("需要接手")) {
            return "leave_takeover";
        }
        if (title.contains("退回待認領交給下一班")) {
            return "shift_end_returned";
        }
        if (title.contains("班末仍中斷")) {
            return "shift_end_still_interrupted";
        }
        if (title.contains("主管回覆了您的提問")) {
            return "kb_reply";
        }
        if (title.startsWith("回答被評為沒幫助")) {
            return "kb_unhelpful";
        }
        throw new IllegalArgumentException(
                "通知標題 \"" + title + "\" 對不上任何一種推播事件的標題格式，無法推導 "
                        + "notifications.event；請確認標題文案，或把新的標題格式補進 PushGivenSteps 的對照表");
    }

    private Map<String, Object> targetForTitle(String title) {
        Matcher roomPrefix = Pattern.compile("^(\\d+)\\s").matcher(title);
        if (roomPrefix.find()) {
            List<Map<String, Object>> tasks = world.backend().queryRows("""
                    SELECT t.id FROM tasks t
                    JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                    WHERE t.tenant_id = ? AND r.room_no = ?
                    """, world.backend().ensureTenant(), roomPrefix.group(1));
            if (tasks.size() == 1) {
                return Map.of("kind", "task", "taskId", tasks.get(0).get("id").toString());
            }
        }
        return Map.of("kind", "home");
    }

    @假設("{string} 已在 App 打開通知中心")
    public void alreadyOpenedNotificationCenter(String staffName) {
        var staff = world.staff(staffName);
        world.setNotificationViewer(staff);
        world.ensureLoggedInAs(staff);
        world.waitForLabelContaining("通知", SCREEN_TIMEOUT).click();
        world.waitForTestId("notification.screen", SCREEN_TIMEOUT);
    }

    static OffsetDateTime parseMoment(String momentExpr, LocalDate today) {
        String[] parts = momentExpr.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "時刻格式須為「今天 HH:mm」或「{日期} HH:mm」，實際=" + momentExpr);
        }
        LocalDate date = parseDateToken(parts[0], today);
        LocalTime time = LocalTime.parse(parts[1]);
        return ZonedDateTime.of(date, time, ZoneId.systemDefault()).toOffsetDateTime();
    }

    static LocalDate parseDateToken(String token, LocalDate today) {
        return switch (token) {
            case "今天" -> today;
            case "昨天" -> today.minusDays(1);
            case "前天" -> today.minusDays(2);
            case "明天" -> today.plusDays(1);
            default -> {
                Matcher monthDay = Pattern.compile("^(\\d{1,2})/(\\d{1,2})$").matcher(token);
                if (monthDay.matches()) {
                    LocalDate candidate = LocalDate.of(
                            today.getYear(),
                            Integer.parseInt(monthDay.group(1)),
                            Integer.parseInt(monthDay.group(2)));
                    yield candidate.isAfter(today) ? candidate.minusYears(1) : candidate;
                }
                yield LocalDate.parse(token);
            }
        };
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }
}
