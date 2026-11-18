package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;

/** 待派單頁（路徑 {@code /triage}）：待派單的清單與每一列的動作。 */
public class TriagePage extends PageObject {

    public TriagePage(WebWorld world) {
        super(world);
    }

    /** 該房那一列右側的三顆動作，收在名稱是「{房號} 派單」的群組裡。 */
    public Locator actions(String roomNo) {
        return page().getByRole(AriaRole.GROUP, new Page.GetByRoleOptions()
                .setName(roomNo + " 派單").setExact(true));
    }

    /** 該房那一列上名稱相符的動作，例如「派給房務」「當場回答…」。 */
    public Locator action(String roomNo, String name) {
        return actions(roomNo).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(name.strip()).setExact(true));
    }

    /**
     * 該房那一列上動作以外、點了會開詳情面板的地方：列左側那一塊，可及性名稱由房號開頭。
     * 用「房號後面接空白或結尾」比對，305 不會連 3051 一起找到。
     */
    public Locator rowOpener(String roomNo) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo) + "(\\s|$)")));
    }

    /** 清單上每一列的動作群組，依由上到下的順序；每一列剛好一組，所以也代表清單的每一列。 */
    public Locator allRowActions() {
        return page().getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName(ROW_ACTIONS_NAME));
    }

    /** 清單上這一列在不在：該房的動作群組還找得到就算在。 */
    public boolean hasRow(String roomNo) {
        return actions(roomNo).count() > 0;
    }

    /**
     * 清單各列的房號，由上到下。房號取自每一列動作群組的名稱「{房號} 派單」，
     * 名稱是輔助科技讀到的那一個，從群組的無障礙樹摘要裡拿，不從版面去猜。
     */
    public java.util.List<String> roomsInOrder() {
        java.util.List<String> rooms = new java.util.ArrayList<>();
        Locator groups = allRowActions();
        for (int i = 0; i < groups.count(); i++) {
            java.util.regex.Matcher name = ROW_ACTIONS_IN_SNAPSHOT.matcher(groups.nth(i).ariaSnapshot());
            if (!name.find()) {
                throw new IllegalStateException("讀不出這一列動作群組的名稱：" + groups.nth(i).ariaSnapshot());
            }
            rooms.add(name.group(1));
        }
        return rooms;
    }

    /** 按下派單後最久等這麼久讓派單請求回來。 */
    private static final double ASSIGN_RESPONSE_TIMEOUT_MS = 5_000;

    /**
     * 按該房那一列的「派給{部門}」，等到這張單的派單請求回成功、畫面底部出現這張單的浮條才回來。
     *
     * <p>畫面是按下去就先照成功的樣子變，浮條在請求回來之前就會出現，所以另外等請求的回應。
     * 只等浮條的話，後端其實拒絕了也看不出來，後面的步驟就會在一張沒派出去的單上操作。
     *
     * @param taskId 這一列那張單的 id，用來認出是哪一個派單請求
     * @param deptTerm 部門的說法，房務或工務
     */
    public void assignAndWaitForToast(String roomNo, java.util.UUID taskId, String deptTerm) {
        String dept = deptTerm.strip();
        String assignPath = "/api/v1/desk/tasks/" + taskId + "/assign";
        ShellFrame shell = new ShellFrame(world);
        com.microsoft.playwright.Response response = page().waitForResponse(
                r -> "POST".equals(r.request().method()) && r.url().split("[?#]")[0].endsWith(assignPath),
                new Page.WaitForResponseOptions().setTimeout(ASSIGN_RESPONSE_TIMEOUT_MS),
                () -> shell.press(action(roomNo, "派給" + dept)));
        if (!response.ok()) {
            throw new IllegalStateException("「" + roomNo + "」派給" + dept + "沒有成功：HTTP "
                    + response.status() + "，body=" + response.text());
        }
        shell.assignToast().filter(new Locator.FilterOptions().setHasText("已派給" + dept + "（" + roomNo + "）"))
                .waitFor();
    }

    private static final Pattern ROW_ACTIONS_NAME = Pattern.compile("^\\S+ 派單$");

    private static final Pattern ROW_ACTIONS_IN_SNAPSHOT = Pattern.compile("group \"(\\S+) 派單\"");

    /*
     * 以下是待派單頁結果句讀畫面用的方法：一整列、頁首、頁尾說明、空狀態、骨架與頁副標。
     * 這些區塊本身沒有角色或名稱可找，所以都從區塊裡看得到的文字或有名字的元素往外找，
     * 取同時包住這些東西的最內層元素。外層的區塊（清單、內容區、整頁）也同時包住它們，
     * 但在文件順序裡排在前面，所以符合的元素取最後一個，就是最貼近的那一層。
     */

    /** 頁首寫房務、工務各有幾張單待接的那兩段字。 */
    public static final Pattern DEPT_LOAD = Pattern.compile("(房務|工務) \\d+ 單待接");

    /** 頁尾說明開頭的幾個字，照待派單頁的畫面文字。 */
    public static final String FOOTER_LEAD = "只有住客打字";

    /** 該房那一整列：同時包住列左側開詳情的那一塊與右側動作群組的最內層元素。 */
    public Locator row(String roomNo) {
        return page().locator("*")
                .filter(new Locator.FilterOptions().setHas(actions(roomNo)))
                .filter(new Locator.FilterOptions().setHas(rowOpener(roomNo)))
                .last();
    }

    /** 頁首：同時寫著房務與工務待接張數的最內層元素。清單是空的時候沒有頁首，找不到。 */
    public Locator header() {
        return page().locator("*")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("房務 \\d+ 單待接")))
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("工務 \\d+ 單待接")))
                .last();
    }

    /** 頁尾說明：以「只有住客打字」開頭的那一段字。 */
    public Locator footer() {
        return page().getByText(Pattern.compile("^" + BrowserRegex.literal(FOOTER_LEAD))).first();
    }

    /** 空狀態：同時寫著標題與說明的最內層元素。 */
    public Locator emptyState(String title, String description) {
        return page().locator("*")
                .filter(new Locator.FilterOptions().setHasText(title.strip()))
                .filter(new Locator.FilterOptions().setHasText(description.strip()))
                .last();
    }

    /** 標成載入中（{@code aria-busy="true"}）的地方，也就是骨架。 */
    public Locator skeletons() {
        return page().locator("[aria-busy='true']");
    }

    /** 範圍內文字整段符合這個樣式的元素，各自的文字；連續空白併成一個。 */
    public java.util.List<String> textsMatching(Locator scope, Pattern pattern) {
        return scope.getByText(pattern).allInnerTexts().stream().map(ShellFrame::normalizeSpaces).toList();
    }

    /** 範圍內文字剛好是這一句的元素。 */
    public Locator exactText(Locator scope, String text) {
        return scope.getByText(text.strip(), new Locator.GetByTextOptions().setExact(true));
    }

    /**
     * 工具列上頁副標的文字：頁標題所在那一塊扣掉頁標題本身剩下的字。
     * 頁副標空著時是空字串。
     */
    public String pageSubtitleText() {
        Object text = page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)).first()
                .evaluate("h => (h.parentElement.innerText || '').replace(h.innerText, '')");
        return ShellFrame.normalizeSpaces(String.valueOf(text));
    }

    /** 一個可以操作的東西：角色與可及性名稱。 */
    public record Control(String role, String name) {
    }

    /**
     * 範圍內每一個可以操作的東西，依畫面上的先後。
     * 從無障礙樹摘要讀，拿到的是輔助科技看到的角色與名稱，和使用者在畫面上能按的東西一致。
     */
    public java.util.List<Control> controlsIn(Locator scope) {
        java.util.List<Control> controls = new java.util.ArrayList<>();
        java.util.regex.Matcher line = CONTROL_IN_SNAPSHOT.matcher(scope.ariaSnapshot());
        while (line.find()) {
            String name = line.group(2) == null ? "" : line.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
            controls.add(new Control(line.group(1), name));
        }
        return controls;
    }

    private static final Pattern CONTROL_IN_SNAPSHOT = Pattern.compile(
            "(?m)^\\s*- (button|link|menuitem|menuitemcheckbox|menuitemradio|combobox|checkbox|radio|switch"
                    + "|textbox|searchbox|slider|spinbutton|tab|option)(?: \"((?:[^\"\\\\]|\\\\.)*)\")?");
}
