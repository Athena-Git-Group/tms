package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

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
     * 怎麼做：透過後端測試資料建立員工與班次。權威狀態落地：後端 staff 資料已存在；
     * 待開通與停用的員工不出現在挑人名單、不能搶單、不收推播。回寫：登入帳號。
     *
     * <p>姓名與部門兩欄必填，部門中文→權威 enum 的對照在
     * {@link com.athena.app.bdd.BackendChannel#dept}（與 tms Terms 同表）；
     * 負責樓層／狀態欄位的語意等對應模組的 stepdef 實作時再接到 {@code world.backend()}。
     */
    @假設("飯店有以下員工：")
    public void hotelHasStaff(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            world.seedStaff(requireCell(row, "姓名"), requireCell(row, "部門"), optionalInt(row, "負責樓層"));
        }
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
     * 怎麼做：透過後端測試資料建立任務。以該員工身分登入 App，停在首頁。
     * 權威狀態落地：後端任務真相已存在。
     */
    @假設("{string} 名下有以下任務：")
    public void staffHasTasks(String staffName, DataTable table) {
        List<AppiumWorld.TaskFixture> tasks = table.asMaps().stream()
                .map(CommonGivenSteps::toTaskFixture)
                .toList();
        world.seedTasks(staffName, tasks);
    }

    private static AppiumWorld.TaskFixture toTaskFixture(Map<String, String> row) {
        String elapsedMinutesText = row.get("已做分鐘");
        int elapsedMinutes = (elapsedMinutesText == null || elapsedMinutesText.isBlank())
                ? 0
                : Integer.parseInt(elapsedMinutesText.trim());
        String pausedReason = row.get("停住原因");
        if (pausedReason != null && pausedReason.isBlank()) {
            pausedReason = null;
        }
        return new AppiumWorld.TaskFixture(
                row.get("房號"),
                row.get("內容"),
                row.get("狀態"),
                elapsedMinutes,
                pausedReason);
    }

    /**
     * 怎麼做：把裝置時間與後端 {@code Clock} 設為今日該時刻。
     *
     * <p>裝置半邊：注入測試後門的 {@code POST /clock}（{@code TestHooks.injectedClockIso}），
     * 需要被控時間的畫面行為從這個注入值讀。後端半邊目前「設不進去」：跑著的後端用
     * 的是 {@code ClockConfig} 的系統時鐘，沒有任何可供外部調整的端點（tms 自己的
     * BDD 是同 JVM 直接撥 MutableClock，harness 構不到）——所以這句注入的是
     * 「今日的該時刻」而非任意日期，fixture 的「今天」與後端的「今天」天然同一天，
     * 分組／日期類斷言不受兩個時鐘差異影響。等後端開出測試用 Clock 端點時，這裡
     * 要改成兩邊一起設。
     */
    @假設("現在時間是 {string}")
    public void nowIs(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        OffsetDateTime moment = ZonedDateTime
                .of(LocalDate.now(), time, ZoneId.systemDefault())
                .toOffsetDateTime();
        world.postBackdoorJson("/clock", "{\"isoTime\":\"" + moment + "\"}");
    }

    /** 怎麼做：把裝置切到無網路。 */
    @假設("手機目前離線")
    public void phoneOffline() {
        throw new PendingException();
    }
}
