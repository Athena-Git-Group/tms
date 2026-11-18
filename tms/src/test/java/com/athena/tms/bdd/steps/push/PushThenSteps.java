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

    // 標題清單在 Gherkin 裡不加引號，{string} 配不到沒有引號的文字；用 ^...$ 讓 Cucumber 當成 Regular Expression 編譯。
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
