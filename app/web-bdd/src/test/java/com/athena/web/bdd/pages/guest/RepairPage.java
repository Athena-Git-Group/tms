package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class RepairPage extends PageObject {

    public static final String SUBMIT = "送出報修";

    private static final List<String> SUBMIT_TRANSLATIONS = List.of("Send report", "故障を連絡", "Gửi báo sửa");

    public static final String OTHER = "其他";

    public static final int NOTE_LIMIT = 200;

    public static final List<String> CATEGORIES = List.of("冷氣不冷", "電視無訊號", "燈具故障", "熱水不熱", "網路連不上", OTHER);

    private static final Map<String, List<String>> CATEGORY_TRANSLATIONS = Map.of(
            "冷氣不冷", List.of("AC not cooling", "エアコンが効かない", "Máy lạnh không mát"),
            "電視無訊號", List.of("TV has no signal", "テレビが映らない", "TV không có tín hiệu"),
            "燈具故障", List.of("Light not working", "照明がつかない", "Đèn bị hỏng"),
            "熱水不熱", List.of("No hot water", "お湯が出ない", "Không có nước nóng"),
            "網路連不上", List.of("Can’t get online", "ネットにつながらない", "Không vào được mạng"),
            OTHER, List.of("Other", "その他", "Khác"));

    private static final Pattern COUNTER = Pattern.compile("^\\s*\\d+ / " + NOTE_LIMIT + "\\s*$");

    public RepairPage(WebWorld world) {
        super(world);
    }

    public static String categoryIn(String language, String zhTw) {
        String category = zhTw.strip();
        List<String> others = CATEGORY_TRANSLATIONS.get(category);
        if (others == null) {
            throw new IllegalArgumentException("設備報修的類別只有 " + CATEGORIES + "，收到「" + zhTw + "」");
        }
        int index = GuestTerms.LANGUAGES.indexOf(language);
        if (index < 0) {
            throw new IllegalArgumentException("住客介面只有 " + GuestTerms.LANGUAGES + "，收到語言「" + language + "」");
        }
        return index == 0 ? category : others.get(index - 1);
    }

    public static String pressableIn(String language, String name) {
        int index = GuestTerms.LANGUAGES.indexOf(language);
        if (!SUBMIT.equals(name.strip()) || index <= 0) {
            return name;
        }
        return SUBMIT_TRANSLATIONS.get(index - 1);
    }

    public Locator category(String zhTw, String language) {
        return page().getByRole(AriaRole.RADIO, new Page.GetByRoleOptions()
                .setName(categoryIn(language, zhTw)).setExact(true));
    }

    public List<String> checkedCategories() {
        return radios().all().stream()
                .filter(Locator::isChecked)
                .map(radio -> radio.innerText().strip())
                .toList();
    }

    public void chooseCategory(String zhTw, String language) {
        Locator chip = category(zhTw, language);
        if (world.profile().touch()) {
            chip.tap();
        } else {
            chip.click();
        }
    }

    public void typeNote(String text) {
        note().pressSequentially(text);
    }

    public Locator note() {
        return page().getByRole(AriaRole.MAIN).locator("textarea")
                .or(page().getByRole(AriaRole.MAIN).getByRole(AriaRole.TEXTBOX))
                .filter(new Locator.FilterOptions().setVisible(true))
                .first();
    }

    public String noteValue() {
        return note().inputValue();
    }

    public String notePlaceholder() {
        String placeholder = note().getAttribute("placeholder");
        return placeholder == null ? "" : placeholder;
    }

    public Optional<String> counter() {
        Locator counter = page().getByRole(AriaRole.MAIN).getByText(COUNTER)
                .filter(new Locator.FilterOptions().setVisible(true));
        return counter.count() == 0 ? Optional.empty() : Optional.of(counter.first().innerText().strip());
    }

    public static int lengthOf(String text) {
        return text.codePointCount(0, text.length());
    }

    private Locator radios() {
        return page().getByRole(AriaRole.MAIN).getByRole(AriaRole.RADIOGROUP)
                .getByRole(AriaRole.RADIO)
                .filter(new Locator.FilterOptions().setVisible(true));
    }
}
