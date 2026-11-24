package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;

/**
 * 首頁的「住宿指南」清單與資訊頁。
 *
 * <p>可及性結構（產品在 Phase 4 照做，和原型 {@code ui/guest.js} 相同）：
 * <ul>
 *   <li>「住宿指南」是首頁的一個區段（{@code h2}「住宿指南」），每一頁一列，列是連結或按鈕，看得到的字只有標題；
 *       右邊的箭頭是不給報讀軟體的裝飾圖示。</li>
 *   <li>「服務」區段之後緊接著的是住宿指南區段，沒有住宿指南時是頁尾那一行說明。</li>
 *   <li>資訊頁的內容區是 {@code main}，子頁標題列在它外面；內文照原文，換行保留。</li>
 * </ul>
 */
public class GuidePage extends PageObject {

    public static final String GUIDE_SECTION = "住宿指南";

    public static final String SERVICES_SECTION = "服務";

    public GuidePage(WebWorld world) {
        super(world);
    }

    /** 「住宿指南」區段裡由上到下的每一列，每一列是它看得到的每一行字；首頁沒有這個區段時是空的。 */
    public List<List<String>> guideRows() {
        return rows().stream().map(row -> ShellFrame.linesOf(row.innerText())).toList();
    }

    /** 「住宿指南」區段裡右端沒有箭頭的列有幾列。 */
    public int guideRowsWithoutArrow() {
        return (int) rows().stream().filter(row -> !GuestFrame.hasTrailingArrow(row)).count();
    }

    /** 「住宿指南」區段裡帶著圖片（報讀軟體看得到的 {@code img}）的列有幾列。 */
    public int guideRowsWithImages() {
        return (int) rows().stream().filter(row -> row.getByRole(AriaRole.IMG).count() > 0).count();
    }

    /** 首頁「服務」區段的下一個區塊看得到的每一行字；首頁沒有「服務」區段時是空的。 */
    public List<String> blockAfterServices() {
        Locator next = new GuestHomePage(world).section(SERVICES_SECTION).locator("xpath=following-sibling::*[1]");
        return next.count() == 0 ? List.of() : ShellFrame.linesOf(next.first().innerText());
    }

    /** 資訊頁內容區看得到的每一行字。 */
    public List<String> bodyLines() {
        Locator main = page().getByRole(AriaRole.MAIN);
        return main.count() == 0 ? List.of() : ShellFrame.linesOf(main.first().innerText());
    }

    /** 資訊頁內容區裡的圖片與影片有幾個。 */
    public int bodyMedia() {
        Locator main = page().getByRole(AriaRole.MAIN);
        return main.getByRole(AriaRole.IMG).count() + main.locator("video, iframe").count();
    }

    private List<Locator> rows() {
        Locator section = new GuestHomePage(world).section(GUIDE_SECTION);
        if (section.count() == 0) {
            return List.of();
        }
        return section.first().getByRole(AriaRole.LINK).or(section.first().getByRole(AriaRole.BUTTON))
                .filter(new Locator.FilterOptions().setVisible(true)).all();
    }
}
