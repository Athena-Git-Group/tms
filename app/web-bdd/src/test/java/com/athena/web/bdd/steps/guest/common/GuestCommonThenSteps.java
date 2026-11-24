package com.athena.web.bdd.steps.guest.common;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.pages.guest.RequestPage.Step;
import com.athena.web.bdd.pages.guest.RequestsPage;
import com.athena.web.bdd.support.GuestCreateRecorder;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.GuestStay.Request;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class GuestCommonThenSteps {

    private static final long OUTLIVES_TOAST_MS = 3_000;

    private static final String PROGRESS_PAGE_TITLE = "需求進度";

    private static final String UNAVAILABLE_TITLE = "請洽櫃檯";

    private static final String RETRY_TITLE = "連線失敗，請再試一次";

    private static final String RETRY_BUTTON = "重試";

    private static final String VIEW_PROGRESS = "查看進度";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestStay stay;

    private final GuestCreateRecorder createRecorder;

    private final GuestFrame frame;

    private final GuestHomePage home;

    private final RequestsPage requests;

    private final RequestPage request;

    public GuestCommonThenSteps(WebWorld world, GuestScenario scenario, GuestStay stay,
            GuestCreateRecorder createRecorder) {
        this.world = world;
        this.scenario = scenario;
        this.stay = stay;
        this.createRecorder = createRecorder;
        this.frame = new GuestFrame(world);
        this.home = new GuestHomePage(world);
        this.requests = new RequestsPage(world);
        this.request = new RequestPage(world);
    }

    public void screenIs(String pageName) {
        GuestScreen screen = GuestScreen.ofTerm(pageName);
        frame.eventually(() -> screenProblem(screen));
    }

    @那麼("畫面是 {string} 的進度頁")
    public void screenIsRequest(String title) {
        UUID taskId = scenario.request(title);
        frame.eventually(() -> {
            if (!frame.path().matches("^/g/[^/]+/requests/" + taskId + "$")) {
                return "網址不是「" + title + "」的進度頁：" + frame.path();
            }
            if (!PROGRESS_PAGE_TITLE.equals(frame.pageTitle())) {
                return "標題列是「" + frame.pageTitle() + "」，不是「" + PROGRESS_PAGE_TITLE + "」";
            }
            Optional<String> cardTitle = request.cardTitle();
            return cardTitle.isPresent() && cardTitle.get().equals(title) ? null
                    : "第一張卡的標題是 " + cardTitle.orElse("（沒有）") + "，不是「" + title + "」";
        });
    }

    @那麼("頁面頂端的標題列是返回鈕、{string} 與 {string}")
    public void titleBarIs(String pageTitle, String roomLine) {
        frame.eventually(() -> {
            if (frame.titleBar().count() == 0) {
                return "頁面頂端沒有標題列";
            }
            if (!frame.titleBarStuckToTop()) {
                return "標題列沒有黏在頁面頂端";
            }
            Locator back = frame.backControl(frame.titleBar().first());
            if (back.count() != 1) {
                return "標題列裡沒有名稱「" + GuestTerms.in(frame.language(), "返回") + "」的返回鈕";
            }
            Locator heading = frame.titleBar().first().getByRole(AriaRole.HEADING,
                    new Locator.GetByRoleOptions().setLevel(1));
            if (heading.count() == 0 || back.boundingBox().x >= heading.first().boundingBox().x) {
                return "返回鈕不在頁標題的左邊";
            }
            List<String> expected = List.of(pageTitle.strip(), roomLine.strip());
            List<String> lines = frame.titleBarLines();
            return lines.equals(expected) ? null : "標題列的字是 " + lines + "，應該剛好是 " + expected;
        });
    }

    @那麼("{string} 的狀態標籤是 {string}")
    public void tagIs(String title, String tag) {
        UUID taskId = scenario.request(title);
        frame.eventually(() -> {
            String language = frame.language();
            Optional<Locator> where = frame.path().matches("^/g/[^/]+/requests/[^/]+$")
                    ? request.card(language) : requests.row(title);
            if (where.isEmpty()) {
                return "畫面上找不到「" + title + "」那一列或進度頁的第一張卡";
            }
            List<String> tags = GuestFrame.tagsIn(where.get(), language);
            return tags.equals(List.of(tag)) ? null : "「" + title + "」的狀態標籤是 " + tags + "，不是「" + tag + "」";
        });
        Request backend = stay.request(taskId);
        assertThat(backend.tag().equals(tag), "後端「" + title + "」是「" + backend.situation()
                + "」，依狀況對照該顯示「" + backend.tag() + "」，不是「" + tag + "」");
    }

    @那麼("清單 {string} 由上到下是 {string}")
    public void sectionIs(String section, String titleList) {
        List<String> expected = Arrays.stream(titleList.split("、")).map(String::strip).toList();
        frame.eventually(() -> {
            List<String> titles = requests.titlesIn(GuestTerms.in(frame.language(), section));
            return titles.equals(expected) ? null : "「" + section + "」段落由上到下是 " + titles + "，應該是 " + expected;
        });
        Map<String, Request> backend = new HashMap<>();
        stay.requests(scenario.phoneRoom()).forEach(r -> backend.put(r.title(), r));
        for (String title : expected) {
            assertThat(backend.containsKey(title), "後端這次住宿沒有「" + title + "」");
        }
        for (int i = 0; i + 1 < expected.size(); i++) {
            Request upper = backend.get(expected.get(i));
            Request lower = backend.get(expected.get(i + 1));
            assertThat(!upper.createdAt().isBefore(lower.createdAt()),
                    "清單上「" + upper.title() + "」在「" + lower.title() + "」上面，後端卻是「" + lower.title() + "」比較晚送出（"
                            + upper.createdAt() + "、" + lower.createdAt() + "）");
        }
    }

    @那麼("狀態訊息框是 {string}，內文 {string}")
    public void messageBoxIs(String title, String body) {
        List<String> expected = List.of(title.strip(), body.strip());
        frame.eventually(() -> {
            List<List<String>> boxes = request.messageBoxes();
            if (boxes.size() != 1) {
                return "進度頁上應該剛好有一個狀態訊息框，實際是 " + boxes;
            }
            if (!boxes.get(0).equals(expected)) {
                return "狀態訊息框是 " + boxes.get(0) + "，應該是 " + expected;
            }
            Optional<Locator> card = request.card(frame.language());
            return card.isPresent() && request.messageBoxBelowCard(card.get()) ? null : "狀態訊息框不在第一張卡的下方";
        });
    }

    @那麼("四步進度走到 {string}")
    public void stepsReach(String step) {
        frame.eventually(() -> {
            String drawn = request.drawnProblem();
            if (drawn != null) {
                return drawn;
            }
            String language = frame.language();
            Optional<Locator> fourSteps = request.fourSteps(language);
            if ("不顯示進度".equals(step)) {
                List<String> stepNames = GuestTerms.STEPS.stream().map(name -> GuestTerms.in(language, name)).toList();
                List<String> shownSteps = new ArrayList<>(mainLines().stream().filter(stepNames::contains).toList());
                request.card(language).ifPresent(card -> GuestFrame.tagsIn(card, language)
                        .forEach(tag -> shownSteps.remove(GuestTerms.in(language, tag))));
                return fourSteps.isEmpty() && shownSteps.isEmpty() ? null
                        : "畫面上不該有四步進度，狀態標籤以外看得到的步名是 " + shownSteps;
            }
            if (fourSteps.isEmpty()) {
                return "畫面上沒有四步進度";
            }
            List<String> looks = request.steps(fourSteps.get()).stream().map(Step::look).toList();
            List<String> expected = expectedLooks(step);
            return looks.equals(expected) ? null : "四步的樣子是 " + looks + "，走到「" + step + "」應該是 " + expected;
        });
    }

    @那麼("四步進度如下：")
    public void stepsAre(DataTable table) {
        frame.eventually(() -> {
            String language = frame.language();
            List<Step> expected = new ArrayList<>();
            for (Map<String, String> row : table.asMaps()) {
                String sub = row.get("副文");
                expected.add(new Step(GuestTerms.in(language, row.get("步").strip()), row.get("樣子").strip(),
                        sub == null ? "" : sub.strip()));
            }
            Optional<Locator> fourSteps = request.fourSteps(language);
            if (fourSteps.isEmpty()) {
                return "畫面上沒有四步進度";
            }
            List<Step> steps = request.steps(fourSteps.get());
            return steps.equals(expected) ? null : "四步進度是 " + steps + "，應該是 " + expected;
        });
    }

    @那麼("進度頁底部是 {string}")
    public void bottomIs(String bottom) {
        frame.eventually(() -> {
            String drawn = request.drawnProblem();
            if (drawn != null) {
                return drawn;
            }
            boolean cancel = request.button(RequestPage.CANCEL_BUTTON).count() > 0 && showsText(RequestPage.CANCEL_NOTE);
            boolean anyCancel = request.button(RequestPage.CANCEL_BUTTON).count() > 0 || showsText(RequestPage.CANCEL_NOTE);
            boolean locked = showsText(RequestPage.LOCKED_NOTE);
            return switch (bottom.strip()) {
                case "取消鈕" -> cancel && !locked ? null : "底部應該只有「" + RequestPage.CANCEL_BUTTON + "」與說明";
                case "鎖定說明" -> locked && !anyCancel ? null : "底部應該只有鎖定說明，沒有取消鈕";
                case "什麼都沒有" -> !locked && !anyCancel ? null : "底部應該沒有取消鈕也沒有鎖定說明";
                default -> throw new IllegalArgumentException("底部只有取消鈕、鎖定說明、什麼都沒有，收到「" + bottom + "」");
            };
        });
    }

    @那麼("進度頁沒有評分卡")
    public void noRatingCard() {
        frame.eventually(() -> {
            String drawn = request.drawnProblem();
            if (drawn != null) {
                return drawn;
            }
            if (showsText(RequestPage.RATING_QUESTION)) {
                return "進度頁上有「" + RequestPage.RATING_QUESTION + "」";
            }
            if (request.stars() > 0) {
                return "進度頁上有星星";
            }
            return request.button(RequestPage.RATING_SUBMIT).count() == 0 ? null
                    : "進度頁上有「" + RequestPage.RATING_SUBMIT + "」";
        });
    }

    @那麼("首頁的進度卡是 {string}")
    public void homeProgressCardIs(String title) {
        frame.eventually(() -> {
            List<List<String>> cards = home.progressCards();
            if (cards.size() != 1) {
                return "首頁「我的需求」底下應該剛好有一張進度卡，實際是 " + cards;
            }
            List<String> lines = cards.get(0);
            return lines.size() >= 2 && lines.get(0).equals(GuestHomePage.PROGRESS_CARD_EYEBROW)
                    && lines.contains(title.strip()) ? null
                    : "進度卡的字是 " + lines + "，應該是眉標「" + GuestHomePage.PROGRESS_CARD_EYEBROW + "」與「" + title + "」";
        });
        Optional<Request> newestActive = stay.requests(scenario.phoneRoom()).stream()
                .filter(Request::active)
                .max(Comparator.comparing(Request::createdAt));
        assertThat(newestActive.map(Request::title).filter(title.strip()::equals).isPresent(),
                "後端這次住宿進行中、送出時間最新的需求是 " + newestActive.map(Request::title).orElse("（沒有）")
                        + "，不是「" + title + "」");
    }

    @那麼("後端這次住宿多了以下需求：")
    public void backendAdded(DataTable table) {
        List<String> expected = table.asMaps().stream()
                .map(row -> row.get("標題").strip() + "（" + row.get("狀況").strip() + "）")
                .sorted()
                .toList();
        String room = scenario.phoneRoom();
        frame.eventually(() -> {
            List<String> added = stay.addedSinceWhen(room).stream()
                    .map(r -> r.title() + "（" + r.situation() + "）")
                    .sorted()
                    .toList();
            return added.equals(expected) ? null : "後端這次住宿多了 " + added + "，應該剛好是 " + expected;
        });
        List<String> keys = createRecorder.sentKeys();
        for (Request added : stay.addedSinceWhen(room)) {
            assertThat(stay.createdWithOneOf(added.id(), keys),
                    "後端的「" + added.title() + "」不是帶著住客送出的冪等鍵建立的；這個場景送出的鍵是 " + keys);
        }
    }

    @那麼("後端這次住宿沒有多任何一張需求")
    public void backendUnchanged() {
        Optional<String> changed = stay.changedSinceWhen(scenario.phoneRoom());
        assertThat(changed.isEmpty(), "後端這次住宿的需求和 When 之前不同：" + changed.orElse(""));
    }

    @那麼("畫面出現提示框 {string}，內文 {string}")
    public void noticeShown(String title, String body) {
        List<String> expected = List.of(title.strip(), body.strip());
        Supplier<String> problem = () -> {
            Locator notices = frame.notices();
            if (notices.count() != 1) {
                return "畫面上應該剛好有一個提示框，實際有 " + notices.count() + " 個";
            }
            List<String> lines = frame.noticeLines(notices.first());
            if (!lines.equals(expected)) {
                return "提示框是 " + lines + "，應該是 " + expected;
            }
            if (world.page().getByRole(AriaRole.MAIN).getByRole(AriaRole.ALERT).count() == 0) {
                return "提示框不在頁面內容裡";
            }
            List<String> covered = frame.buttonsCoveredBy(notices.first());
            return covered.isEmpty() ? null : "提示框擋住了按鈕 " + covered;
        };
        frame.eventually(problem);
        world.page().waitForTimeout(OUTLIVES_TOAST_MS);
        String stillThere = problem.get();
        assertThat(stillThere == null, "提示框過了 " + OUTLIVES_TOAST_MS / 1000 + " 秒就不一樣了：" + stillThere);
    }

    private String screenProblem(GuestScreen screen) {
        if (frame.loading()) {
            return "畫面還標著載入中";
        }
        String path = frame.path();
        String problem = switch (screen) {
            case HOME -> {
                if (!path.matches("^/g/[^/]+/?$")) {
                    yield "網址不是首頁：" + path;
                }
                if (home.roomCapsule(scenario.phoneRoom()).count() == 0) {
                    yield "首頁看不到房號膠囊「" + scenario.phoneRoom() + "」";
                }
                yield home.heroCards(GuestHomePage.SUPPLY_CARD) > 0 && home.heroCards(GuestHomePage.REPAIR_CARD) > 0
                        ? null : "首頁沒有「" + GuestHomePage.SUPPLY_CARD + "」「" + GuestHomePage.REPAIR_CARD + "」兩張主視覺卡";
            }
            case SUPPLY, REPAIR, REQUESTS -> {
                String sub = screen.sub().orElseThrow();
                if (!path.matches("^/g/[^/]+/" + sub + "$")) {
                    yield "網址不是" + screen.term() + "：" + path;
                }
                String title = screen.title().orElseThrow();
                yield title.equals(frame.pageTitle()) ? null : "標題列是「" + frame.pageTitle() + "」，不是「" + title + "」";
            }
            case SENT -> {
                if (!path.matches("^/g/[^/]+/" + screen.sub().orElseThrow() + "$")) {
                    yield "網址不是送出成功頁：" + path;
                }
                yield visibleButton(VIEW_PROGRESS) ? null : "送出成功頁沒有「" + VIEW_PROGRESS + "」";
            }
            case UNAVAILABLE -> showsText(UNAVAILABLE_TITLE) ? null : "畫面上沒有「" + UNAVAILABLE_TITLE + "」";
            case RETRY -> showsText(RETRY_TITLE) && visibleButton(RETRY_BUTTON) ? null
                    : "畫面上沒有「" + RETRY_TITLE + "」與「" + RETRY_BUTTON + "」";
        };
        return problem;
    }

    private List<String> expectedLooks(String step) {
        if ("四步全亮".equals(step)) {
            return List.of("打勾", "打勾", "打勾", "打勾");
        }
        int current = GuestTerms.STEPS.indexOf(step.strip());
        if (current < 0 || current == GuestTerms.STEPS.size() - 1) {
            throw new IllegalArgumentException("步只有已送出、已受理、處理中、四步全亮、不顯示進度，收到「" + step + "」");
        }
        List<String> looks = new ArrayList<>();
        for (int i = 0; i < GuestTerms.STEPS.size(); i++) {
            looks.add(i < current ? "打勾" : i == current ? "實心" : "空心");
        }
        return looks;
    }

    private List<String> mainLines() {
        Locator main = world.page().getByRole(AriaRole.MAIN);
        return main.count() == 0 ? List.of() : ShellFrame.linesOf(main.first().innerText());
    }

    private boolean showsText(String text) {
        String wanted = ShellFrame.normalizeSpaces(text);
        return ShellFrame.linesOf(world.page().locator("body").innerText()).stream().anyMatch(line -> line.contains(wanted));
    }

    private boolean visibleButton(String name) {
        return world.page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true))
                .or(world.page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true)))
                .filter(new Locator.FilterOptions().setVisible(true)).count() > 0;
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
