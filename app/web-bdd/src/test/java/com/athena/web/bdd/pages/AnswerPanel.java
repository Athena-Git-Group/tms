package com.athena.web.bdd.pages;

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

/** 當場回答面板：畫面中央蓋在看板或清單上面的那一塊。 */
public class AnswerPanel extends PageObject {

    public AnswerPanel(WebWorld world) {
        super(world);
    }

    /** 當場回答面板本身：名稱是「當場回答 · {房號}」的對話框。 */
    public Locator panel() {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^當場回答")));
    }

    /** 標題剛好是這一句的當場回答面板，例如「當場回答 · 305」。 */
    public Locator panelTitled(String title) {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(title.strip()).setExact(true));
    }

    /** 面板上標著「回覆住客」的回覆框。 */
    public Locator replyBox() {
        return panel().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName("回覆住客"));
    }

    /** 面板上名稱就是這個標題的那一顆常用回覆，例如「早餐時間」。 */
    public Locator quickReply(String title) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(title.strip()).setExact(true));
    }

    /** 點一則常用回覆，等畫面把它的全文帶進回覆框。 */
    public void pickQuickReply(String title) {
        new ShellFrame(world).pressAndSettle(quickReply(title));
    }

    /** 把回覆框的字全部刪掉：游標放進框裡、全選後按刪除鍵，跟人手動刪的方式一樣。 */
    public void clearReply() {
        Locator box = replyBox();
        new ShellFrame(world).press(box);
        box.selectText();
        box.press("Backspace");
    }

    /** 在回覆框目前的字後面逐字打這段文字，不先清掉；框子有字數上限時多打的字由畫面自己擋掉。 */
    public void typeIntoReply(String text) {
        Locator box = replyBox();
        new ShellFrame(world).press(box);
        box.press("End");
        box.pressSequentially(text);
    }

    /** 面板底部的「回覆並結案」。 */
    public Locator submitButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("回覆並結案").setExact(true));
    }

    /** 等面板出現、常用回覆載入完成：面板裡沒有標成載入中（{@code aria-busy="true"}）的骨架。 */
    public void waitUntilReady() {
        panel().waitFor();
        panel().locator("[aria-busy='true']").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
    }

    /** 在待派單頁按該房那一列的「當場回答…」，等面板出現、常用回覆載入完成。 */
    public void openFromTriageRow(String roomNo) {
        new ShellFrame(world).pressAndSettle(new TriagePage(world).action(roomNo, "當場回答…"));
        waitUntilReady();
    }

    /** 標題是這一句的提醒對話框，例如撞單的「這單已經被派走了」、送出失敗的「沒有送出」。 */
    public Locator notice(String title) {
        return page().getByRole(AriaRole.ALERTDIALOG, new Page.GetByRoleOptions()
                .setName(title.strip()).setExact(true));
    }

    /**
     * 按「回覆並結案」，等當場回答的請求拿到成功的回應、面板關閉。
     * 後端沒有收下時當場停下：這是給前置用的，回答沒成立的話後面的斷言只會錯得更難懂。
     */
    public void submitAndWaitClosed() {
        Response response = page().waitForResponse(
                candidate -> isAnswerRequest(candidate.request()),
                () -> new ShellFrame(world).press(submitButton()));
        if (!response.ok()) {
            throw new IllegalStateException("當場回答沒有被後端收下：HTTP " + response.status() + " " + response.text());
        }
        panel().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    /** 這個請求是不是當場回答（{@code deskAnswerTask}）：POST 到某張單的 {@code /answer}。 */
    private static boolean isAnswerRequest(Request request) {
        String path = URI.create(request.url()).getPath();
        return "POST".equals(request.method()) && path != null && ANSWER_PATH.matcher(path).matches();
    }

    private static final Pattern ANSWER_PATH = Pattern.compile(
            Pattern.quote(Network.DESK_ANSWER.path()).replace("*", "\\E[^/]+\\Q"));

    /** 面板上標著這個名稱的輸入框，例如「回覆住客」。 */
    public Locator field(String label) {
        return panel().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions()
                .setName(label.strip()).setExact(true));
    }

    /** 回覆框右下角的字數，例如「0 / 120」。面板上只有這一處是「數字 / 數字」的字。 */
    public Locator charCount() {
        return panel().getByText(Pattern.compile("^\\s*\\d+\\s*/\\s*\\d+\\s*$"));
    }

    /**
     * 面板上的每一則常用回覆，依畫面上的先後。
     * 常用回覆是可以切換「選中」的按鈕，以 {@code aria-pressed} 表示選中與否，所以只算帶這個屬性的按鈕，
     * 「取消」「回覆並結案」不算在內。
     */
    public Locator quickReplies() {
        return panel().locator("button[aria-pressed], [role=button][aria-pressed]");
    }

    /** 這一則常用回覆現在是不是選中的樣子。 */
    public static boolean selected(Locator quickReply) {
        return "true".equals(quickReply.getAttribute("aria-pressed"));
    }

    /**
     * 蓋住整個視窗、還看得到的遮罩，回每一個的描述；沒有遮罩時是空清單。
     * 遮罩是固定在視窗上、四邊貼齊視窗的一層；包著內容區的那一層是畫面本身的版面，不算遮罩。
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> coveringOverlays() {
        return (java.util.List<String>) page().evaluate("""
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
                """);
    }
}
