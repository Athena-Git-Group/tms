package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.JsResult;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.net.URI;
import java.util.regex.Pattern;

public class AnswerPanel extends PageObject {

    public AnswerPanel(WebWorld world) {
        super(world);
    }

    public Locator panel() {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^當場回答")));
    }

    public Locator panelTitled(String title) {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(title.strip()).setExact(true));
    }

    public Locator replyBox() {
        return panel().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName("回覆住客"));
    }

    public Locator quickReply(String title) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(title.strip()).setExact(true));
    }

    public void pickQuickReply(String title) {
        new ShellFrame(world).pressAndSettle(quickReply(title));
    }

    public void clearReply() {
        Locator box = replyBox();
        new ShellFrame(world).press(box);
        box.selectText();
        box.press("Backspace");
    }

    public void typeIntoReply(String text) {
        Locator box = replyBox();
        new ShellFrame(world).press(box);
        box.press("End");
        box.pressSequentially(text);
    }

    public Locator submitButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("回覆並結案").setExact(true));
    }

    public void waitUntilReady() {
        panel().waitFor();
        panel().locator("[aria-busy='true']").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
    }

    public void openFromTriageRow(String roomNo) {
        new ShellFrame(world).pressAndSettle(new TriagePage(world).action(roomNo, "當場回答…"));
        waitUntilReady();
    }

    public Locator notice(String title) {
        return page().getByRole(AriaRole.ALERTDIALOG, new Page.GetByRoleOptions()
                .setName(title.strip()).setExact(true));
    }

    public void submitAndWaitClosed() {
        Response response = page().waitForResponse(
                candidate -> isAnswerRequest(candidate.request()),
                () -> new ShellFrame(world).press(submitButton()));
        if (!response.ok()) {
            throw new IllegalStateException("當場回答沒有被後端收下：HTTP " + response.status() + " " + response.text());
        }
        panel().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    private static boolean isAnswerRequest(Request request) {
        String path = URI.create(request.url()).getPath();
        return "POST".equals(request.method()) && path != null && ANSWER_PATH.matcher(path).matches();
    }

    private static final Pattern ANSWER_PATH = Pattern.compile(
            Pattern.quote(Network.DESK_ANSWER.path()).replace("*", "\\E[^/]+\\Q"));

    public Locator field(String label) {
        return panel().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions()
                .setName(label.strip()).setExact(true));
    }

    public Locator charCount() {
        return panel().getByText(Pattern.compile("^\\s*\\d+\\s*/\\s*\\d+\\s*$"));
    }

    public Locator quickReplies() {
        return panel().locator("button[aria-pressed], [role=button][aria-pressed]");
    }

    public static boolean selected(Locator quickReply) {
        return "true".equals(quickReply.getAttribute("aria-pressed"));
    }

    public java.util.List<String> coveringOverlays() {
        return JsResult.strings(page().evaluate("""
                () => {
                  const vw = window.innerWidth, vh = window.innerHeight;
                  const main = document.querySelector('main, [role=main]');
                  return [...document.querySelectorAll('body *')].filter(el => {
                    const style = getComputedStyle(el);
                    if (style.position !== 'fixed' || style.display === 'none' || style.visibility === 'hidden'
                        || Number(style.opacity) === 0) return false;
                    const r = el.getBoundingClientRect();
                    if (r.left > 1 || r.top > 1 || r.right < vw - 1 || r.bottom < vh - 1) return false;
                    return !(main && el.contains(main));
                  }).map(el => el.tagName.toLowerCase()
                      + (el.getAttribute('role') ? '[role=' + el.getAttribute('role') + ']' : '')
                      + (el.getAttribute('aria-label') ? '「' + el.getAttribute('aria-label') + '」' : ''));
                }
                """));
    }
}
