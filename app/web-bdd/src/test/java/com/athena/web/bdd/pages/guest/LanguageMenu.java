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

public class LanguageMenu extends PageObject {

    public static final List<String> CAPSULE_NAMES = List.of("語言", "Language", "言語", "Ngôn ngữ");

    public static final Map<String, List<String>> FIXED_PHRASES = Map.of(
            "服務", List.of("服務", "Services", "サービス", "Dịch vụ"),
            "備品補充", List.of("備品補充", "Amenities", "アメニティ", "Bổ sung vật dụng"),
            "設備報修", List.of("設備報修", "Report a problem", "故障の連絡", "Báo sửa thiết bị"),
            "我的需求", List.of("我的需求", "My requests", "リクエスト", "Yêu cầu của tôi"),
            "需求進度", List.of("需求進度", "Request", "リクエスト", "Tiến độ yêu cầu"));

    private static final int BOLD_WEIGHT = 600;

    public LanguageMenu(WebWorld world) {
        super(world);
    }

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

    public List<String> options() {
        return items().stream().map(LanguageMenu::text).toList();
    }

    public List<String> checkedOptions() {
        return items().stream().filter(item -> "true".equals(item.getAttribute("aria-checked"))).map(LanguageMenu::text).toList();
    }

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
