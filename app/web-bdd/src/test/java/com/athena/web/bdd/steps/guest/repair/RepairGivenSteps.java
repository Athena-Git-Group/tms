package com.athena.web.bdd.steps.guest.repair;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RepairPage;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.zh_tw.假設;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RepairGivenSteps {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CATEGORY_SEPARATOR = "：";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestStay stay;

    private final RepairDraft draft;

    private final GuestFrame frame;

    private final RepairPage repair;

    public RepairGivenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, GuestStay stay,
            RepairDraft draft) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.stay = stay;
        this.draft = draft;
        this.frame = new GuestFrame(world);
        this.repair = new RepairPage(world);
    }

    @假設("這家飯店的分類規則沒有對 {string} 的規則")
    public void noRuleFor(String keyword) {
        String wanted = keyword.strip().toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (Map<String, Object> row : world.backend().queryRows(
                "SELECT value::text AS rules FROM tenant_config WHERE tenant_id = ? AND key = 'classification_rules'",
                world.defaults().tenantId())) {
            for (JsonNode rule : parse((String) row.get("rules"))) {
                for (JsonNode ruleKeyword : rule.path("keywords")) {
                    String k = ruleKeyword.asText().toLowerCase(Locale.ROOT);
                    if (!k.isEmpty() && (wanted.contains(k) || k.contains(wanted))) {
                        hits.add(rule.path("rule_id").asText() + "（" + ruleKeyword.asText() + "）");
                    }
                }
            }
        }
        if (!hits.isEmpty()) {
            throw new IllegalStateException("預設飯店有會命中「" + keyword + "」的分類規則：" + hits);
        }
    }

    @假設("這家飯店的分類規則把含 {string} 的文字分給工務")
    public void ruleRoutesToEngineering(String keyword) {
        world.backend().setClassificationRule(keyword.strip(), "engineering", "repair");
    }

    @假設("{string} 房的住客在設備報修頁")
    public void onRepairPage(String roomNo) {
        phone.ensureHome(roomNo);
        phone.openScreen(roomNo, GuestScreen.REPAIR);
        frame.waitAtMost(world.page(), () -> repair.checkedCategories().isEmpty() && repair.noteValue().isEmpty());
    }

    @假設("{string} 房的住客在設備報修頁，已選類別 {string}")
    public void onRepairPageWithCategory(String roomNo, String category) {
        onRepairPage(roomNo);
        chooseCategory(category);
    }

    @假設("{string} 房的住客在設備報修頁，已選類別 {string}，補充說明已輸入 {string}")
    public void onRepairPageWithNote(String roomNo, String category, String note) {
        onRepairPageWithCategory(roomNo, category);
        typeNote(note);
    }

    @假設("{string} 房的住客在設備報修頁，已選類別 {string}，補充說明已有 200 個字")
    public void onRepairPageWithFullNote(String roomNo, String category) {
        onRepairPageWithCategory(roomNo, category);
        String note = RepairDraft.chineseChars(RepairPage.NOTE_LIMIT);
        typeNote(note);
        draft.rememberFullNote(note);
    }

    @假設("{string} 房的住客剛送出報修 {string}，停在送出成功頁")
    public void justSubmittedRepair(String roomNo, String title) {
        String text = title.strip();
        int separator = text.indexOf(CATEGORY_SEPARATOR);
        if (separator > 0 && RepairPage.CATEGORIES.contains(text.substring(0, separator))) {
            onRepairPageWithNote(roomNo, text.substring(0, separator), text.substring(separator + 1));
        } else if (RepairPage.CATEGORIES.contains(text)) {
            onRepairPageWithCategory(roomNo, text);
        } else {
            onRepairPageWithNote(roomNo, RepairPage.OTHER, text);
        }
        frame.press(RepairPage.SUBMIT);
        phone.waitUntilShown(roomNo, GuestScreen.SENT);
        scenario.rememberRequest(text, stay.newest(roomNo, text).id());
    }

    private void chooseCategory(String category) {
        String language = frame.language();
        frame.settleAfter(() -> repair.chooseCategory(category, language));
        String shown = RepairPage.categoryIn(language, category);
        frame.waitAtMost(world.page(), () -> repair.checkedCategories().equals(List.of(shown)));
    }

    private void typeNote(String note) {
        String expected = RepairPage.lengthOf(note) + " / " + RepairPage.NOTE_LIMIT;
        repair.typeNote(note);
        frame.waitAtMost(world.page(), () -> repair.counter().filter(expected::equals).isPresent());
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("讀不懂預設飯店的分類規則：" + json, e);
        }
    }
}
