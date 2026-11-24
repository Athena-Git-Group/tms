package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class SupplyPage extends PageObject {

    public static final String CHOOSE_QUANTITY = "選擇數量";

    public SupplyPage(WebWorld world) {
        super(world);
    }

    public static String submitLabel(int items) {
        return "送出 " + items + " 項";
    }

    public Locator increase(String item) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("增加" + item).setExact(true));
    }

    public static final String MAX_HINT = "最多 5";

    public static final int MAX_QUANTITY = 5;

    private static final String INCREASE = "增加";

    public Locator decrease(String item) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("減少" + item).setExact(true));
    }

    public java.util.List<String> itemNames() {
        return page().getByRole(AriaRole.MAIN).getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(java.util.regex.Pattern.compile("^" + INCREASE + ".+")))
                .all().stream()
                .map(button -> button.getAttribute("aria-label") != null ? button.getAttribute("aria-label")
                        : com.athena.web.bdd.pages.ShellFrame.normalizeSpaces(button.innerText()))
                .map(name -> name.substring(INCREASE.length()).strip())
                .toList();
    }

    public Locator row(String item) {
        return page().getByRole(AriaRole.MAIN).locator("*")
                .filter(new Locator.FilterOptions().setHas(increase(item)).setHasText(item))
                .last();
    }

    public java.util.List<String> rowLines(String item) {
        return com.athena.web.bdd.pages.ShellFrame.linesOf(row(item).innerText());
    }

    public int quantity(String item) {
        java.util.List<String> numbers = rowLines(item).stream().filter(line -> line.matches("\\d+")).toList();
        return numbers.size() == 1 ? Integer.parseInt(numbers.get(0)) : -1;
    }

    public Locator bottomButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(java.util.regex.Pattern.compile("^(" + CHOOSE_QUANTITY + "|送出 \\d+ 項)$")));
    }

    public String expectedBottomLabel() {
        long chosen = itemNames().stream().filter(item -> quantity(item) > 0).count();
        return chosen == 0 ? CHOOSE_QUANTITY : submitLabel((int) chosen);
    }

    public void tapEvenIfDisabled(Locator button) {
        if (world.profile().touch()) {
            button.tap(new Locator.TapOptions().setForce(true));
        } else {
            button.click(new Locator.ClickOptions().setForce(true));
        }
    }
}
