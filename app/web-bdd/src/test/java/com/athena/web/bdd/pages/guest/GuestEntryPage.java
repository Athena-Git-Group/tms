package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;

public class GuestEntryPage extends PageObject {

    public static final String UNAVAILABLE_TITLE = "請洽櫃檯";

    public static final String RETRY_TITLE = "連線失敗，請再試一次";

    public static final String RETRY_BUTTON = "重試";

    private static final List<AriaRole> CLICKABLE_ROLES = List.of(AriaRole.BUTTON, AriaRole.LINK, AriaRole.MENUITEM,
            AriaRole.MENUITEMRADIO, AriaRole.MENUITEMCHECKBOX, AriaRole.OPTION, AriaRole.RADIO, AriaRole.CHECKBOX,
            AriaRole.TAB, AriaRole.SWITCH, AriaRole.TEXTBOX, AriaRole.COMBOBOX);

    public GuestEntryPage(WebWorld world) {
        super(world);
    }

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

    public List<String> toasts() {
        return page().locator("[role='status']").filter(new Locator.FilterOptions().setVisible(true)).all().stream()
                .map(toast -> ShellFrame.normalizeSpaces(toast.innerText()))
                .filter(text -> !text.isBlank())
                .toList();
    }
}
