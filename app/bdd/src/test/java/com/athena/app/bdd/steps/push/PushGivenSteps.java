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

    /**
     * 怎麼做：透過後端測試資料建立去處所指的任務，再送出一則推播 payload 給裝置，內容依
     * 後端推播格式帶任務 id 或待認領入口。App 處於背景。
     *
     * <p>「去處」是給人看的白話字串（dsl.md 的五種形式：X 的任務頁、待認領、待認領並
     * 標亮 X、首頁、X 的工務單詳情（X＝單號）），這裡解析成
     * {@code lib/core/push_gateway.dart} 認得的 {@code dest}/{@code room}/
     * {@code highlight} 欄位。去處指向「待認領並標亮 X」時，待認領池要有一張房號 X、
     * 狀態待認領的卡才有東西可以標亮，所以連帶補上這張卡的 fixture
     * （{@link AppiumWorld#ensurePoolTask}）；指向任務頁時不用額外建資料，
     * 任務頁的標題本身就帶房號，不靠 fixture 存在與否。
     *
     * <p>去處是「X 的工務單詳情」時，payload 帶 {@code dest}=ticket 與該修繕任務的
     * task id（contracts PushTarget：{@code kind}=ticket 時 {@code taskId} 帶的是該
     * 修繕任務的 task id，與工務單詳情端點的路徑參數同一個 id）。單號到 task id 的
     * 對照走後端權威讀回 {@code repair_tickets.task_id}（同 steps/repair 的
     * {@code repairTicketRow} 做法），所以該單號的工務單 fixture（「已有以下工務單：」
     * 等句型）必須先鋪好，查不到就讓這一步早爆。App 照 {@code dest} 機械導頁，
     * 不自己查任務類別。
     *
     * <p>去處是「房務待認領並標亮 X」或「工務待認領並標亮 X」時（主管收的推播），payload 帶
     * {@code kind}=pool、{@code dept}（housekeeping 或 engineering）與 {@code highlightTaskId}
     * （契約 PushTarget）；房務池標亮的是房號那張卡、工務池標亮的是單號那張（卡片鍵也一起
     * 帶在 {@code highlight}）。去處是「首頁」時 {@code kind}=home，App 依登入 session 的
     * role 與 dept 開外殼，payload 不帶角色。標題只是這則推播的顯示文字，不驗權威文案。
     *
     * <p>送推播前先以該員工登入（{@link AppiumWorld#ensureLoggedInAs}）：App 的
     * 起始畫面一律是登入殼，點開推播後的去處畫面（待認領池等）要用她的 JWT 向
     * 後端讀資料，沒登入時池子只會顯示載入失敗、沒有卡可標亮。
     */
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
                // 工務池的卡以單號指認，單要先由「已有以下工務單」鋪好，查不到就讓這一步早爆。
                highlightTaskId = world.backend().repairTicketRow(key).get("task_id").toString();
            } else {
                world.ensurePoolTask(key, title);
                highlightTaskId = poolTaskIdByRoom(key);
            }
            world.sendPush(title, "", "pool", null, null, key, dept, highlightTaskId);
        } else if (taskMatcher.matches()) {
            String room = taskMatcher.group(1);
            // 去處房號已有既存任務（「名下有以下任務」先鋪的）就把 taskId 一起放進
            // payload：任務頁的房號反查只蓋得到待認領與下一間推薦（accepted），
            // 已中斷等狀態的單要靠 taskId 直取，跟後端真推播的 PushTarget 帶
            // taskId 的行為同形。查不到既存任務（純導頁斷言的標題）就不帶。
            world.sendPush(title, "", "task", room, taskIdByRoom(room), null);
        } else if (highlightMatcher.matches()) {
            String room = highlightMatcher.group(1);
            world.ensurePoolTask(room, title);
            world.sendPush(title, "", "pool", null, null, room);
        } else if (ticketMatcher.matches()) {
            // 修繕任務的推播由後端發 kind=ticket，這裡同形：payload 帶該修繕任務的
            // task id，不帶房號——工務單詳情靠 task id 直取，跟房號無關。
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

    /** 房號 → 待認領池裡那張任務的 id（{@link AppiumWorld#ensurePoolTask} 保證剛好一張）。 */
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

    /** 房號 → 既存任務 id；剛好一張才帶（多張無從指認哪張是推播講的那張），否則 null。 */
    private String taskIdByRoom(String roomNo) {
        List<Map<String, Object>> tasks = world.backend().queryRows("""
                SELECT t.id FROM tasks t
                JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND r.room_no = ?
                """, world.backend().ensureTenant(), roomNo);
        return tasks.size() == 1 ? tasks.get(0).get("id").toString() : null;
    }

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(6);

    /**
     * 怎麼做：透過後端測試資料依表格建立該員工的通知紀錄。權威狀態落地：後端
     * {@code notifications} 表有這些紀錄。Data Table 支援：標題（必填）、時刻
     * （必填，{@code 今天 HH:mm} 或 {@code {日期} HH:mm}）、已讀（選填，是或否，
     * 預設否）。
     *
     * <p>已讀的通知把 {@code read_at} 落在與 {@code created_at} 相同的時刻：
     * 這句型只保證「這則通知已讀」這個旗標成立，不描述已讀動作發生在何時，
     * 另外編一個時間反而是在測試資料裡製造沒有規格依據的事實。
     *
     * <p>事件值（{@code event}，NOT NULL）：句型也沒有這個參數，照標題的文案格式
     * 反推——推導規則見 {@link #eventForTitle}。
     *
     * <p>去處（{@code target}，NOT NULL）：句型沒有這個參數，照後端產生通知的
     * 慣例由標題推導——標題開頭的房號若對得上後端已存在的任務（「名下有以下
     * 任務」先鋪的），就當作屬於本人的任務通知（{@code kind=task} 帶 taskId，
     * 對照 contracts PushTarget「屬於本人的任務帶 taskId」）；對不上任務的
     * 標題用最小合法形狀 {@code kind=home}。
     */
    @假設("{string} 的通知中心已有以下通知：")
    public void notificationCenterHasNotifications(String staffName, DataTable table) {
        var staff = world.staff(staffName);
        world.setNotificationViewer(staff); // 收件人＝通知 Then 權威讀回的當事員工
        LocalDate today = world.deviceNow().toLocalDate();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String title = requireCell(row, "標題");
            OffsetDateTime createdAt = parseMoment(requireCell(row, "時刻"), today);
            boolean read = "是".equals(row.get("已讀"));
            world.backend().createNotification(
                    staff.id(), eventForTitle(title), title, createdAt, read, targetForTitle(title));
        }
    }

    /**
     * 標題 → 推播事件值的對照（{@code notifications.event}，NOT NULL）。句型沒有這個
     * 參數，但通知的標題文案不是隨便寫的——每一種推播事件在後端都有固定的標題格式
     * （「{房號} 查房通過」「新需求 · {房號} {內容}」「{房號} {類別}已修好」……），
     * 所以照那些格式反推事件值，推出來的就是這則通知真正的來源，不是猜的。
     *
     * <p>對不上任何格式的標題直接讓這一步失敗，而不是隨便塞一個合法值：事件值決定
     * 通知中心那一列的分類圖示與顏色，塞錯的值會讓測試資料在畫面上呈現出一個產品
     * 不會產生的樣子，比測試掛掉更難發現。新增句型用到新的標題格式時，把格式補進
     * 這張對照表。
     */
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

    /** 標題開頭房號 → 既有任務的對照；推導規則見 {@link #notificationCenterHasNotifications}。 */
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

    /**
     * 怎麼做：以該員工登入後點 Tab bar「通知」，等列表載入完成。
     *
     * <p>「登入」走 {@link AppiumWorld#ensureLoggedInAs}（登入殼落地後 App 一律從
     * 登入畫面開始，不再有測試建置跳過登入的捷徑）。Tab 以可見文字「通知」定位
     * 而不用 accessibility id，因為 id 清單（ui-plan 的約定）沒有給 Tab bar 項目
     * 定 id，而測試不能自創 id——自創的 id 畫面上永遠不存在，會把「Tab 還沒實作」
     * 的失敗偽裝成「id 打錯」。「列表載入完成」以通知中心畫面根節點
     * {@code notification.screen} 出現為準。
     */
    @假設("{string} 已在 App 打開通知中心")
    public void alreadyOpenedNotificationCenter(String staffName) {
        // 員工沒建過就是劇本錯誤，照 AppiumWorld.staff 的語意早爆；開通知中心的人
        // 就是後續通知 Then 權威讀回的當事員工。
        var staff = world.staff(staffName);
        world.setNotificationViewer(staff);
        world.ensureLoggedInAs(staff);
        world.waitForLabelContaining("通知", SCREEN_TIMEOUT).click();
        world.waitForTestId("notification.screen", SCREEN_TIMEOUT);
    }

    /**
     * 把「時刻」參數（{@code 今天 HH:mm} 或 {@code {日期} HH:mm}）解析成通知的
     * {@code created_at}。日期收相對詞（今天／昨天／前天／明天）、「11/02」這種
     * 月/日省年形式與 ISO 日期；時間一律 24 小時制。相對詞與省年形式都以
     * {@code today}（裝置生效的日期）為基準，畫面的今天／更早分組才跟 fixture 同一天。
     */
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
                // 月/日省年形式的年份取當年；算出來在今天之後（1 月的場景寫 12/31）
                // 表示講的是去年，往前退一年，否則「更早」的通知會被排到未來。
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
