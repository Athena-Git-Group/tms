package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.Optional;

/**
 * 我的需求清單。
 *
 * <p>可及性結構（產品在 Phase 4 照做）：每個段落是一個 {@code h2} 段落名（「進行中」「已結束」），
 * 緊接在它後面的清單（{@code ul}／{@code ol}，角色 {@code list}）每一列是一個 {@code listitem}，
 * 列裡看得到的第一行字是需求標題，其中一個字剛好是狀態標籤。
 */
public class RequestsPage extends PageObject {

    public RequestsPage(WebWorld world) {
        super(world);
    }

    /** 某個段落裡由上到下每一列的標題；這個段落不在畫面上時是空的。 */
    public List<String> titlesIn(String sectionName) {
        return rowsIn(sectionName).stream().map(RequestsPage::titleOf).toList();
    }

    /** 清單上標題剛好是這個的那一列（不分段落）。 */
    public Optional<Locator> row(String title) {
        String wanted = ShellFrame.normalizeSpaces(title);
        return page().getByRole(AriaRole.MAIN).getByRole(AriaRole.LISTITEM)
                .filter(new Locator.FilterOptions().setVisible(true))
                .all().stream()
                .filter(item -> titleOf(item).equals(wanted))
                .findFirst();
    }

    /** 某個段落裡由上到下的每一列；這個段落不在畫面上時是空的。 */
    public List<Locator> rowsIn(String sectionName) {
        Locator heading = page().getByRole(AriaRole.MAIN).getByRole(AriaRole.HEADING,
                new Locator.GetByRoleOptions().setLevel(2).setName(sectionName).setExact(true));
        if (heading.count() == 0) {
            return List.of();
        }
        Locator list = heading.first().locator("xpath=following::*[self::ul or self::ol or @role='list'][1]");
        return list.locator(":scope > li, :scope > [role='listitem']")
                .filter(new Locator.FilterOptions().setVisible(true))
                .all();
    }

    /** 一列看得到的每一行字，第一行是標題。 */
    public static List<String> linesOf(Locator row) {
        return ShellFrame.linesOf(row.innerText());
    }

    /**
     * 一列左邊的圖示是哪一種需求類型的（{@link RequestIcons}）：列裡第一個 {@code svg} 在列的左邊三分之一，
     * 它的 {@code path} 圖形對得到四種之一。沒有圖示、不在左邊或認不出時回圖形的前 40 個字或「沒有圖示」，前面加「?」。
     */
    public static String leftIconTypeOf(Locator row) {
        Object shape = row.evaluate("""
                row => {
                  const svg = row.querySelector('svg');
                  if (!svg) return null;
                  const r = row.getBoundingClientRect();
                  const s = svg.getBoundingClientRect();
                  if (s.width === 0 || s.left > r.left + r.width / 3) return null;
                  return [...svg.querySelectorAll('path')].map(p => p.getAttribute('d') || '').join(' ');
                }
                """);
        if (!(shape instanceof String path) || path.isBlank()) {
            return "?沒有圖示";
        }
        return RequestIcons.typeOf(path).orElse("?" + path.substring(0, Math.min(40, path.length())));
    }

    /** 畫面上一顆狀態標籤的底色與字色（瀏覽器算出來的 {@code rgb(…)}）。 */
    public record TagColor(String background, String text) {
    }

    /** 清單裡每一顆字剛好是這個的狀態標籤（這個語言的字）的顏色。 */
    public List<TagColor> tagColors(String shownTag) {
        return page().getByRole(AriaRole.MAIN)
                .getByText(shownTag, new Locator.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true))
                .all().stream()
                .map(tag -> {
                    Object colors = tag.evaluate("""
                            el => {
                              let box = el;
                              while (box && getComputedStyle(box).backgroundColor === 'rgba(0, 0, 0, 0)'
                                  && box.parentElement && !box.parentElement.matches('li, [role=listitem], a, button')) {
                                box = box.parentElement;
                              }
                              return [getComputedStyle(box).backgroundColor, getComputedStyle(el).color];
                            }
                            """);
                    List<?> pair = (List<?>) colors;
                    return new TagColor(String.valueOf(pair.get(0)), String.valueOf(pair.get(1)));
                })
                .toList();
    }

    private static String titleOf(Locator row) {
        List<String> lines = ShellFrame.linesOf(row.innerText());
        return lines.isEmpty() ? "" : lines.get(0);
    }
}
