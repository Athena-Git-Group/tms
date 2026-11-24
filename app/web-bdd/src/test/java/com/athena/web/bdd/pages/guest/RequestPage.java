package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RequestPage extends PageObject {

    public static final String CANCEL_BUTTON = "取消這個需求";

    public static final String CANCEL_NOTE = "還沒有同仁接手前，都可以自己取消";

    public static final String LOCKED_NOTE = "同仁已經接手。需要更改或取消，請用房內電話撥 9 找櫃檯。";

    public static final String RATING_QUESTION = "這次服務還滿意嗎？";

    public static final String RATING_SUBMIT = "送出評分";

    public record Step(String name, String look, String sub) {
    }

    private static final String STEP_LOOK = """
            step => {
              const opaque = color => {
                const m = /rgba?\\(([^)]*)\\)/.exec(color);
                if (!m) return false;
                const parts = m[1].split(',').map(Number);
                return parts.length < 4 || parts[3] > 0.5;
              };
              const dot = [...step.querySelectorAll('*')].find(el => {
                const r = el.getBoundingClientRect();
                const radius = getComputedStyle(el).borderTopLeftRadius;
                const round = radius.endsWith('%') ? parseFloat(radius) >= 50 : parseFloat(radius) >= r.width / 2 - 1;
                return r.width >= 16 && r.width <= 40 && Math.abs(r.width - r.height) <= 1 && round;
              });
              if (!dot) return '沒有圓點';
              const icon = dot.querySelector('svg');
              if (icon && icon.getBoundingClientRect().width > 0) return '打勾';
              let card = step.parentElement;
              while (card && !opaque(getComputedStyle(card).backgroundColor)) card = card.parentElement;
              const fill = getComputedStyle(dot).backgroundColor;
              const cardFill = card ? getComputedStyle(card).backgroundColor : 'rgb(255, 255, 255)';
              return opaque(fill) && fill !== cardFill ? '實心' : '空心';
            }
            """;

    public RequestPage(WebWorld world) {
        super(world);
    }

    private Locator main() {
        return page().getByRole(AriaRole.MAIN);
    }

    public String drawnProblem() {
        GuestFrame frame = new GuestFrame(world);
        if (!frame.path().matches("^/g/[^/]+/requests/[^/]+$")) {
            return "網址不是進度頁：" + frame.path();
        }
        if (frame.pageTitle().isEmpty()) {
            return "進度頁的標題列還沒畫出來";
        }
        if (cardTitle().filter(title -> !title.isEmpty()).isEmpty()) {
            return "進度頁的第一張卡還沒畫出來";
        }
        return frame.loading() ? "進度頁還標著載入中" : null;
    }

    public Optional<String> cardTitle() {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2));
        return title.count() == 0 ? Optional.empty()
                : Optional.of(ShellFrame.normalizeSpaces(title.first().innerText()));
    }

    public Optional<Locator> card(String language) {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2));
        if (title.count() == 0) {
            return Optional.empty();
        }
        for (int level = 1; level <= 3; level++) {
            Locator around = title.first().locator("xpath=ancestor::*[" + level + "]");
            if (!GuestFrame.tagsIn(around, language).isEmpty()) {
                return Optional.of(around);
            }
        }
        return Optional.empty();
    }

    public Optional<Locator> cardWithIcon(String language) {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2));
        if (title.count() == 0) {
            return Optional.empty();
        }
        for (int level = 1; level <= 4; level++) {
            Locator around = title.first().locator("xpath=ancestor::*[" + level + "]");
            if (!GuestFrame.tagsIn(around, language).isEmpty() && around.locator("svg").count() > 0) {
                return Optional.of(around);
            }
        }
        return Optional.empty();
    }

    public List<List<String>> messageBoxes() {
        return main().getByRole(AriaRole.NOTE).filter(new Locator.FilterOptions().setVisible(true)).all().stream()
                .map(box -> ShellFrame.linesOf(box.innerText()))
                .toList();
    }

    public boolean messageBoxBelowCard(Locator card) {
        Locator box = main().getByRole(AriaRole.NOTE).filter(new Locator.FilterOptions().setVisible(true)).first();
        return box.boundingBox().y >= card.boundingBox().y + card.boundingBox().height - 1;
    }

    public Optional<Locator> fourSteps(String language) {
        List<String> names = GuestTerms.STEPS.stream().map(step -> GuestTerms.in(language, step)).toList();
        for (Locator list : main().getByRole(AriaRole.LIST).all()) {
            List<String> firstLines = list.getByRole(AriaRole.LISTITEM).all().stream()
                    .map(item -> firstLine(item)).toList();
            if (firstLines.equals(names)) {
                return Optional.of(list);
            }
        }
        return Optional.empty();
    }

    public List<Step> steps(Locator fourSteps) {
        List<Step> steps = new ArrayList<>();
        for (Locator item : fourSteps.getByRole(AriaRole.LISTITEM).all()) {
            String look = String.valueOf(item.evaluate(STEP_LOOK));
            boolean current = "step".equals(item.getAttribute("aria-current"));
            if (look.equals("實心") != current) {
                look += current ? "（標了 aria-current）" : "（沒有標 aria-current）";
            }
            List<String> lines = ShellFrame.linesOf(item.innerText());
            String sub = String.join(" ", lines.subList(Math.min(1, lines.size()), lines.size()));
            steps.add(new Step(lines.isEmpty() ? "" : lines.get(0), look, sub));
        }
        return steps;
    }

    public Locator button(String name) {
        return page().getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName(name).setExact(true)).filter(new Locator.FilterOptions().setVisible(true));
    }

    public int stars() {
        return page().getByRole(AriaRole.RADIO, new com.microsoft.playwright.Page.GetByRoleOptions()
                        .setName(java.util.regex.Pattern.compile("^[1-5] 顆星$")))
                .filter(new Locator.FilterOptions().setVisible(true)).count();
    }

    public boolean cardTitleFullyShown() {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2)).first();
        Object whole = title.evaluate("""
                el => el.scrollHeight <= el.clientHeight + 1 && el.scrollWidth <= el.clientWidth + 1
                  && getComputedStyle(el).textOverflow !== 'ellipsis'
                """);
        return Boolean.TRUE.equals(whole);
    }

    public Optional<Locator> dialog() {
        Locator shown = page().getByRole(AriaRole.ALERTDIALOG).or(page().getByRole(AriaRole.DIALOG))
                .filter(new Locator.FilterOptions().setVisible(true));
        int count = shown.count();
        return count == 0 ? Optional.empty() : Optional.of(shown.nth(count - 1));
    }

    public static String dialogTitle(Locator dialog) {
        Locator heading = dialog.getByRole(AriaRole.HEADING);
        return heading.count() == 0 ? "" : ShellFrame.normalizeSpaces(heading.first().innerText());
    }

    public static List<String> dialogButtons(Locator dialog) {
        return dialog.getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setVisible(true)).all().stream()
                .map(button -> ShellFrame.normalizeSpaces(button.innerText()))
                .toList();
    }

    public static String dialogBody(Locator dialog) {
        List<String> lines = new ArrayList<>(ShellFrame.linesOf(dialog.innerText()));
        lines.remove(dialogTitle(dialog));
        lines.removeAll(dialogButtons(dialog));
        return String.join("", lines);
    }

    public static boolean focusOnFirstButton(Locator dialog) {
        Object focused = dialog.evaluate("""
                d => {
                  const first = d.querySelector('button, [role=button]');
                  return !!first && first === document.activeElement;
                }
                """);
        return Boolean.TRUE.equals(focused);
    }

    public static boolean scrimBehind(Locator dialog) {
        Object scrim = dialog.evaluate("""
                d => {
                  try { if (d.matches(':modal')) return true; } catch (e) {}
                  const covers = el => {
                    const cs = getComputedStyle(el);
                    const r = el.getBoundingClientRect();
                    return (cs.position === 'fixed' || cs.position === 'absolute')
                      && r.width >= innerWidth - 1 && r.height >= innerHeight - 1
                      && cs.backgroundColor !== 'rgba(0, 0, 0, 0)' && cs.visibility !== 'hidden';
                  };
                  for (let el = d.parentElement; el && el !== document.body; el = el.parentElement) {
                    if (covers(el)) return true;
                  }
                  return [...document.body.querySelectorAll('*')]
                    .some(el => !el.contains(d) && !d.contains(el) && covers(el));
                }
                """);
        return Boolean.TRUE.equals(scrim);
    }

    public Optional<Locator> ratingCard() {
        Locator question = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions()
                .setName(RATING_QUESTION).setExact(true)).filter(new Locator.FilterOptions().setVisible(true));
        if (question.count() == 0) {
            return Optional.empty();
        }
        Locator card = question.first().locator("xpath=ancestor::*[.//*[@role='radio']][1]");
        return card.count() == 0 ? Optional.empty() : Optional.of(card);
    }

    public Locator star(int n) {
        return page().getByRole(AriaRole.RADIO, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName(n + " 顆星").setExact(true));
    }

    public void tapStar(int n) {
        if (world.profile().touch()) {
            star(n).tap();
        } else {
            star(n).click();
        }
    }

    public List<Integer> checkedStars() {
        List<Integer> checked = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            Locator star = star(n);
            if (star.count() > 0 && star.first().isChecked()) {
                checked.add(n);
            }
        }
        return checked;
    }

    public List<StarShape> starShapes() {
        List<StarShape> shapes = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            Object paths = star(n).evaluateAll(STAR_PATHS);
            shapes.add(StarShape.of((List<?>) paths));
        }
        return shapes;
    }

    static final String STAR_PATHS =
            "els => els.length === 1 ? [...els[0].querySelectorAll('svg path')].map(p => p.getAttribute('d') || '') : []";

    public static int textInputsIn(Locator area) {
        return area.locator("textarea, input:not([type]), input[type=text], input[type=search], [contenteditable=true]")
                .or(area.getByRole(AriaRole.TEXTBOX))
                .count();
    }

    private static String firstLine(Locator item) {
        List<String> lines = ShellFrame.linesOf(item.innerText());
        return lines.isEmpty() ? "" : lines.get(0);
    }
}
