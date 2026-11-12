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

    /**
     * 點開推播之後等目的地畫面出現可以等多久。點推播到畫面定住中間有導頁轉場加一次
     * 後端來回，整套 suite 一起跑時模擬器負載高，這段偶發會拖到十秒以上；等太短會把
     * 機器忙不過來誤報成推播導錯地方。
     */
    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(20);

    private static final Pattern POOL_HIGHLIGHTED = Pattern.compile("^待認領，(\\d+) 標亮$");
    private static final Pattern TASK_SCREEN = Pattern.compile("^(\\d+) 的任務頁$");
    private static final Pattern TICKET_DETAIL = Pattern.compile("^(W-\\d+) 的工務單詳情$");
    private static final Pattern HOME = Pattern.compile("^首頁$");
    private static final Pattern POOL_PLAIN = Pattern.compile("^待認領$");
    private static final Pattern DEPT_POOL_HIGHLIGHTED =
            Pattern.compile("^待認領，部門為(房務|工務)，(\\S+) 標亮$");

    /** 依登入者的角色與部門該停在哪個首頁外殼（與 {@code landing.dart} 同一條判定）。 */
    private static final Map<String, String> HOME_SHELL = Map.of(
            "房務首頁", "home.screen",
            "工務首頁", "engHome.screen",
            "主管首頁", "manager.home");

    private final AppiumWorld world;

    public PushThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：目前畫面符合描述；有標亮時，該房的單卡在可視範圍內並有標亮樣式；
     * 畫面是工務單詳情時，顯示的是該單號的工務單詳情頁，不是任務頁。
     *
     * 畫面寫明部門時，待認領的部門切換停在該部門、本機的部門選擇已改成它；畫面是首頁時，
     * 外殼與登入者的角色一致；主管看到的工務單詳情是主管視角。
     *
     * <p>「畫面」字串跟 {@link PushGivenSteps} 解析「去處」用的是同一套白話形式
     * （dsl.md），這裡對應回 accessibility id：標亮用 {@code pool.card.{roomNo}--highlighted}
     * （{@code lib/features/pool/pool_screen.dart} 只在標亮時才掛這個 id，存在即代表
     * 樣式也對）；任務頁沒有獨立的房號節點，房號斷言改抓導覽列標題文字（本身就含房號）。
     * 工務單詳情（{@code lib/features/ticket/ticket_screen.dart}）整頁掛
     * {@code ticket.screen}；工務視角的詳情頁一個字的單號都不寫，所以「停在哪一張單」
     * 改以房號對上：畫面讀標題區的地點（{@code ticket.hero.where}），單號那一半拿去
     * 後端查那一列的房號，兩邊要一致。只讀畫面不夠，因為畫面讀到的房號本身證明不了
     * 那是推播所指的那一張單。「不是任務頁」再用 {@code task.screen} 不得存在把關——
     * 修繕推播導錯到任務頁時，不能只靠等 ticket.screen 逾時來間接發現。
     */
    @那麼("App 停在 {string}")
    public void appStaysOn(String screenDescription) {
        Matcher highlightedMatcher = POOL_HIGHLIGHTED.matcher(screenDescription);
        Matcher taskMatcher = TASK_SCREEN.matcher(screenDescription);
        Matcher ticketMatcher = TICKET_DETAIL.matcher(screenDescription);
        Matcher deptPoolMatcher = DEPT_POOL_HIGHLIGHTED.matcher(screenDescription);

        if (deptPoolMatcher.matches()) {
            // 畫面寫明部門：待認領的部門切換停在該部門、本機的部門選擇已改成它、該單的卡標亮。
            // 本機選擇讀的是 shared_preferences 那一把鍵——畫面上的切換停對了但沒寫回本機，
            // 下次打開待認領就會跳回另一個部門。
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
                // 主管從推播進同一張單，看到的要是主管視角（managerTicket），不是工務或房務那一版。
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

    /**
     * 畫面是首頁時，外殼要與登入者的角色一致：主管首頁依主管種類顯示格子數字（房務主管待查房
     * 與待驗收兩格、工務主管只有待驗收一格，見 {@link ManagerHomeWait}）、工務首頁顯示工務今日
     * 進度、房務首頁顯示今日進度；另外兩個外殼的根節點不得同時在畫面上。劇本寫的首頁先跟
     * 這位登入者的角色與部門對過，寫錯外殼的劇本不該因為 App 也分錯而綠。
     */
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

    /**
     * 必查：以 {@code notification.group.{today|earlier}.section} 定位該分組，其中的
     * {@code notification.item.{id}.title} 文字順序剛好等於清單（沒有多也沒有少），
     * 並與後端通知的 {@code created_at} 新到舊排序一致。
     *
     * <p>標題清單以「、」分隔，純文字（非 quoted string），故用 regex 形式的
     * step definition 擷取，避免 Cucumber 把清單裡的數字切成 {@code {int}} 參數。
     */
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

        // 後端權威狀態：當事員工自己的通知裡，同一批標題照 created_at 新到舊排出來
        // 要跟畫面清單一致，而且每一則的 created_at 日期要真的落在這個分組
        //（今天＝今天的日期、更早＝今天之前）——分組歸屬不能只信畫面。「今天」以裝置
        // 生效的日期為準，畫面就是照它分組的。
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

    /**
     * 把 JDBC 回來的 {@code created_at}（timestamptz；pgjdbc 的 getObject 回
     * {@code java.sql.Timestamp}）換算成系統時區的日期。分組用系統時鐘與系統時區，
     * 跟 {@link PushGivenSteps} 建通知時刻用的是同一套。
     */
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

    /**
     * 必查：該通知項的 {@code notification.item.{id}.unread-dot} 不存在或不可見；
     * 後端該筆通知已有 {@code read_at}。畫面是點擊後才刷新的，所以用輪詢等藍點
     * 消失，不能只看當下一瞬間。
     */
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

        // 畫面定住後再讀一次後端，拿的是標為已讀之後的最終狀態。
        Object readAt = notificationRow(title).get("read_at");
        if (readAt == null) {
            throw new AssertionError("後端通知「" + title + "」的 read_at 仍為 null，尚未標記已讀");
        }
    }

    /**
     * 必查：{@code notification.mark-all-read-button} 不存在或不可見；後端該員工
     * 已經沒有 {@code read_at} 為 null 的通知。畫面是點擊後才刷新的，所以用輪詢等
     * 按鈕消失，不能只看當下一瞬間。
     */
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

    /**
     * 讀出分組區塊內通知標題的畫面順序。通知項目的 id 帶執行期的通知 uuid
     * （{@code notification.item.{id}.title}），事前列不出完整 id 清單，所以用
     * accessibility id 的前後綴比對一次撈出全部標題節點。
     *
     * <p>「其中的」用座標包含判定，不從 section 元素往下查：分組節點帶著
     * 「今天／更早」的 label，XCUITest 把這種有 label 的節點當成單一
     * accessibility element，從它往下的子查詢永遠回空（實測重現，頁面
     * snapshot 的巢狀結構與元素查詢是兩回事）。改成全頁撈標題節點，再以
     * 「元素中心點落在分組框內」判斷歸屬，最後依畫面 y 座標排出由上而下
     * 的順序。iOS 專用寫法（NSPredicate）——Android 的 UiAutomator2 driver
     * 尚未實作（{@code AppiumWorld.start()} 直接擋下），進場時再補分流。
     */
    private List<String> notificationTitlesIn(WebElement section) {
        Rectangle frame = section.getRect();
        int top = frame.getY();
        int bottom = frame.getY() + frame.getHeight();

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

    /**
     * 權威讀回：用標題撈那筆通知的 {@code id}（拼 accessibility id 用）與
     * {@code read_at}（已讀狀態斷言用），鎖在本租戶、當事員工自己的通知。
     * 場景裡她的通知標題是唯一的，撈到不只一筆代表 fixture 建錯，直接噴錯不猜。
     */
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
