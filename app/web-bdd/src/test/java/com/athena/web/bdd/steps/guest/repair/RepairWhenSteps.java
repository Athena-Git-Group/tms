package com.athena.web.bdd.steps.guest.repair;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RepairPage;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.當;
import java.util.List;

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

    @當("住客點類別 {string}")
    public void guestChoosesCategory(String category) {
        String language = frame.language();
        frame.settleAfter(() -> repair.chooseCategory(category, language));
        scenario.countTap();
        String shown = RepairPage.categoryIn(language, category);
        frame.waitAtMost(world.page(), () -> repair.checkedCategories().equals(List.of(shown)));
    }

    @當("住客在補充說明輸入 {string}")
    public void guestTypesNote(String text) {
        type(text);
    }

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
