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

/** 「設備報修」模組的 Given 句型（{@code specs/truth/features/guest-web/設備報修/dsl.md}）。 */
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

    /**
     * 預設飯店沒有任何一條分類規則會命中含這個關鍵字的文字：規則的關鍵字出現在這個關鍵字裡，或這個關鍵字出現在
     * 規則的關鍵字裡，都算會命中（後端以「文字含規則關鍵字」比對，不分大小寫）。每個場景開頭清空業務資料，預設就沒有規則。
     */
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

    /** 替預設飯店加一條分類規則：文字含這個關鍵字時分到工務、類型是修繕。 */
    @假設("這家飯店的分類規則把含 {string} 的文字分給工務")
    public void ruleRoutesToEngineering(String keyword) {
        world.backend().setClassificationRule(keyword.strip(), "engineering", "repair");
    }

    /** 開首頁再開設備報修頁，等它畫完：沒有選類別、補充說明是空的。 */
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

    /** 同上，補充說明打滿 200 個測試自編、不含空白的中文字，記下這 200 個字。 */
    @假設("{string} 房的住客在設備報修頁，已選類別 {string}，補充說明已有 200 個字")
    public void onRepairPageWithFullNote(String roomNo, String category) {
        onRepairPageWithCategory(roomNo, category);
        String note = RepairDraft.chineseChars(RepairPage.NOTE_LIMIT);
        typeNote(note);
        draft.rememberFullNote(note);
    }

    /**
     * 開設備報修頁，照標題選類別與輸入補充說明，按「送出報修」，等送出成功頁畫完，記下建立的需求。
     * 標題拆法照送出的文字反推：「{類別}：{補充說明}」拆開；剛好是一個類別名稱時只選類別；其餘是「其他」加補充說明。
     */
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
