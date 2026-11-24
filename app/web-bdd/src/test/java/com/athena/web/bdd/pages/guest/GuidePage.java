package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;

public class GuidePage extends PageObject {

    public static final String GUIDE_SECTION = "住宿指南";

    public static final String SERVICES_SECTION = "服務";

    public GuidePage(WebWorld world) {
        super(world);
    }

    public List<List<String>> guideRows() {
        return rows().stream().map(row -> ShellFrame.linesOf(row.innerText())).toList();
    }

    public int guideRowsWithoutArrow() {
        return (int) rows().stream().filter(row -> !GuestFrame.hasTrailingArrow(row)).count();
    }

    public int guideRowsWithImages() {
        return (int) rows().stream().filter(row -> row.getByRole(AriaRole.IMG).count() > 0).count();
    }

    public List<String> blockAfterServices() {
        Locator next = new GuestHomePage(world).section(SERVICES_SECTION).locator("xpath=following-sibling::*[1]");
        return next.count() == 0 ? List.of() : ShellFrame.linesOf(next.first().innerText());
    }

    public List<String> bodyLines() {
        Locator main = page().getByRole(AriaRole.MAIN);
        return main.count() == 0 ? List.of() : ShellFrame.linesOf(main.first().innerText());
    }

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
