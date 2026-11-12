package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.common.CommonGivenSteps;
import com.athena.app.bdd.steps.manager.ManagerHomeWait;
import io.appium.java_client.AppiumBy;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PushThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(20);

    private static final Pattern POOL_HIGHLIGHTED = Pattern.compile("^待認領，(\\d+) 標亮$");
    private static final Pattern TASK_SCREEN = Pattern.compile("^(\\d+) 的任務頁$");
    private static final Pattern TICKET_DETAIL = Pattern.compile("^(W-\\d+) 的工務單詳情$");
    private static final Pattern HOME = Pattern.compile("^首頁$");
    private static final Pattern POOL_PLAIN = Pattern.compile("^待認領$");
    private static final Pattern DEPT_POOL_HIGHLIGHTED =
            Pattern.compile("^待認領，部門為(房務|工務)，(\\S+) 標亮$");

    private static final Map<String, String> HOME_SHELL = Map.of(
            "房務首頁", "home.screen",
            "工務首頁", "engHome.screen",
            "主管首頁", "manager.home");

    private final AppiumWorld world;

    public PushThenSteps(AppiumWorld world) {
        this.world = world;
    }

    @那麼("App 停在 {string}")
    public void appStaysOn(String screenDescription) {
        Matcher highlightedMatcher = POOL_HIGHLIGHTED.matcher(screenDescription);
        Matcher taskMatcher = TASK_SCREEN.matcher(screenDescription);
        Matcher ticketMatcher = TICKET_DETAIL.matcher(screenDescription);
        Matcher deptPoolMatcher = DEPT_POOL_HIGHLIGHTED.matcher(screenDescription);

        if (deptPoolMatcher.matches()) {
            String dept = BackendChannel.dept(deptPoolMatcher.group(1));
            String key = deptPoolMatcher.group(2);
            world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
            WebElement side = world.waitForTestId("pool.dept-switch." + dept, SCREEN_TIMEOUT);
            if (!side.isSelected()) {
                throw new AssertionError("待認領的部門切換應停在 " + dept + "，pool.dept-switch." + dept + " 不是選中狀態");
            }
            world.waitForTestId("pool.card." + key + "--highlighted", SCREEN_TIMEOUT);
            String local = world.readLocalPreference(CommonGivenSteps.POOL_DEPT_PREFERENCE_KEY);
            if (!dept.equals(local)) {
                throw new AssertionError("App 本機的待認領部門選擇應為 " + dept + "，實際「" + local + "」");
            }
        } else if (HOME_SHELL.containsKey(screenDescription)) {
            assertHomeShell(screenDescription);
        } else if (highlightedMatcher.matches()) {
            String room = highlightedMatcher.group(1);
            world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
            world.waitForTestId("pool.card." + room + "--highlighted", SCREEN_TIMEOUT);
        } else if (taskMatcher.matches()) {
            String room = taskMatcher.group(1);
            world.waitForTestId("task.screen", SCREEN_TIMEOUT);
            world.waitForTestId("task.nav." + room, SCREEN_TIMEOUT);
        } else if (ticketMatcher.matches()) {
            String ticketNo = ticketMatcher.group(1);
            world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
            if ("manager".equals(world.loggedInStaff().role())) {
                world.waitForTestId("managerTicket", SCREEN_TIMEOUT);
            }
            String roomNo = roomNoOf(ticketNo);
            String onScreen = world.waitForLabelOf("ticket.hero.where", roomNo, SCREEN_TIMEOUT);
            if (!roomNo.equals(onScreen)) {
                throw new AssertionError("預期停在 " + ticketNo + " 的工務單詳情頁（房號 "
                        + roomNo + "），但標題區的地點讀到「" + onScreen + "」");
            }
            List<WebElement> taskScreens = world.driver().findElements(world.byTestId("task.screen"));
            if (!taskScreens.isEmpty()) {
                throw new AssertionError("預期停在 " + ticketNo
                        + " 的工務單詳情頁，但畫面上同時找得到任務頁（task.screen）");
            }
        } else if (HOME.matcher(screenDescription).matches()) {
            world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        } else if (POOL_PLAIN.matcher(screenDescription).matches()) {
            world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
        } else {
            throw new IllegalArgumentException("認不得的畫面字串：「" + screenDescription + "」");
        }
    }

    private void assertHomeShell(String homeChinese) {
        var staff = world.loggedInStaff();
        String expectedHome = "manager".equals(staff.role()) ? "主管首頁"
                : "engineering".equals(staff.dept()) ? "工務首頁" : "房務首頁";
        if (!expectedHome.equals(homeChinese)) {
            throw new AssertionError("登入者「" + staff.name() + "」的角色是 " + staff.role() + "、部門是 "
                    + staff.dept() + "，依分流應停在「" + expectedHome + "」，劇本卻寫「" + homeChinese + "」");
        }
        String shell = HOME_SHELL.get(homeChinese);
        world.waitForTestId(shell, SCREEN_TIMEOUT);
        switch (homeChinese) {
            case "主管首頁" -> ManagerHomeWait.awaitExactly(world, staff.name(), SCREEN_TIMEOUT);
            case "工務首頁" -> world.waitForTestId("engHome.progress.in-hand-count", SCREEN_TIMEOUT);
            default -> world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);
        }
        for (String other : HOME_SHELL.values()) {
            if (!other.equals(shell) && !world.driver().findElements(world.byTestId(other)).isEmpty()) {
                throw new AssertionError("停在「" + homeChinese + "」時不該同時找得到 " + other);
            }
        }
    }

    private String roomNoOf(String ticketNo) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                """
                SELECT r.room_no FROM repair_tickets rt
                JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                WHERE rt.tenant_id = ? AND rt.ticket_no = ?
                """, world.backend().ensureTenant(), ticketNo);
        if (rows.size() != 1) {
            throw new IllegalStateException("單號「" + ticketNo + "」在後端應恰有 1 張工務單，實際 "
                    + rows.size() + " 張");
        }
        return (String) rows.get(0).get("room_no");
    }

    @那麼("任務頁提示可以恢復 {string}")
    public void taskDetailOffersResume(String task) {
        world.waitForTestId("task.resume-banner", SCREEN_TIMEOUT);
        world.waitForTestId("task.status-row.resume-button", SCREEN_TIMEOUT);
        world.waitForLabelContaining(task, SCREEN_TIMEOUT);
    }

    @那麼("^通知中心「(今天|更早)」分組依序包含 (.+)$")
    public void notificationGroupContainsInOrder(String groupName, String titleListText) {
        List<String> expected = splitTitleList(titleListText);
        String groupKey = "今天".equals(groupName) ? "today" : "earlier";
        WebElement section = world.waitForTestId(
                "notification.group." + groupKey + ".section", SCREEN_TIMEOUT);

        List<String> onScreen = notificationTitlesIn(section);
        if (!expected.equals(onScreen)) {
            throw new AssertionError("通知中心「" + groupName + "」分組的標題順序不符：預期 "
                    + expected + "，畫面上是 " + onScreen);
        }

        boolean expectToday = "today".equals(groupKey);
        LocalDate today = world.deviceNow().toLocalDate();
        List<String> backendOrder = new ArrayList<>();
        for (Map<String, Object> row : world.backend().queryRows(
                "SELECT title, created_at FROM notifications WHERE tenant_id = ? AND recipient_staff_id = ? "
                        + "ORDER BY created_at DESC",
                world.backend().ensureTenant(), world.notificationViewer().id())) {
            String title = (String) row.get("title");
            if (!expected.contains(title)) {
                continue;
            }
            backendOrder.add(title);

            LocalDate createdDate = toLocalDate(row.get("created_at"));
            boolean isToday = createdDate.equals(today);
            if (isToday != expectToday) {
                throw new AssertionError("通知「" + title + "」的 created_at 日期是 " + createdDate
                        + "，不該落在「" + groupName + "」分組（今天是 " + today + "）");
            }
        }
        if (!expected.equals(backendOrder)) {
            throw new AssertionError("後端 notifications 依 created_at 新到舊的順序不符：預期 "
                    + expected + "，後端是 " + backendOrder);
        }
    }

    private static LocalDate toLocalDate(Object createdAt) {
        if (createdAt instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (createdAt instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        }
        throw new IllegalStateException("認不得的 created_at 型別：" + createdAt);
    }

    @那麼("{string} 的通知顯示未讀藍點")
    public void notificationShowsUnreadDot(String title) {
        Map<String, Object> row = notificationRow(title);
        world.waitForTestId("notification.item." + row.get("id") + ".unread-dot", SCREEN_TIMEOUT);
        if (row.get("read_at") != null) {
            throw new AssertionError("後端通知「" + title + "」已有 read_at，不該顯示未讀藍點");
        }
    }

    @那麼("{string} 的通知沒有未讀藍點")
    public void notificationHasNoUnreadDot(String title) {
        Map<String, Object> row = notificationRow(title);
        String dotId = "notification.item." + row.get("id") + ".unread-dot";

        long deadline = System.nanoTime() + SCREEN_TIMEOUT.toNanos();
        while (true) {
            List<WebElement> dots = world.driver().findElements(world.byTestId(dotId));
            if (dots.isEmpty() || dots.stream().noneMatch(WebElement::isDisplayed)) {
                break;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("通知「" + title + "」的未讀藍點（" + dotId
                        + "）在 " + SCREEN_TIMEOUT.getSeconds() + "s 內沒有消失");
            }
            sleep(200);
        }

        Object readAt = notificationRow(title).get("read_at");
        if (readAt == null) {
            throw new AssertionError("後端通知「" + title + "」的 read_at 仍為 null，尚未標記已讀");
        }
    }

    @那麼("通知中心沒有「全部標為已讀」")
    public void notificationCenterHasNoMarkAllReadButton() {
        world.waitForTestId("notification.screen", SCREEN_TIMEOUT);

        long deadline = System.nanoTime() + SCREEN_TIMEOUT.toNanos();
        while (true) {
            List<WebElement> buttons =
                    world.driver().findElements(world.byTestId("notification.mark-all-read-button"));
            if (buttons.isEmpty() || buttons.stream().noneMatch(WebElement::isDisplayed)) {
                break;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("通知中心的「全部標為已讀」在 "
                        + SCREEN_TIMEOUT.getSeconds() + "s 內沒有消失");
            }
            sleep(200);
        }

        List<Map<String, Object>> unread = world.backend().queryRows(
                "SELECT id FROM notifications WHERE tenant_id = ? AND recipient_staff_id = ? AND read_at IS NULL",
                world.backend().ensureTenant(), world.notificationViewer().id());
        if (!unread.isEmpty()) {
            throw new AssertionError("後端還有 " + unread.size() + " 則未讀通知，不該收掉「全部標為已讀」");
        }
    }

    @那麼("通知中心顯示空狀態 {string}")
    public void notificationCenterShowsEmptyState(String message) {
        world.waitForTestId("notification.screen", SCREEN_TIMEOUT);
        WebElement emptyState = world.waitForTestId("notification.empty-state", SCREEN_TIMEOUT);
        String text = emptyState.getText();
        if (text == null || !text.contains(message)) {
            throw new AssertionError("空狀態文字不符：預期含「" + message + "」，實際是「" + text + "」");
        }
        List<WebElement> items = world.driver().findElements(AppiumBy.iOSNsPredicateString(
                "name BEGINSWITH 'notification.item.'"));
        if (!items.isEmpty()) {
            throw new AssertionError("通知中心顯示空狀態時不該有通知項目，實際有 " + items.size() + " 個");
        }
    }

    private static List<String> splitTitleList(String titleListText) {
        List<String> titles = new ArrayList<>();
        for (String piece : titleListText.split("、")) {
            titles.add(piece.strip());
        }
        return titles;
    }

    private List<String> notificationTitlesIn(WebElement section) {
        Rectangle frame = section.getRect();
        int top = frame.getY();
        int bottom = frame.getY() + frame.getHeight();

        // XCUITest 把帶 label 的節點當成單一 accessibility element，從分組節點往下的子查詢一律回空，
        // 所以全頁撈標題再用座標判斷歸屬。
        List<WebElement> all = world.driver().findElements(AppiumBy.iOSNsPredicateString(
                "name BEGINSWITH 'notification.item.' AND name ENDSWITH '.title'"));
        List<WebElement> inSection = new ArrayList<>();
        for (WebElement title : all) {
            Rectangle r = title.getRect();
            int centerY = r.getY() + r.getHeight() / 2;
            if (centerY >= top && centerY <= bottom) {
                inSection.add(title);
            }
        }
        inSection.sort(Comparator.comparingInt(e -> e.getRect().getY()));

        List<String> titles = new ArrayList<>();
        for (WebElement title : inSection) {
            titles.add(title.getText());
        }
        return titles;
    }

    private Map<String, Object> notificationRow(String title) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT id, read_at FROM notifications WHERE tenant_id = ? AND recipient_staff_id = ? AND title = ?",
                world.backend().ensureTenant(), world.notificationViewer().id(), title);
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "預期剛好一筆標題為「" + title + "」的通知，實際 " + rows.size() + " 筆");
        }
        return rows.get(0);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待未讀藍點消失時被中斷", e);
        }
    }
}
