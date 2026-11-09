package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
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

/** 推播模組的 Then。 */
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

    private final AppiumWorld world;

    public PushThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：目前畫面符合描述；有標亮時，該房的單卡在可視範圍內並有標亮樣式；
     * 畫面是工務單詳情時，顯示的是該單號的工務單詳情頁，不是任務頁。
     *
     * <p>「畫面」字串跟 {@link PushGivenSteps} 解析「去處」用的是同一套白話形式
     * （dsl.md），這裡對應回 accessibility id：標亮用 {@code pool.card.{roomNo}--highlighted}
     * （{@code lib/features/pool/pool_screen.dart} 只在標亮時才掛這個 id，存在即代表
     * 樣式也對）；任務頁沒有獨立的房號節點，房號斷言改抓導覽列標題文字（本身就含房號）。
     * 工務單詳情（{@code lib/features/ticket/ticket_screen.dart}）整頁掛
     * {@code ticket.screen}，單號斷言抓摘要卡的標題文字「工務單 {單號} · {房號}」；
     * 「不是任務頁」再用 {@code task.screen} 不得存在把關——修繕推播導錯到任務頁時，
     * 不能只靠等 ticket.screen 逾時來間接發現。
     */
    @那麼("App 停在 {string}")
    public void appStaysOn(String screenDescription) {
        Matcher highlightedMatcher = POOL_HIGHLIGHTED.matcher(screenDescription);
        Matcher taskMatcher = TASK_SCREEN.matcher(screenDescription);
        Matcher ticketMatcher = TICKET_DETAIL.matcher(screenDescription);

        if (highlightedMatcher.matches()) {
            String room = highlightedMatcher.group(1);
            world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
            world.waitForTestId("pool.card." + room + "--highlighted", SCREEN_TIMEOUT);
        } else if (taskMatcher.matches()) {
            String room = taskMatcher.group(1);
            world.waitForTestId("task.screen", SCREEN_TIMEOUT);
            world.waitForLabelContaining(room + " 的任務頁", SCREEN_TIMEOUT);
        } else if (ticketMatcher.matches()) {
            String ticketNo = ticketMatcher.group(1);
            world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
            world.waitForLabelContaining("工務單 " + ticketNo, SCREEN_TIMEOUT);
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
     * 必查：任務頁顯示工務已修好的提示，並有可點的恢復按鈕；後端任務仍為 interrupted。
     * accessibility id：{@code task.resume-banner}、{@code task.status-row.resume-button}。
     */
    @那麼("任務頁提示可以恢復 {string}")
    public void taskDetailOffersResume(String task) {
        world.waitForTestId("task.resume-banner", SCREEN_TIMEOUT);
        world.waitForTestId("task.status-row.resume-button", SCREEN_TIMEOUT);
        world.waitForLabelContaining(task, SCREEN_TIMEOUT);
    }

    // ──── 通知中心（T309–T311 落點） ────

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
        //（今天＝今天的日期、更早＝今天之前）——分組歸屬不能只信畫面。
        boolean expectToday = "today".equals(groupKey);
        LocalDate today = LocalDate.now();
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
     * 跟 {@link PushGivenSteps} 建通知時刻用的是同一套（詳見那邊 parseMoment 的說明）。
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

    /**
     * 必查：該通知項的 {@code notification.item.{id}.unread-dot} 可見；
     * 後端該筆通知的 {@code read_at} 仍為 null（未讀）。
     */
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

    /** 必查：通知中心顯示該空狀態文字，沒有任何通知項目。 */
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

    /** 把「A、B、C」形式的標題清單切成有序 List，順手去掉前後空白。 */
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
