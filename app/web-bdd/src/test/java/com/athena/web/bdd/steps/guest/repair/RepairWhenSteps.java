package com.athena.web.bdd.steps.guest.repair;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RepairPage;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.當;
import java.util.List;

/**
 * 「設備報修」模組的 When 句型（{@code specs/truth/features/guest-web/設備報修/dsl.md}）。
 *
 * <p>每一句最多等 5 秒讓畫面跟上，等不到也往下走：補充說明打滿 200 個字之後再打，字數會停在 200，
 * 畫面對不對由結果句判斷。
 */
public class RepairWhenSteps {

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestFrame frame;

    private final RepairPage repair;

    public RepairWhenSteps(WebWorld world, GuestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.frame = new GuestFrame(world);
        this.repair = new RepairPage(world);
    }

    /** 點單選群組裡的這一顆；等膠囊的選中狀態與頁底按鈕跟上。 */
    @當("住客點類別 {string}")
    public void guestChoosesCategory(String category) {
        String language = frame.language();
        frame.settleAfter(() -> repair.chooseCategory(category, language));
        scenario.countTap();
        String shown = RepairPage.categoryIn(language, category);
        frame.waitAtMost(world.page(), () -> repair.checkedCategories().equals(List.of(shown)));
    }

    /** 照字面逐字打進補充說明，含空白；等字數跟上。 */
    @當("住客在補充說明輸入 {string}")
    public void guestTypesNote(String text) {
        type(text);
    }

    /** 打進這麼多個測試自編的中文字；等字數跟上。 */
    @當("住客在補充說明輸入 {int} 個字")
    public void guestTypesChars(int count) {
        type(RepairDraft.chineseChars(count));
    }

    private void type(String text) {
        int before = RepairPage.lengthOf(repair.noteValue());
        int expected = Math.min(before + RepairPage.lengthOf(text), RepairPage.NOTE_LIMIT);
        String counter = expected + " / " + RepairPage.NOTE_LIMIT;
        repair.typeNote(text);
        scenario.countInput();
        frame.waitAtMost(world.page(), () -> repair.counter().filter(counter::equals).isPresent());
    }
}
