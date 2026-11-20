package com.athena.web.bdd.steps.guest.guide;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 「住宿指南」模組的 Given 句型：改後端的資訊頁設定，畫面已經開著時重新載入讓它生效。 */
public class GuideGivenSteps {

    private final WebWorld world;

    private final GuestPhone phone;

    private final GuestFrame frame;

    private final InfoPages infoPages;

    public GuideGivenSteps(WebWorld world, GuestScenario scenario) {
        this.world = world;
        this.phone = new GuestPhone(world, scenario);
        this.frame = new GuestFrame(world);
        this.infoPages = new InfoPages(world);
    }

    /** 每一頁的鍵照表格順序編成 {@code info-1}、{@code info-2}…；表格的空格（Cucumber 給 {@code null}）設成空字串。 */
    @假設("飯店設定了以下資訊頁：")
    public void infoPagesAre(DataTable table) {
        List<Map<String, String>> pages = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            pages.add(InfoPages.page("info-" + (pages.size() + 1), text(row.get("標題")), text(row.get("內文"))));
        }
        apply(pages);
    }

    @假設("飯店沒有設定任何資訊頁")
    public void noInfoPages() {
        apply(List.of());
    }

    @假設("飯店的 {string} 資訊頁內文是兩行 {string} 與 {string}")
    public void bodyHasTwoLines(String title, String first, String second) {
        List<Map<String, String>> pages = new ArrayList<>(infoPages.read());
        int index = -1;
        for (int i = 0; i < pages.size(); i++) {
            if (title.strip().equals(pages.get(i).get("title"))) {
                index = i;
            }
        }
        if (index < 0) {
            throw new IllegalStateException("飯店的資訊頁裡沒有「" + title + "」，有的是 "
                    + pages.stream().map(page -> page.get("title")).toList());
        }
        Map<String, String> page = pages.get(index);
        pages.set(index, InfoPages.page(page.get("key"), page.get("title"), first + "\n" + second));
        apply(pages);
    }

    private void apply(List<Map<String, String>> pages) {
        infoPages.write(pages);
        if (world.hasPage() && frame.path().startsWith("/g/")) {
            phone.reload();
        }
    }

    private static String text(String cell) {
        return cell == null ? "" : cell.strip();
    }
}
