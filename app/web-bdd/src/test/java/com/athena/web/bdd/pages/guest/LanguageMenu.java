package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 首頁頂列的語言膠囊與它彈出的語言選單，以及判斷介面語言用的固定字句。
 *
 * <p>可及性結構（產品在 Phase 4 照做，和原型 {@code ui/guest.js} 相同）：
 * <ul>
 *   <li>語言膠囊是一顆按鈕，名稱是這個語言的「語言」（{@link #CAPSULE_NAMES}），看得到的字是短寫法；地球與箭頭是裝飾圖示。</li>
 *   <li>語言選單是角色 {@code menu}，每個選項是 {@code menuitemradio}，目前的語言 {@code aria-checked="true"}、字加粗。</li>
 * </ul>
 * 膠囊的名稱跟著介面語言換字，所以四種寫法都認。
 */
public class LanguageMenu extends PageObject {

    /** 語言膠囊在四種介面語言下的名稱。 */
    public static final List<String> CAPSULE_NAMES = List.of("語言", "Language", "言語", "Ngôn ngữ");

    /**
     * 規格以這幾句在畫面上的寫法認介面語言，因為它們每一頁都在、而且四種語言的寫法都不同。
     * 首頁看「服務」區段標題，子頁看標題列的頁標題；寫法依 {@link GuestTerms#LANGUAGES} 的順序。進度頁改看四步的步名。
     */
    public static final Map<String, List<String>> FIXED_PHRASES = Map.of(
            "服務", List.of("服務", "Services", "サービス", "Dịch vụ"),
            "備品補充", List.of("備品補充", "Amenities", "アメニティ", "Bổ sung vật dụng"),
            "設備報修", List.of("設備報修", "Report a problem", "故障の連絡", "Báo sửa thiết bị"),
            "我的需求", List.of("我的需求", "My requests", "リクエスト", "Yêu cầu của tôi"),
            "需求進度", List.of("需求進度", "Request", "リクエスト", "Tiến độ yêu cầu"));

    /** 字重到 600 才算加粗，因為一般選項的字重是 400、目前的語言是 600 以上。 */
    private static final int BOLD_WEIGHT = 600;

    public LanguageMenu(WebWorld world) {
        super(world);
    }

    /** 某個固定字句在某個語言（{@link GuestTerms#LANGUAGES} 的代碼）下的寫法。 */
    public static String phrase(String zhTw, String language) {
        List<String> all = FIXED_PHRASES.get(zhTw);
        if (all == null) {
            throw new IllegalArgumentException("「" + zhTw + "」不是判斷介面語言用的固定字句");
        }
        return all.get(GuestTerms.LANGUAGES.indexOf(language));
    }

    public Locator capsule() {
        Pattern names = Pattern.compile("^(" + String.join("|", CAPSULE_NAMES.stream().map(BrowserRegex::literal).toList()) + ")$");
        return page().getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName(names))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 語言膠囊看得到的字。 */
    public String capsuleText() {
        Locator capsule = capsule();
        return capsule.count() == 0 ? "" : String.join(" ", ShellFrame.linesOf(capsule.first().innerText()));
    }

    public Locator menu() {
        return page().getByRole(AriaRole.MENU).filter(new Locator.FilterOptions().setVisible(true));
    }

    public boolean menuShown() {
        return menu().count() > 0;
    }

    /** 選單裡由上到下每個選項看得到的字。 */
    public List<String> options() {
        return items().stream().map(LanguageMenu::text).toList();
    }

    /** 標成打勾（{@code aria-checked="true"}）的選項的字。 */
    public List<String> checkedOptions() {
        return items().stream().filter(item -> "true".equals(item.getAttribute("aria-checked"))).map(LanguageMenu::text).toList();
    }

    /** 字加粗的選項的字；選項的字包在裡面的元素時以它的字重為準。 */
    public List<String> boldOptions() {
        return items().stream()
                .filter(item -> {
                    Object weight = item.evaluate(
                            "el => parseInt(getComputedStyle(el.querySelector('span') || el).fontWeight, 10)");
                    return ((Number) weight).intValue() >= BOLD_WEIGHT;
                })
                .map(LanguageMenu::text)
                .toList();
    }

    private List<Locator> items() {
        Locator menu = menu();
        if (menu.count() == 0) {
            return List.of();
        }
        return menu.first().getByRole(AriaRole.MENUITEMRADIO).filter(new Locator.FilterOptions().setVisible(true)).all();
    }

    private static String text(Locator item) {
        return String.join(" ", ShellFrame.linesOf(item.innerText()));
    }
}
