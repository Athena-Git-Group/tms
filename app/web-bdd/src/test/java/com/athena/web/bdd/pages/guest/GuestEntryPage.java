package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;

/**
 * 進入住客掃碼頁時的三種畫面：載入中、無法使用頁與重試狀態。
 *
 * <p>可及性結構（產品在 Phase 4 照 {@code ui/ui-plan.md} 畫面 1 做）：無法使用頁沒有任何按鈕、連結或可點的列，
 * 「櫃檯（24 小時）／房內電話撥 9」那張卡也不是連結；重試狀態只有一顆名稱「重試」的按鈕。
 * 提示浮層是明寫 {@code role="status"} 的元素；數字欄位（{@code <output>}）雖然也算 status，不是提示浮層。
 */
public class GuestEntryPage extends PageObject {

    public static final String UNAVAILABLE_TITLE = "請洽櫃檯";

    public static final String RETRY_TITLE = "連線失敗，請再試一次";

    public static final String RETRY_BUTTON = "重試";

    /** 畫面上任何一種「可以點」的角色。 */
    private static final List<AriaRole> CLICKABLE_ROLES = List.of(AriaRole.BUTTON, AriaRole.LINK, AriaRole.MENUITEM,
            AriaRole.MENUITEMRADIO, AriaRole.MENUITEMCHECKBOX, AriaRole.OPTION, AriaRole.RADIO, AriaRole.CHECKBOX,
            AriaRole.TAB, AriaRole.SWITCH, AriaRole.TEXTBOX, AriaRole.COMBOBOX);

    public GuestEntryPage(WebWorld world) {
        super(world);
    }

    /** 整個畫面看得到的每一行字，由上到下。 */
    public List<String> visibleLines() {
        return ShellFrame.linesOf(page().locator("body").innerText());
    }

    public boolean unavailableShown() {
        return visibleLines().stream().anyMatch(line -> line.contains(UNAVAILABLE_TITLE));
    }

    public boolean retryShown() {
        return visibleLines().stream().anyMatch(line -> line.contains(RETRY_TITLE))
                && page().getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                        .setName(RETRY_BUTTON).setExact(true)).filter(new Locator.FilterOptions().setVisible(true))
                        .count() > 0;
    }

    /** 看得到、可以點的東西的說明（角色加名稱）；另外算進沒有角色、但有 {@code href} 的連結。 */
    public List<String> clickables() {
        List<String> found = new java.util.ArrayList<>();
        for (AriaRole role : CLICKABLE_ROLES) {
            for (Locator each : page().getByRole(role).filter(new Locator.FilterOptions().setVisible(true)).all()) {
                found.add(role.name().toLowerCase() + "「" + ShellFrame.normalizeSpaces(each.innerText()) + "」");
            }
        }
        for (Locator each : page().locator("a[href], [onclick], [tabindex]:not([tabindex='-1'])")
                .filter(new Locator.FilterOptions().setVisible(true)).all()) {
            found.add("可點的元素「" + ShellFrame.normalizeSpaces(each.innerText()) + "」");
        }
        return found;
    }

    /** 看得到的提示浮層的字；明寫 {@code role="status"} 的才算，{@code <output>} 的數字不算。 */
    public List<String> toasts() {
        return page().locator("[role='status']").filter(new Locator.FilterOptions().setVisible(true)).all().stream()
                .map(toast -> ShellFrame.normalizeSpaces(toast.innerText()))
                .filter(text -> !text.isBlank())
                .toList();
    }
}
