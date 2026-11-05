package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 通知中心句型的 Then（dsl.md 通知中心段落）：每句依 dsl.md 的「必查」清單斷言。
 * 本類別只放通知中心的句型。
 */
public class PushThenSteps {

    private final ScenarioWorld world;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;

    public PushThenSteps(
            ScenarioWorld world, DbProbe dbProbe, TenantFixtures tenantFixtures, MutableClock clock) {
        this.world = world;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
    }

    /**
     * dsl.md 必查／呈現結果：以通知建立時刻與目前 {@code Clock} 所在日期比較，
     * 同一天為「今天」、更早日期為「更早」，該分組內的標題剛好等於清單。
     *
     * <p>依賴同一 scenario 前面「查看通知中心」那句留下的 {@link ScenarioWorld#lastResponse()}
     * （回應是未分組、依 createdAt 新到舊排序的陣列——contracts {@code listNotifications}
     * 說明本端點不分組，分組是呈現層／測試端自己依 createdAt 與呼叫者所在時區的日期算的）。
     *
     * <p>dsl.md 這句在 Gherkin 裡「{標題清單}」不加引號（以「、」分隔的裸文字），比照
     * repair 的「工務待認領順序為」與 today 的「待開始的單推薦順序為」，用
     * {@code ^...$} 讓 Cucumber 當 Regular Expression 編譯；「{員工}」用雙引號、
     * 「{分組}」用「」全形角括號包住，都是句型本身的固定字面。
     */
    @那麼("^\"([^\"]+)\" 的通知中心「([^」]+)」分組包含 (.+)$")
    public void notificationCenterGroupContains(String staffName, String group, String rawTitleList) {
        if (!"今天".equals(group) && !"更早".equals(group)) {
            throw new IllegalArgumentException("通知中心分組只支援「今天」「更早」，實際=" + group);
        }
        List<String> expected = Arrays.asList(rawTitleList.split("、"));

        JsonNode json = world.lastResponse().json();
        LocalDate today = LocalDate.now(clock);
        List<String> actual = new ArrayList<>();
        if (json != null) {
            for (JsonNode item : json) {
                OffsetDateTime createdAt = OffsetDateTime.parse(item.path("createdAt").asText());
                LocalDate createdDate = createdAt.atZoneSameInstant(clock.getZone()).toLocalDate();
                String actualGroup = createdDate.equals(today) ? "今天" : "更早";
                if (actualGroup.equals(group)) {
                    actual.add(item.path("title").asText(null));
                }
            }
        }

        if (!expected.equals(actual)) {
            throw new AssertionError(
                    staffName + " 的通知中心「" + group + "」分組預期 " + expected + "，實際 " + actual);
        }
    }

    /**
     * dsl.md 必查：呈現結果：通知清單標題的順序剛好等於清單。前一句「查看通知中心」
     * （{@code listNotifications}，{@code GET /notifications}）的回應本身就是直接
     * 依 {@code createdAt} 新到舊排序好的陣列（見 groups/知識與通知/通知中心.yaml），
     * 這裡不重打一次 API，只從 {@link ScenarioWorld#lastResponse()} 取每筆的 title
     * 跟預期清單逐一核對順序與筆數。
     *
     * <p>dsl.md 的 {@code 標題清單} 是裸文字（不加引號），以 {@code 、} 分隔，標題本身
     * 可能含逗號、句點等中文標點（例如「連續 6 天零退回，繼續保持。」），所以這句用
     * regex 而非 Cucumber Expression：{@code {string}} 只能配對帶引號的片段，無法配對
     * 到行尾為止的裸文字。
     */
    @那麼("^\"([^\"]+)\" 的通知中心依序包含 (.+)$")
    public void notificationCenterListsInOrder(String staffName, String titleList) {
        List<String> expected = splitTitles(titleList);
        List<String> actual = new ArrayList<>();
        for (JsonNode item : world.lastResponse().json()) {
            actual.add(item.path("title").asText(null));
        }
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "\"" + staffName + "\" 的通知中心依序包含預期 " + expected + "，實際 " + actual);
        }
    }

    private static List<String> splitTitles(String raw) {
        return Arrays.stream(raw.split("、")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * dsl.md 必查：權威狀態：待補的通知紀錄 {@code read_at} 已寫入該筆。
     *
     * <p>dsl.md 這句只給標題，沒有另外給通知 id；比照
     * {@link PushWhenSteps#staffMarksNotificationRead} 的做法，以該員工、該標題在
     * {@code notifications} 表查出最新一筆，斷言其 {@code read_at} 已寫入。
     */
    @那麼("{string} 的 {string} 通知已讀")
    public void staffNotificationIsRead(String staffName, String title) {
        long tenantId = tenantFixtures.testTenantId();
        UUID staffId = UUID.fromString(world.staffId(staffName));

        List<Map<String, Object>> rows = dbProbe.queryMaps(
                """
                SELECT read_at FROM notifications
                WHERE tenant_id = ? AND recipient_staff_id = ? AND title = ?
                ORDER BY created_at DESC
                """,
                tenantId, staffId, title);
        if (rows.isEmpty()) {
            throw new AssertionError(staffName + " 名下找不到標題「" + title + "」的通知");
        }
        Object readAt = rows.get(0).get("read_at");
        if (readAt == null) {
            throw new AssertionError(
                    "預期 " + staffName + " 的「" + title + "」通知已讀（read_at 應已寫入），實際 read_at=null");
        }
    }

    /**
     * dsl.md 必查：權威狀態——該員工名下每一筆通知紀錄的 {@code read_at} 都已寫入。
     * 直接查 notifications 表，該員工名下 {@code read_at IS NULL} 的筆數應為 0。
     */
    @那麼("{string} 的所有通知都已讀")
    public void allNotificationsAreRead(String staffName) {
        long tenantId = tenantFixtures.testTenantId();
        UUID staffId = UUID.fromString(world.staffId(staffName));

        long unreadCount = dbProbe.count(
                "SELECT count(*) FROM notifications WHERE tenant_id = ? AND recipient_staff_id = ? AND read_at IS NULL",
                tenantId, staffId);
        if (unreadCount != 0) {
            throw new AssertionError(
                    staffName + " 名下仍有 " + unreadCount + " 筆通知未讀（read_at 為 null），預期全部已讀");
        }
    }
}
