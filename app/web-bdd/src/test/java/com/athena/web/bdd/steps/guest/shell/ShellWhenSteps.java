package com.athena.web.bdd.steps.guest.shell;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.LanguageMenu;
import com.athena.web.bdd.pages.guest.PhoneLayout;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.當;

/** 「共用外框」模組的 When 句型。 */
public class ShellWhenSteps {

    private final WebWorld world;

    private final GuestFrame frame;

    private final LanguageMenu language;

    private final PhoneLayout layout;

    public ShellWhenSteps(WebWorld world) {
        this.world = world;
        this.frame = new GuestFrame(world);
        this.language = new LanguageMenu(world);
        this.layout = new PhoneLayout(world);
    }

    /** 觸控設定下用點觸；等語言選單出現，最多 5 秒。 */
    @當("住客點語言膠囊")
    public void tapLanguageCapsule() {
        Locator capsule = language.capsule();
        if (capsule.count() == 0) {
            throw new AssertionError("畫面上找不到語言膠囊（名稱是 " + LanguageMenu.CAPSULE_NAMES + " 其中之一的按鈕）；畫面上的文字是 "
                    + ShellFrame.linesOf(world.page().locator("body").innerText()));
        }
        frame.settleAfter(() -> {
            if (world.profile().touch()) {
                capsule.first().tap();
            } else {
                capsule.first().click();
            }
        });
        Page page = world.page();
        frame.waitAtMost(page, language::menuShown);
        if (!language.menuShown()) {
            throw new AssertionError("點了語言膠囊之後語言選單沒有出現");
        }
    }

    /**
     * 行動版 WebKit 不支援滑鼠滾輪，Playwright 也沒有觸控滑動的手勢，所以直接把頁面與裡面每一塊能捲的區域捲到底，
     * 對版面來說和手指滑到底的結果相同。
     */
    @當("住客把頁面捲到底")
    public void scrollToBottom() {
        layout.scrollToBottom();
    }
}
