package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TriagePage extends PageObject {

    public TriagePage(WebWorld world) {
        super(world);
    }

    public Locator actions(String roomNo) {
        return page().getByRole(AriaRole.GROUP, new Page.GetByRoleOptions()
                .setName(roomNo + " 派單").setExact(true));
    }

    public Locator action(String roomNo, String name) {
        return actions(roomNo).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(name.strip()).setExact(true));
    }

    public Locator rowOpener(String roomNo) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo) + "(\\s|$)")));
    }

    public Locator allRowActions() {
        return page().getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName(ROW_ACTIONS_NAME));
    }

    public boolean hasRow(String roomNo) {
        return actions(roomNo).count() > 0;
    }

    public java.util.List<String> roomsInOrder() {
        java.util.List<String> rooms = new java.util.ArrayList<>();
        Matcher name = ROW_ACTIONS_IN_SNAPSHOT.matcher(page().locator("body").ariaSnapshot());
        while (name.find()) {
            rooms.add(name.group(1));
        }
        return rooms;
    }

    private static final double ASSIGN_RESPONSE_TIMEOUT_MS = 5_000;

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

    public static final Pattern DEPT_LOAD = Pattern.compile("(房務|工務) (\\d+) 單待接");

    public static final String FOOTER_LEAD = "住客打字、系統又猜不準部門的需求";

    public Locator row(String roomNo) {
        return page().locator("*")
                .filter(new Locator.FilterOptions().setHas(actions(roomNo)))
                .filter(new Locator.FilterOptions().setHas(rowOpener(roomNo)))
                .last();
    }

    public Locator header() {
        return page().locator("*")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("房務 \\d+ 單待接")))
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("工務 \\d+ 單待接")))
                .last();
    }

    public Locator deptLoad(String deptName, int count) {
        return exactText(header(), deptName + " " + count + " 單待接");
    }

    public int deptLoadCount(String deptName) {
        for (String text : textsMatching(header(), DEPT_LOAD)) {
            Matcher load = DEPT_LOAD.matcher(text);
            if (load.matches() && load.group(1).equals(deptName)) {
                return Integer.parseInt(load.group(2));
            }
        }
        throw new IllegalStateException("待派單頁頁首沒有「" + deptName + " N 單待接」");
    }

    public Locator footer() {
        return page().getByText(Pattern.compile("^" + BrowserRegex.literal(FOOTER_LEAD))).first();
    }

    public Locator emptyState(String title, String description) {
        return page().locator("*")
                .filter(new Locator.FilterOptions().setHasText(title.strip()))
                .filter(new Locator.FilterOptions().setHasText(description.strip()))
                .last();
    }

    public Locator skeletons() {
        return page().locator("[aria-busy='true']");
    }

    public java.util.List<String> textsMatching(Locator scope, Pattern pattern) {
        return scope.getByText(pattern).allInnerTexts().stream().map(ShellFrame::normalizeSpaces).toList();
    }

    public Locator exactText(Locator scope, String text) {
        return scope.getByText(text.strip(), new Locator.GetByTextOptions().setExact(true));
    }

    public String pageSubtitleText() {
        Object text = page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)).first()
                .evaluate("h => (h.parentElement.innerText || '').replace(h.innerText, '')");
        return ShellFrame.normalizeSpaces(String.valueOf(text));
    }

    public record Control(String role, String name) {
    }

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
