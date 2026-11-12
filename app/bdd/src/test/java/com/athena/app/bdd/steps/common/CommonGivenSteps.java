package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 介面根共通句的 Given（dsl.md Given 句型區）。只建立/確認權威狀態，不含任何斷言——
 * 「必查」清單在 {@link CommonThenSteps}。
 */
public class CommonGivenSteps {

    private final AppiumWorld world;

    public CommonGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料建立員工與班次（同後端共用 DSL 的同名句型）。權威狀態落地：
     * 後端 staff 資料已存在，{@code role} 依角色寫入；待開通與停用的員工不出現在挑人名單、
     * 不能搶單、不收推播。回寫：登入帳號。
     *
     * <p>欄位：姓名與部門必填；角色（主管／員工；部門寫房務或工務而角色寫主管＝被設了部門的
     * 主管帳號）、負責樓層、班次結束（{@code HH:mm}）、狀態（在職／待開通／停用）選填，預設
     * 角色依部門、班次結束 17:00、狀態在職。班次結束沒寫時不落真的時刻，理由見
     * {@link com.athena.app.bdd.BackendChannel#assignPlanRoom(java.util.UUID, int)}：預設
     * 17:00 一落地，班末掃描就會在那個時刻之後把她名下的單退池，劇本沒要求的事不該由
     * 預設值宣告。
     */
    @假設("飯店有以下員工：")
    public void hotelHasStaff(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            world.seedStaff(
                    requireCell(row, "姓名"),
                    requireCell(row, "部門"),
                    blankToNull(row.get("角色")),
                    optionalInt(row, "負責樓層"),
                    blankToNull(row.get("班次結束")),
                    blankToNull(row.get("狀態")));
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** 選填的整數欄；缺欄或留白一律當沒給（dsl.md 的選填欄語意）。 */
    private static Integer optionalInt(Map<String, String> row, String column) {
        String value = row.get(column);
        return (value == null || value.isBlank()) ? null : Integer.valueOf(value.trim());
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }

    /**
     * 怎麼做：透過後端測試資料建立任務。劇本的操作者就是這位員工時，以他登入 App 並停在
     * 首頁；操作者是別人（主管看他清好的房）時只建資料、不換登入者。
     * 權威狀態落地：後端任務真相已存在。
     *
     * <p>登入是延後到第一個需要身分的句子才做的（{@link AppiumWorld#ensureLoggedInAs}），
     * 所以這裡不會因為表格的人不是操作者就把登入者換掉；唯一會把表格的人拉去登入的是
     * 「進行中要在 App 按開始」那條路，別人登入時它改走後端開工，見
     * {@code AppiumWorld.settleDeferredStartsViaBackendExcept}。
     *
     * <p>「完成時間」（{@code 今天 HH:mm}／{@code 昨天 HH:mm}）只對已完成的狀態有意義，
     * 寫進 {@code tasks.done_at} 與 completed 事件時間；寫昨天時 work_date 也是昨天。
     * 落地排在建單之後：完成那一步是正式端點寫的現在時刻，事後才推得回去。
     */
    @假設("{string} 名下有以下任務：")
    public void staffHasTasks(String staffName, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        List<AppiumWorld.TaskFixture> tasks = rows.stream()
                .map(CommonGivenSteps::toTaskFixture)
                .toList();
        world.seedTasks(staffName, tasks);

        LocalDate today = world.deviceNow().toLocalDate();
        for (Map<String, String> row : rows) {
            String doneAtText = blankToNull(row.get("完成時間"));
            if (doneAtText == null) {
                continue;
            }
            if (!DONE_STATES.contains(row.get("狀態"))) {
                throw new IllegalArgumentException("「完成時間」只用於已完成的狀態（" + DONE_STATES
                        + "），「" + row.get("房號") + "」的狀態是「" + row.get("狀態") + "」");
            }
            Map<String, Object> task = world.taskRowByRoomNo(row.get("房號"));
            world.backend().setTaskDoneAt((UUID) task.get("id"), parseMoment(doneAtText, today));
        }
    }

    /** 可以帶「完成時間」的狀態用語（backend dsl 同名句型的「完成時間」欄）。 */
    private static final List<String> DONE_STATES =
            List.of("待查房", "已送達", "查房通過", "完成 · 免查房");

    /** 「今天 HH:mm」／「昨天 HH:mm」→ 時刻；日期以裝置生效的日期為準，跟通知中心那一句同一套。 */
    private static OffsetDateTime parseMoment(String text, LocalDate today) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("完成時間格式須為「今天 HH:mm」或「昨天 HH:mm」，實際=" + text);
        }
        LocalDate date = switch (parts[0]) {
            case "今天" -> today;
            case "昨天" -> today.minusDays(1);
            default -> throw new IllegalArgumentException("完成時間的日期只收「今天」或「昨天」，實際=" + text);
        };
        return ZonedDateTime.of(date, LocalTime.parse(parts[1]), ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static AppiumWorld.TaskFixture toTaskFixture(Map<String, String> row) {
        Integer elapsedMinutes = optionalInt(row, "已做分鐘");
        String pausedReason = row.get("停住原因");
        if (pausedReason != null && pausedReason.isBlank()) {
            pausedReason = null;
        }
        String category = row.get("類別");
        if (category != null && category.isBlank()) {
            category = null;
        }
        return new AppiumWorld.TaskFixture(
                row.get("房號"),
                row.get("內容"),
                category,
                optionalInt(row, "預估分鐘"),
                row.get("狀態"),
                elapsedMinutes == null ? 0 : elapsedMinutes,
                optionalInt(row, "重做次數"),
                pausedReason,
                "是".equals(row.get("免查房")));
    }

    /**
     * 怎麼做：把裝置時間與後端 {@code Clock} 設為今日該時刻，兩邊同一個時刻。
     *
     * <p>裝置半邊：注入測試後門的 {@code POST /clock}（{@code TestHooks.injectedClockIso}），
     * 需要被控時間的畫面行為從這個注入值讀。後端半邊：打後端的撥時鐘端點
     * （{@link BackendChannel#setBackendClock}），後端「現在」固定在該時刻，班別、是否下班、
     * 是否今天的判定都照這個時刻。後端沒帶撥時鐘旗標啟動時這句直接失敗並印出重啟指令，
     * 不會只撥手機。
     *
     * <p>只撥時刻、日期維持今天：fixture 的「今天」與後端的「今天」因此仍是同一天，
     * 分組與日期類斷言不受影響。後端時鐘在每個情境開場由 {@link AppiumWorld} 放回系統時間。
     */
    @假設("現在時間是 {string}")
    public void nowIs(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        OffsetDateTime moment = ZonedDateTime
                .of(LocalDate.now(), time, ZoneId.systemDefault())
                .toOffsetDateTime();
        world.backend().setBackendClock(moment);
        world.postBackdoorJson("/clock", "{\"isoTime\":\"" + moment + "\"}");
    }

    /**
     * 怎麼做：把裝置切到無網路（測試後門的 {@code /offline}）；切換前先確保劇本中的操作者
     * 已登入（App 不支援離線登入）。離線判定以這個注入開關為準，App 不另做網路偵測。
     *
     * <p>操作者是誰：前面的句子已經有人登入就是那一位（主管「已在 App 登入，停在首頁」之後
     * 切離線）；還沒有人登入時由 {@link AppiumWorld#goOffline} 依背景推定——背景只有一位
     * 員工就是他，主管一個人的離線劇本也切得了。
     */
    @假設("手機目前離線")
    public void phoneOffline() {
        // 切離線之前先把測試資料欠的「在 App 按開始」補完：開工是離線時被擋下來的
        // 動作，裝置離線後那一筆進行中就再也補不起來了。
        world.settleDeferredStarts();
        world.goOffline();
    }

    /**
     * App 本機記住「上次在待認領停在哪個部門」的那一把鍵（{@code shared_preferences}），值是
     * housekeeping 或 engineering；沒有紀錄視為 housekeeping。產品碼要用同一個鍵名，
     * 否則這句鋪的狀態 App 讀不到。
     */
    public static final String POOL_DEPT_PREFERENCE_KEY = "pool.dept";

    /**
     * 怎麼做：把 App 本機的待認領部門選擇寫成該部門並重新啟動 App，以該主管身分登入後停在
     * 首頁、還沒切到待認領。這個選擇只存在這台手機，不是後端資料。回寫：無。
     *
     * <p>不經畫面：真的去切一次再切回來，等於在 Arrange 裡先跑一遍受測的切換動作。偏好設定
     * 只在 App 啟動時讀，所以寫完要整個 process 重開，再重新登入。
     */
    @假設("{string} 上次在待認領停在 {string}")
    public void lastPoolDeptWas(String managerName, String deptChinese) {
        world.writeLocalPreference(POOL_DEPT_PREFERENCE_KEY, BackendChannel.dept(deptChinese));
        world.restartApp();
        world.ensureLoggedInAs(world.staff(managerName));
    }
}
