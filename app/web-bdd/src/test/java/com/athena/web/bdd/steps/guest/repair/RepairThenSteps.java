package com.athena.web.bdd.steps.guest.repair;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RepairPage;
import com.athena.web.bdd.support.GuestRequestLimitStub;
import com.athena.web.bdd.support.GuestRequestLimitStub.SentCreate;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.GuestStay.Request;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.zh_tw.那麼;
import java.util.List;
import java.util.Optional;

/**
 * 「設備報修」模組的 Then 句型（{@code specs/truth/features/guest-web/設備報修/dsl.md}）。
 * 畫面會晚一點才跟上，每一句都重讀到成立為止，最多 5 秒。
 */
public class RepairThenSteps {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestStay stay;

    private final GuestRequestLimitStub limitStub;

    private final RepairDraft draft;

    private final GuestFrame frame;

    private final RepairPage repair;

    public RepairThenSteps(WebWorld world, GuestScenario scenario, GuestStay stay, GuestRequestLimitStub limitStub,
            RepairDraft draft) {
        this.world = world;
        this.scenario = scenario;
        this.stay = stay;
        this.limitStub = limitStub;
        this.draft = draft;
        this.frame = new GuestFrame(world);
        this.repair = new RepairPage(world);
    }

    /** 六顆膠囊裡只有這一顆是選中的樣子，其餘五顆都不是。 */
    @那麼("只有 {string} 一顆類別是選中的")
    public void onlyCategoryChecked(String category) {
        frame.eventually(() -> {
            List<String> expected = List.of(RepairPage.categoryIn(frame.language(), category));
            List<String> checked = repair.checkedCategories();
            return checked.equals(expected) ? null : "選中的類別是 " + checked + "，應該只有 " + expected;
        });
    }

    /** 補充說明是空的，佔位提示字照字面相符。 */
    @那麼("補充說明的提示字是 {string}")
    public void notePlaceholderIs(String placeholder) {
        frame.eventually(() -> {
            String value = repair.noteValue();
            if (!value.isEmpty()) {
                return "補充說明不是空的：「" + value + "」";
            }
            String shown = repair.notePlaceholder();
            return shown.equals(placeholder) ? null : "補充說明的提示字是「" + shown + "」，不是「" + placeholder + "」";
        });
    }

    @那麼("字數是 {string}")
    public void counterIs(String counter) {
        frame.eventually(() -> {
            Optional<String> shown = repair.counter();
            return shown.filter(counter.strip()::equals).isPresent() ? null
                    : "輸入框下方的字數是 " + shown.map(s -> "「" + s + "」").orElse("（沒有）") + "，不是「" + counter + "」";
        });
    }

    /** 輸入框的內容就是前置打滿的那 200 個字，When 打的字沒有進去。 */
    @那麼("補充說明裡仍是原本的 200 個字")
    public void noteStillFull() {
        String expected = draft.fullNote();
        frame.eventually(() -> {
            String value = repair.noteValue();
            return value.equals(expected) ? null
                    : "補充說明有 " + RepairPage.lengthOf(value) + " 個字，和前置打的 200 個字不同：「" + value + "」";
        });
    }

    /**
     * 這次 When 建立的那張需求在後端是自由文字：住客原文有值、沒有服務項目的鍵，語言是該語言的代碼；
     * 而且這個分頁送出的建立請求都沒有帶 {@code itemId}。
     */
    @那麼("後端這張需求是打字的、帶語言 {string}，沒有帶服務項目")
    public void backendIsTypedRequest(String language) {
        String code = GuestTerms.languageCode(language);
        String room = scenario.phoneRoom();
        frame.eventually(() -> {
            List<Request> added = stay.addedSinceWhen(room);
            if (added.size() != 1) {
                return "這次 When 應該剛好建立一張需求，後端多了 " + added.stream().map(Request::title).toList();
            }
            JsonNode note = note(added.get(0));
            if (note.path("text").asText("").isBlank()) {
                return "後端「" + added.get(0).title() + "」沒有住客原文：" + note;
            }
            if (note.has("menu_key")) {
                return "後端「" + added.get(0).title() + "」帶了服務項目：" + note;
            }
            String lang = note.path("lang").asText(null);
            return code.equals(lang) ? null
                    : "後端「" + added.get(0).title() + "」的語言是 " + lang + "，不是 " + code + "（" + language + "）";
        });
        List<SentCreate> fromThisPage = limitStub.creates().stream()
                .filter(create -> create.page() == world.page())
                .toList();
        assertThat(!fromThisPage.isEmpty(), "這個分頁沒有送出任何建立需求的請求");
        for (SentCreate create : fromThisPage) {
            assertThat(!parse(create.body()).has("itemId"), "這個分頁送出的建立請求帶了 itemId：" + create.body());
        }
    }

    private JsonNode note(Request request) {
        Object note = world.backend().queryRows("SELECT note FROM tasks WHERE tenant_id = ? AND id = ?",
                world.defaults().tenantId(), request.id()).get(0).get("note");
        return parse(note == null ? "" : note.toString());
    }

    private static JsonNode parse(String json) {
        try {
            return json.isBlank() ? JSON.createObjectNode() : JSON.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("讀不懂這段 JSON：" + json, e);
        }
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
