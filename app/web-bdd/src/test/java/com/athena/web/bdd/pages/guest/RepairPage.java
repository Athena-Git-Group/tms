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

/**
 * 設備報修頁。
 *
 * <p>可及性結構（產品在 Phase 4 照 {@code ui/ui-plan.md}「鍵盤與螢幕報讀」與原型 {@code ui/guest.js} 做）：
 * <ul>
 *   <li>六顆類別膠囊是一個單選群組（{@code radiogroup}），每一顆是角色 {@code radio}、名稱是介面語言的類別字，
 *       選中的那一顆 {@code aria-checked="true"}。</li>
 *   <li>補充說明是頁面上唯一的多行輸入框（{@code textarea}，角色 {@code textbox}），佔位提示字放在 {@code placeholder}。</li>
 *   <li>字數是輸入框下方一段剛好寫成「{n} / 200」的字。</li>
 * </ul>
 */
public class RepairPage extends PageObject {

    /** 頁底送出鈕的名稱。 */
    public static final String SUBMIT = "送出報修";

    /** 送出鈕在 English、日本語、Tiếng Việt 介面上的名稱，照原型的字典。 */
    private static final List<String> SUBMIT_TRANSLATIONS = List.of("Send report", "故障を連絡", "Gửi báo sửa");

    /** 「其他」：選它時送出的文字只有補充說明。 */
    public static final String OTHER = "其他";

    /** 補充說明最多幾個字。 */
    public static final int NOTE_LIMIT = 200;

    /** 六顆類別膠囊，由左到右，以繁體中文寫。 */
    public static final List<String> CATEGORIES = List.of("冷氣不冷", "電視無訊號", "燈具故障", "熱水不熱", "網路連不上", OTHER);

    /** 類別在 English、日本語、Tiếng Việt 介面上的字，照原型的字典。 */
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

    /** 規格寫的繁體中文類別在某個介面語言下膠囊上的字。 */
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

    /**
     * 規格以繁體中文寫的按鈕名稱在某個介面語言下的名稱：送出鈕換成該語言的字，其他名稱照原字。
     * 「住客按 "送出報修"」在日本語介面也出現（{@code 組成文字送出.feature} 的日本住客場景），意思是按送出鈕。
     */
    public static String pressableIn(String language, String name) {
        int index = GuestTerms.LANGUAGES.indexOf(language);
        if (!SUBMIT.equals(name.strip()) || index <= 0) {
            return name;
        }
        return SUBMIT_TRANSLATIONS.get(index - 1);
    }

    /** 某一顆類別膠囊；{@code zhTw} 以繁體中文寫，介面是別的語言時找那個語言的字。 */
    public Locator category(String zhTw, String language) {
        return page().getByRole(AriaRole.RADIO, new Page.GetByRoleOptions()
                .setName(categoryIn(language, zhTw)).setExact(true));
    }

    /** 單選群組裡選中的類別膠囊的名稱（介面語言的字）。 */
    public List<String> checkedCategories() {
        return radios().all().stream()
                .filter(Locator::isChecked)
                .map(radio -> radio.innerText().strip())
                .toList();
    }

    /** 點一顆類別膠囊，觸控設定下用點觸。 */
    public void chooseCategory(String zhTw, String language) {
        Locator chip = category(zhTw, language);
        if (world.profile().touch()) {
            chip.tap();
        } else {
            chip.click();
        }
    }

    /** 在補充說明逐字打字，和住客用鍵盤打的一樣。 */
    public void typeNote(String text) {
        note().pressSequentially(text);
    }

    /** 補充說明的輸入框。 */
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

    /** 輸入框下方的字數，例如「4 / 200」；畫面上沒有時是空的。 */
    public Optional<String> counter() {
        Locator counter = page().getByRole(AriaRole.MAIN).getByText(COUNTER)
                .filter(new Locator.FilterOptions().setVisible(true));
        return counter.count() == 0 ? Optional.empty() : Optional.of(counter.first().innerText().strip());
    }

    /** 這段字照住客打的算有幾個字：以字元計，不是以 UTF-16 單位。 */
    public static int lengthOf(String text) {
        return text.codePointCount(0, text.length());
    }

    private Locator radios() {
        return page().getByRole(AriaRole.MAIN).getByRole(AriaRole.RADIOGROUP)
                .getByRole(AriaRole.RADIO)
                .filter(new Locator.FilterOptions().setVisible(true));
    }
}
