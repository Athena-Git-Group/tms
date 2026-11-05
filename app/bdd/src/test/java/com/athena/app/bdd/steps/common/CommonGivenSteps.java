package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

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
     * <p>F3（點開推播的去處）目前只走測試後門（{@code /seed}），後門只收姓名清單，
     * 部門／負責樓層／狀態這幾欄在真後端接線前都還沒有地方可以落地，先不送。
     */
    @假設("飯店有以下員工：")
    public void hotelHasStaff(DataTable table) {
        List<String> names = table.asMaps().stream()
                .map(row -> row.get("姓名"))
                .toList();
        world.seedStaff(names);
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
        world.seedTasks(tasks);
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

    /** 怎麼做：把裝置時間與後端 {@code Clock} 設為今日該時刻。 */
    @假設("現在時間是 {string}")
    public void nowIs(String hhmm) {
        throw new PendingException();
    }

    /** 怎麼做：把裝置切到無網路。 */
    @假設("手機目前離線")
    public void phoneOffline() {
        throw new PendingException();
    }
}
