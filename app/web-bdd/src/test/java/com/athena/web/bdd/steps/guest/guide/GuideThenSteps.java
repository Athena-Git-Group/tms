package com.athena.web.bdd.steps.guest.guide;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.pages.guest.GuidePage;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.那麼;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 「住宿指南」模組的 Then 句型。畫面跟上要一點時間，檢查一律重讀到成立為止，最多 5 秒。
 */
public class GuideThenSteps {

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestFrame frame;

    private final GuestHomePage home;

    private final GuidePage guide;

    private final InfoPages infoPages;

    public GuideThenSteps(WebWorld world, GuestScenario scenario) {
        this.scenario = scenario;
        this.phone = new GuestPhone(world, scenario);
        this.frame = new GuestFrame(world);
        this.home = new GuestHomePage(world);
        this.guide = new GuidePage(world);
        this.infoPages = new InfoPages(world);
    }

    /** 每一列看得到的字只有標題一行，右端有箭頭：箭頭是裝飾圖示，不算字。 */
    @那麼("首頁的住宿指南由上到下是 {string}")
    public void guideListIs(String titleList) {
        List<String> expected = Arrays.stream(titleList.split("、")).map(String::strip).toList();
        frame.eventually(() -> {
            List<List<String>> rows = guide.guideRows();
            List<String> titles = rows.stream().map(lines -> lines.isEmpty() ? "" : lines.get(0)).toList();
            if (!titles.equals(expected)) {
                return "住宿指南由上到下是 " + titles + "，應該是 " + expected;
            }
            if (!rows.stream().allMatch(lines -> lines.size() == 1)) {
                return "住宿指南的列上除了標題還有別的字：" + rows;
            }
            int noArrow = guide.guideRowsWithoutArrow();
            return noArrow == 0 ? null : "住宿指南有 " + noArrow + " 列右端沒有箭頭";
        });
    }

    @那麼("住宿指南的列上沒有圖片、營業時間或地點")
    public void guideRowsArePlain() {
        frame.eventually(() -> {
            List<List<String>> rows = guide.guideRows();
            if (rows.isEmpty()) {
                return "首頁沒有住宿指南的列";
            }
            if (guide.guideRowsWithImages() > 0) {
                return "住宿指南有 " + guide.guideRowsWithImages() + " 列帶著圖片";
            }
            return rows.stream().allMatch(lines -> lines.size() == 1) ? null
                    : "住宿指南的列上除了標題還有別的字：" + rows;
        });
    }

    @那麼("首頁 {string} 下面直接是 {string}")
    public void blockAfterServicesIs(String section, String text) {
        if (!GuidePage.SERVICES_SECTION.equals(section.strip())) {
            throw new IllegalArgumentException("這一句只看首頁的「" + GuidePage.SERVICES_SECTION + "」區段，收到「" + section + "」");
        }
        List<String> expected = List.of(text.strip());
        frame.eventually(() -> {
            List<String> next = guide.blockAfterServices();
            return next.equals(expected) ? null : "「" + section + "」下面那一塊的字是 " + next + "，應該只有 " + expected;
        });
    }

    /** 網址是後端設定裡這個標題那一頁的鍵，標題列的頁標題是它，畫面上沒有首頁的兩張主視覺卡與住宿指南。 */
    @那麼("畫面是 {string} 的資訊頁")
    public void screenIsInfoPage(String title) {
        Optional<Map<String, String>> page = infoPages.titled(title);
        if (page.isEmpty()) {
            throw new AssertionError("後端的資訊頁設定裡沒有「" + title + "」");
        }
        String path = GuestApp.path(phone.qrToken(scenario.phoneRoom()), GuestApp.guide(page.get().get("key")));
        frame.eventually(() -> {
            if (!frame.path().equals(path)) {
                return "網址是 " + frame.path() + "，不是「" + title + "」的資訊頁 " + path;
            }
            if (!title.strip().equals(frame.pageTitle())) {
                return "標題列是「" + frame.pageTitle() + "」，不是「" + title + "」";
            }
            boolean homeShown = home.heroCards(GuestHomePage.SUPPLY_CARD) > 0 || home.heroCards(GuestHomePage.REPAIR_CARD) > 0
                    || !guide.guideRows().isEmpty();
            return homeShown ? "資訊頁上還看得到首頁的內容" : null;
        });
    }

    @那麼("資訊頁的內文是 {string}")
    public void bodyIs(String body) {
        assertBody(List.of(body.strip()));
    }

    @那麼("資訊頁的內文是兩行 {string} 與 {string}")
    public void bodyIsTwoLines(String first, String second) {
        assertBody(List.of(first.strip(), second.strip()));
    }

    @那麼("資訊頁的內文是空白的")
    public void bodyIsEmpty() {
        frame.eventually(() -> frame.pageTitle().isEmpty() ? "資訊頁還沒畫完"
                : guide.bodyLines().isEmpty() ? null : "資訊頁的內容區有字：" + guide.bodyLines());
    }

    private void assertBody(List<String> expected) {
        frame.eventually(() -> {
            List<String> lines = guide.bodyLines();
            if (!lines.equals(expected)) {
                return "資訊頁的內文是 " + lines + "，應該是 " + expected;
            }
            return guide.bodyMedia() == 0 ? null : "資訊頁的內容區有圖片或影片";
        });
    }

}
