package com.athena.web.bdd.steps.common;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.TaskSituation;
import com.athena.bddsupport.TaskSituationSpec;
import com.athena.web.bdd.support.Clocks;
import com.athena.web.bdd.support.ScenarioTasks;
import com.athena.web.bdd.support.WebWorld;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把「全館有以下的單：」表格的每一列，換成後端裡一張處在那個狀況的單。
 *
 * <h2>要保證什麼</h2>
 * 表格寫了的照寫，沒寫的照規格的預設補上；規格也沒說的，每次都補同一個值，
 * 同一份表格每次建出來的單都一樣。建好的單登記在 {@link ScenarioTasks}，之後的句子用房號找得到。
 *
 * <h2>怎麼做到</h2>
 * 每一列先把中文說法換成後端的值、把相對時間換成場景時鐘上的時刻，
 * 有住客的單先建訂房，再交給 {@link BackendChannel#createTaskInSituation} 一次建出整張單與它的歷程。
 *
 * <h2>規格沒說、這裡自己定的預設</h2>
 * <ul>
 *   <li>要有人做動作、表格又沒寫「經手人」時：主管指派、轉派、延後、取消、查房、驗收是張主管；
 *       當場回答與櫃檯派出是林佩君；櫃檯開單是林佩君；同仁報修與同仁撤回的報修人是小美。</li>
 *   <li>要有負責人、表格又沒寫時：房務照小美、小林、小陳、阿美的順序，工務是阿強。
 *       同一位同仁同時只能有一張進行中，所以進行中的單跳過手上已經有進行中的人。</li>
 *   <li>沒有標題也沒有住客原話時，依類型取一個常見的標題。</li>
 *   <li>類型是其他、又沒寫部門時：還在櫃檯手上的單（待派單、已回答、在待派單就被收回或取消）不分部門，其餘歸房務。</li>
 *   <li>已回答沒寫回覆時，回的是常用回覆的第一頁「早餐時間」。</li>
 *   <li>預設的住客昨天 15:00 入住、明天 11:00 退房；只寫了入住日或退房日時，時刻也用這兩個。</li>
 * </ul>
 */
final class TaskTableRows {

    private static final Set<String> COLUMNS = Set.of(
            "房號", "狀況", "標題", "類型", "部門", "負責人", "住客", "VIP", "入住日", "退房日", "來源",
            "住客原話", "原文語言", "翻譯", "時間", "建立", "派出時間", "到手時間", "派單方式", "經手人",
            "原負責人", "說明", "SOP", "單號", "急件");

    private static final Map<String, String> SOURCE = Map.of(
            "住客掃碼", "guest", "櫃檯開單", "desk", "系統排房", "system", "同仁報修", "staff");

    private static final Map<String, String> DEPT = Map.of(
            "房務", "housekeeping", "工務", "engineering", "未分部門", "frontdesk");

    private static final Map<String, String> LANGUAGE = Map.of(
            "日文", "ja", "英文", "en", "韓文", "ko", "越南文", "vi", "中文", "zh-TW");

    private static final Map<String, String> TRIAGE_VIA = Map.of("按鈕", "button", "拖曳", "drag");

    private static final Map<String, String> DEFAULT_TITLE = Map.of(
            "打掃", "退房打掃", "備品", "加一條浴巾", "修繕", "冷氣不冷", "其他", "住客詢問");

    private static final Map<String, List<String>> DEFAULT_STAFF_BY_DEPT = Map.of(
            "housekeeping", List.of("小美", "小林", "小陳", "阿美"),
            "engineering", List.of("阿強"));

    private static final String DEFAULT_MANAGER = "張主管";
    private static final String DEFAULT_DESK = "林佩君";
    private static final String DEFAULT_REPORTER = "小美";
    private static final String DEFAULT_ANSWER = "早餐 07:00–10:00，在 2 樓自助餐廳，憑房卡入場。";

    /** 住客姓名由測試自編，挑不會和預設帳號或規格裡寫明的住客撞名的名字。 */
    private static final List<String> INVENTED_GUESTS = List.of(
            "黃志明", "李淑芬", "吳建宏", "鄭雅文", "謝宗翰", "郭美玲", "曾俊傑", "蕭佩玲");

    private static final LocalTime DEFAULT_CHECKIN = LocalTime.of(15, 0);
    private static final LocalTime DEFAULT_CHECKOUT = LocalTime.of(11, 0);

    /** 空房的住客單要掛的那筆訂房放在這麼多天以前，和後端資料準備替空房建住客連線的做法相同，兩邊才會是同一筆。 */
    private static final int VACANT_ROOM_STAY_DAYS_AGO = 30;

    /** 這些狀況的單沒有負責人，或負責人可有可無；其餘狀況一定要有。 */
    private static final Set<TaskSituation> NO_ASSIGNEE_NEEDED = EnumSet.of(
            TaskSituation.TRIAGE, TaskSituation.POOL, TaskSituation.ANSWERED, TaskSituation.CANCELLED_BY_GUEST,
            TaskSituation.CANCELLED_BY_REPORTER, TaskSituation.DEFERRED_BY_MANAGER,
            TaskSituation.CANCELLED_BY_MANAGER, TaskSituation.CANCELLED_PLAN_SUPERSEDED);

    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern MINUTES_AGO = Pattern.compile("(\\d+) ?分鐘前");
    private static final Pattern DAYS_AGO = Pattern.compile("(\\d+) ?天前 (\\d{1,2}):(\\d{2})");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})/(\\d{1,2})");
    private static final Pattern SOP = Pattern.compile("(\\d+)/(\\d+)");

    private final WebWorld world;

    /** 手上已經有一張進行中的同仁，替沒寫負責人的進行中單挑人時要跳過。 */
    private final Set<String> busyDoing = new HashSet<>();

    TaskTableRows(WebWorld world) {
        this.world = world;
    }

    void createAll(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            for (String column : row.keySet()) {
                if (!COLUMNS.contains(column)) {
                    throw new IllegalArgumentException("「全館有以下的單：」不認得「" + column + "」這一欄，可以用的欄：" + COLUMNS);
                }
            }
        }
        busyDoing.addAll(staffAlreadyDoing());
        for (Map<String, String> row : rows) {
            if (TaskSituation.ofTerm(required(row, "狀況")) == TaskSituation.DOING && cell(row, "負責人") != null) {
                busyDoing.add(cell(row, "負責人"));
            }
        }
        for (Map<String, String> row : rows) {
            create(row);
        }
    }

    private void create(Map<String, String> row) {
        String roomNo = required(row, "房號");
        String situationTerm = required(row, "狀況");
        TaskSituation situation = TaskSituation.ofTerm(situationTerm);
        String guestText = cell(row, "住客原話");
        String typeTerm = orDefault(cell(row, "類型"), guestText != null ? "其他" : "備品");
        String sourceTerm = orDefault(cell(row, "來源"), defaultSource(typeTerm, guestText));
        String source = lookup(SOURCE, sourceTerm, "來源");
        String title = cell(row, "標題");
        if (title == null && guestText == null) {
            title = lookup(DEFAULT_TITLE, typeTerm, "類型");
        }
        String dept = dept(row, typeTerm, situation, guestText);

        TaskSituationSpec spec = TaskSituationSpec.of(roomNo, BackendChannel.taskType(typeTerm), source)
                .title(title)
                .dept(dept);
        if (guestText != null) {
            String lang = cell(row, "原文語言") == null ? null : lookup(LANGUAGE, cell(row, "原文語言"), "原文語言");
            spec.guestText(guestText, lang, cell(row, "翻譯"));
        }
        applyTimes(row, situation, spec);
        applyPeople(row, situation, source, typeTerm, dept, spec);
        applyNote(row, situation, spec);
        applyExtras(row, spec);
        UUID reservationId = reservation(row, roomNo, source);
        if (reservationId != null) {
            spec.reservationId(reservationId);
        }

        UUID taskId = world.backend().createTaskInSituation(situation, spec);
        String shownTitle = title != null ? title : titleFromGuestText(guestText);
        world.tasks().add(new ScenarioTasks.Entry(roomNo, shownTitle, taskId, situationTerm));
    }

    private static String defaultSource(String typeTerm, String guestText) {
        if (guestText != null) {
            return "住客掃碼";
        }
        return switch (typeTerm) {
            case "打掃" -> "系統排房";
            case "修繕" -> "同仁報修";
            default -> "住客掃碼";
        };
    }

    private static String dept(
            Map<String, String> row, String typeTerm, TaskSituation situation, String guestText) {
        String deptTerm = cell(row, "部門");
        if (deptTerm != null) {
            return lookup(DEPT, deptTerm, "部門");
        }
        if (situation == TaskSituation.TRIAGE || situation == TaskSituation.ANSWERED) {
            return "frontdesk";
        }
        return switch (typeTerm) {
            case "修繕" -> "engineering";
            case "其他" -> {
                boolean leftTriageUnassigned = situation == TaskSituation.CANCELLED_BY_GUEST
                        || situation == TaskSituation.CANCELLED_BY_MANAGER;
                yield leftTriageUnassigned && guestText != null ? "frontdesk" : "housekeeping";
            }
            default -> "housekeeping";
        };
    }

    /**
     * 「時間」是這個狀況的計時起點，其餘三個時刻是歷程上更早的點。
     * 只寫了建立、沒寫時間的待派單與待認領，等待就從建立起算；
     * 不這樣補的話計時起點會是現在，晚於建立，待認領的單會被當成退回過的單。
     */
    private void applyTimes(Map<String, String> row, TaskSituation situation, TaskSituationSpec spec) {
        OffsetDateTime createdAt = moment(cell(row, "建立"));
        OffsetDateTime at = moment(cell(row, "時間"));
        boolean waitsFromCreation = situation == TaskSituation.TRIAGE || situation == TaskSituation.POOL;
        if (at == null) {
            at = waitsFromCreation && createdAt != null ? createdAt : world.clocks().now();
        }
        spec.at(at).createdAt(createdAt).triagedAt(moment(cell(row, "派出時間")))
                .acceptedAt(moment(cell(row, "到手時間")));
    }

    /**
     * 「經手人」依狀況放到對的位置：派單的櫃檯、指派或轉派的主管、做了這個狀況那個動作的人，或開單的人。
     * 狀況本身沒有需要經手人的動作時，寫了經手人就看來源：櫃檯開單與同仁報修是開單的人，其餘是派出這張單的櫃檯。
     */
    private void applyPeople(Map<String, String> row, TaskSituation situation, String source, String typeTerm,
            String dept, TaskSituationSpec spec) {
        String handler = cell(row, "經手人");
        String assignee = cell(row, "負責人");
        CreatedStaff createdBy = null;
        boolean handlerUsed = true;

        switch (situation) {
            case ACCEPTED_BY_MANAGER, ACCEPTED_BY_REASSIGN -> spec.assignedBy(staff(orDefault(handler, DEFAULT_MANAGER)));
            case DEFERRED_BY_MANAGER, CANCELLED_BY_MANAGER, INSPECTION_PASSED, CLOSED_VERIFIED ->
                    spec.actedBy(staff(orDefault(handler, DEFAULT_MANAGER)));
            case ANSWERED -> spec.actedBy(staff(orDefault(handler, DEFAULT_DESK)));
            case INTERRUPTED_BY_LEAVE -> {
                if (handler != null) {
                    spec.actedBy(staff(handler));
                }
            }
            case CANCELLED_BY_REPORTER -> {
                // 撤回的就是報修人，規格有的寫在經手人、有的寫在負責人，兩種都收；這個狀況本身沒有負責人。
                createdBy = staff(orDefault(handler, orDefault(assignee, DEFAULT_REPORTER)));
                assignee = null;
            }
            default -> handlerUsed = false;
        }

        boolean byStaff = "desk".equals(source) || "staff".equals(source);
        boolean triaged = cell(row, "派出時間") != null || cell(row, "派單方式") != null;
        if (!handlerUsed && handler != null) {
            if (byStaff) {
                createdBy = staff(handler);
            } else {
                triaged = true;
            }
        }
        if (triaged) {
            String via = cell(row, "派單方式") == null ? null : lookup(TRIAGE_VIA, cell(row, "派單方式"), "派單方式");
            spec.triagedBy(staff(!handlerUsed && handler != null ? handler : DEFAULT_DESK), via);
        }
        if (createdBy == null && byStaff) {
            createdBy = staff("desk".equals(source) ? DEFAULT_DESK : DEFAULT_REPORTER);
        }

        if (assignee == null && !NO_ASSIGNEE_NEEDED.contains(situation)) {
            assignee = defaultAssignee(dept, situation);
        }
        if (assignee != null) {
            spec.assignee(staff(assignee));
        }
        if ("修繕".equals(typeTerm) && createdBy == null && assignee == null) {
            createdBy = staff(DEFAULT_REPORTER);
        }
        if (createdBy != null) {
            spec.createdBy(createdBy);
        }
        if (situation == TaskSituation.ACCEPTED_BY_REASSIGN) {
            String previous = cell(row, "原負責人");
            spec.previousAssignee(staff(previous != null ? previous : anotherStaff(dept, assignee)));
        }
    }

    private String defaultAssignee(String dept, TaskSituation situation) {
        List<String> candidates = DEFAULT_STAFF_BY_DEPT.get(dept);
        if (candidates == null) {
            throw new IllegalArgumentException("「" + situation.term() + "」要有負責人，但部門 " + dept + " 沒有預設的同仁可以挑；請在表格寫「負責人」");
        }
        if (situation != TaskSituation.DOING) {
            return candidates.get(0);
        }
        for (String name : candidates) {
            if (busyDoing.add(name)) {
                return name;
            }
        }
        throw new IllegalArgumentException("部門 " + dept + " 的預設同仁手上都已經有進行中的單了，一位同仁同時只能有一張進行中；請在表格寫「負責人」");
    }

    private static String anotherStaff(String dept, String assignee) {
        List<String> candidates = DEFAULT_STAFF_BY_DEPT.getOrDefault(dept, List.of());
        return candidates.stream().filter(name -> !name.equals(assignee)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("被轉派的單要有原負責人，部門 " + dept + " 挑不出負責人以外的同仁；請在表格寫「原負責人」"));
    }

    private static void applyNote(Map<String, String> row, TaskSituation situation, TaskSituationSpec spec) {
        String note = cell(row, "說明");
        switch (situation) {
            case ANSWERED -> spec.answer(orDefault(note, DEFAULT_ANSWER));
            case INTERRUPTED_BY_SELF, DEFERRED_BY_SELF -> spec.reason(note);
            case INTERRUPTED_BY_LEAVE -> spec.handoverNote(note);
            default -> {
                if (note != null) {
                    throw new IllegalArgumentException("「" + situation.term() + "」的「說明」不知道該放在哪裡；"
                            + "目前只有已回答、本人暫停、本人延後與請假中斷的單收說明");
                }
            }
        }
    }

    private void applyExtras(Map<String, String> row, TaskSituationSpec spec) {
        spec.displayNo(cell(row, "單號"));
        spec.urgent("是".equals(cell(row, "急件")));
        String sop = cell(row, "SOP");
        if (sop != null) {
            Matcher matcher = SOP.matcher(sop);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("「SOP」要寫成「已做/總數」，收到「" + sop + "」");
            }
            int total = Integer.parseInt(matcher.group(2));
            for (int i = 1; i <= total; i++) {
                world.backend().ensureCleaningSopItemId("清潔項目 " + i);
            }
            spec.sopCheckedCount(Integer.parseInt(matcher.group(1)));
        }
    }

    /**
     * 這張單要連結的訂房。看板上的住客姓名與 VIP 都從訂房讀，所以有住客的單一律先建訂房再建單。
     * 同一間房在同一個場景只建一筆在住訂房，之後的單沿用。
     * 空房只有住客自己送的單需要訂房（後端要求住客的單一定連結訂房），掛一筆住期早已結束、沒有姓名的訂房。
     */
    private UUID reservation(Map<String, String> row, String roomNo, String source) {
        String guest = cell(row, "住客");
        OffsetDateTime now = world.clocks().now();
        if ("空房".equals(guest)) {
            if (!"guest".equals(source)) {
                return null;
            }
            OffsetDateTime longAgo = now.minusDays(VACANT_ROOM_STAY_DAYS_AGO);
            return world.backend().createReservation(roomNo, null, false, longAgo, longAgo.plusDays(1));
        }
        ScenarioTasks tasks = world.tasks();
        if (tasks.reservationOf(roomNo).isPresent()) {
            return tasks.reservationOf(roomNo).get();
        }
        String name = guest != null ? guest : INVENTED_GUESTS.get(tasks.all().size() % INVENTED_GUESTS.size());
        boolean vip = "是".equals(cell(row, "VIP"));
        LocalDate today = world.clocks().today();
        OffsetDateTime checkin = Clocks.at(
                cell(row, "入住日") != null ? monthDay(cell(row, "入住日")) : today.minusDays(1), DEFAULT_CHECKIN);
        OffsetDateTime checkout = Clocks.at(
                cell(row, "退房日") != null ? monthDay(cell(row, "退房日")) : today.plusDays(1), DEFAULT_CHECKOUT);
        UUID reservationId = world.backend().createReservation(roomNo, name, vip, checkin, checkout);
        tasks.rememberReservation(roomNo, reservationId);
        return reservationId;
    }

    /** 規格寫的時刻都相對於場景目前的「現在」：今天的 HH:mm、昨天、前天、N 天前，或 N 分鐘前。 */
    private OffsetDateTime moment(String text) {
        if (text == null) {
            return null;
        }
        OffsetDateTime now = world.clocks().now();
        LocalDate today = world.clocks().today();
        String value = text.strip();
        Matcher matcher = MINUTES_AGO.matcher(value);
        if (matcher.matches()) {
            return now.minusMinutes(Long.parseLong(matcher.group(1)));
        }
        matcher = DAYS_AGO.matcher(value);
        if (matcher.matches()) {
            return Clocks.at(today.minusDays(Long.parseLong(matcher.group(1))), clock(matcher.group(2), matcher.group(3)));
        }
        int daysBack = 0;
        if (value.startsWith("昨天 ")) {
            daysBack = 1;
            value = value.substring(3);
        } else if (value.startsWith("前天 ")) {
            daysBack = 2;
            value = value.substring(3);
        }
        matcher = CLOCK.matcher(value.strip());
        if (matcher.matches()) {
            return Clocks.at(today.minusDays(daysBack), clock(matcher.group(1), matcher.group(2)));
        }
        throw new IllegalArgumentException("時刻要寫成 HH:mm、昨天 HH:mm、前天 HH:mm、N 天前 HH:mm 或 N 分鐘前，收到「" + text + "」");
    }

    private static LocalTime clock(String hour, String minute) {
        return LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
    }

    private static LocalDate monthDay(String text) {
        Matcher matcher = MONTH_DAY.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("日期要寫成 M/D，收到「" + text + "」");
        }
        return LocalDate.of(Clocks.DEFAULT_DATE.getYear(), Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)));
    }

    private Set<String> staffAlreadyDoing() {
        Set<String> names = new HashSet<>();
        for (Map<String, Object> found : world.backend().queryRows("""
                SELECT s.name FROM tasks t JOIN staff_accounts s ON s.id = t.assignee_id
                 WHERE t.tenant_id = ? AND t.state = 'doing'
                """, world.defaults().tenantId())) {
            names.add((String) found.get("name"));
        }
        return names;
    }

    private CreatedStaff staff(String name) {
        return world.staff(name);
    }

    /** 和後端建單時一樣，住客原文太長時標題只取前 120 字。 */
    private static String titleFromGuestText(String guestText) {
        return guestText.length() <= 120 ? guestText : guestText.substring(0, 120) + "…";
    }

    private static String required(Map<String, String> row, String column) {
        String value = cell(row, column);
        if (value == null) {
            throw new IllegalArgumentException("「全館有以下的單：」每一列都要寫「" + column + "」：" + row);
        }
        return value;
    }

    /** 空白的格子當成沒寫。 */
    static String cell(Map<String, String> row, String column) {
        String value = row.get(column);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String lookup(Map<String, String> table, String term, String column) {
        String value = table.get(term);
        if (value == null) {
            throw new IllegalArgumentException("「" + column + "」不認得「" + term + "」，可以用的值：" + table.keySet());
        }
        return value;
    }
}
