package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.Optional;

public class RequestsPage extends PageObject {

    public RequestsPage(WebWorld world) {
        super(world);
    }

    public List<String> titlesIn(String sectionName) {
        return rowsIn(sectionName).stream().map(RequestsPage::titleOf).toList();
    }

    public Optional<Locator> row(String title) {
        String wanted = ShellFrame.normalizeSpaces(title);
        return page().getByRole(AriaRole.MAIN).getByRole(AriaRole.LISTITEM)
                .filter(new Locator.FilterOptions().setVisible(true))
                .all().stream()
                .filter(item -> titleOf(item).equals(wanted))
                .findFirst();
    }

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

    public static List<String> linesOf(Locator row) {
        return ShellFrame.linesOf(row.innerText());
    }

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

    public record TagColor(String background, String text) {
    }

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
