package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * 備品補充頁。
 *
 * <p>可及性結構（產品在 Phase 4 照 {@code ui/ui-plan.md}「鍵盤與螢幕報讀」做）：每一項的加減鈕名稱是
 * 「增加{名稱}」「減少{名稱}」。
 */
public class SupplyPage extends PageObject {

    /** 一項都還沒選時頁底按鈕的字。 */
    public static final String CHOOSE_QUANTITY = "選擇數量";

    public SupplyPage(WebWorld world) {
        super(world);
    }

    /** 選了幾項時頁底送出鈕的字。 */
    public static String submitLabel(int items) {
        return "送出 " + items + " 項";
    }

    /** 某一項的加號。 */
    public Locator increase(String item) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("增加" + item).setExact(true));
    }

    /** 每一列名稱下面那一行提示。 */
    public static final String MAX_HINT = "最多 5";

    /** 每一項的上限。 */
    public static final int MAX_QUANTITY = 5;

    private static final String INCREASE = "增加";

    /** 某一項的減號。 */
    public Locator decrease(String item) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("減少" + item).setExact(true));
    }

    /** 清單各列的名稱，由上到下；以每一列加號的名稱「增加{名稱}」認出有哪幾列。 */
    public java.util.List<String> itemNames() {
        return page().getByRole(AriaRole.MAIN).getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(java.util.regex.Pattern.compile("^" + INCREASE + ".+")))
                .all().stream()
                .map(button -> button.getAttribute("aria-label") != null ? button.getAttribute("aria-label")
                        : com.athena.web.bdd.pages.ShellFrame.normalizeSpaces(button.innerText()))
                .map(name -> name.substring(INCREASE.length()).strip())
                .toList();
    }

    /** 某一項那一列：包著它的加號、也包著它名稱的最內層元素。 */
    public Locator row(String item) {
        return page().getByRole(AriaRole.MAIN).locator("*")
                .filter(new Locator.FilterOptions().setHas(increase(item)).setHasText(item))
                .last();
    }

    /** 那一列看得到的每一行字，例如名稱、「最多 5」與數字。 */
    public java.util.List<String> rowLines(String item) {
        return com.athena.web.bdd.pages.ShellFrame.linesOf(row(item).innerText());
    }

    /** 那一列減號與加號之間的數字；列裡找不到剛好一個數字時是 -1。 */
    public int quantity(String item) {
        java.util.List<String> numbers = rowLines(item).stream().filter(line -> line.matches("\\d+")).toList();
        return numbers.size() == 1 ? Integer.parseInt(numbers.get(0)) : -1;
    }

    /** 頁底的送出鈕：字是「選擇數量」或「送出 {n} 項」的那一顆。 */
    public Locator bottomButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(java.util.regex.Pattern.compile("^(" + CHOOSE_QUANTITY + "|送出 \\d+ 項)$")));
    }

    /** 依每一項現在的數字，頁底按鈕該寫的字。 */
    public String expectedBottomLabel() {
        long chosen = itemNames().stream().filter(item -> quantity(item) > 0).count();
        return chosen == 0 ? CHOOSE_QUANTITY : submitLabel((int) chosen);
    }

    /** 點一下這顆按鈕；停用時也照點（用 {@code force}），瀏覽器不會把它當成一次有效的點按。 */
    public void tapEvenIfDisabled(Locator button) {
        if (world.profile().touch()) {
            button.tap(new Locator.TapOptions().setForce(true));
        } else {
            button.click(new Locator.ClickOptions().setForce(true));
        }
    }
}
