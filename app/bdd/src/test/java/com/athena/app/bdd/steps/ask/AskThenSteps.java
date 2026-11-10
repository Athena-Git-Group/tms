package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AskThenSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AppiumWorld world;

    public AskThenSteps(AppiumWorld world) {
        this.world = world;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }

    @那麼("查一下畫面的推薦問題依序為：")
    public void askSuggestionsOrderIs(DataTable table) {
        List<Map<String, String>> expectedRows = table.asMaps(String.class, String.class);
        List<AskPanel.Suggestion> actual = AskPanel.suggestions(world);
        if (actual.size() != expectedRows.size()) {
            throw new AssertionError("推薦問題預期 " + expectedRows.size() + " 則，實際 "
                    + actual.size() + " 則：" + AskPanel.describe(actual));
        }
        for (int i = 0; i < expectedRows.size(); i++) {
            String question = requireCell(expectedRows.get(i), "問題");
            String scope = requireCell(expectedRows.get(i), "範圍");
            if (!question.equals(actual.get(i).question()) || !scope.equals(actual.get(i).scope())) {
                throw new AssertionError("第 " + (i + 1) + " 則推薦問題預期「" + question + "／" + scope
                        + "」，實際為「" + actual.get(i).question() + "／" + actual.get(i).scope() + "」");
            }
        }
    }

    /**
     * 必查：回答區全文相等，並與後端回應一致。accessibility id：{@code ask.answer.text}。
     *
     * <p>「與後端回應一致」的權威讀回走稽核紀錄：每次提問後端都寫一筆
     * {@code kb_audit_logs}，裡面的 {@code answer} 就是它回給 App 的那段文字。比對它
     * 才證明得了畫面上的字是後端給的、不是 App 自己生的。
     */
    @那麼("查一下畫面顯示回答 {string}")
    public void askShowsAnswer(String answer) {
        String shown = world.waitForLabelOf("ask.answer.text", answer, TIMEOUT);
        if (!answer.equals(shown)) {
            throw new AssertionError("查一下的回答預期為「" + answer + "」，實際為「" + shown + "」");
        }
        String recorded = String.valueOf(latestAuditLog().get("answer"));
        if (!answer.equals(recorded)) {
            throw new AssertionError("後端稽核紀錄裡的回答為「" + recorded + "」，與畫面上的「"
                    + answer + "」不一致");
        }
    }

    private Map<String, Object> latestAuditLog() {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT * FROM kb_audit_logs WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 1",
                world.backend().ensureTenant());
        if (rows.isEmpty()) {
            throw new AssertionError("後端沒有任何查一下的稽核紀錄——這次提問有真的送到後端嗎？");
        }
        return rows.get(0);
    }

    @那麼("回答下方的出處卡如下：")
    public void askShowsSourceCards(DataTable table) {
        List<Map<String, String>> expectedRows = table.asMaps(String.class, String.class);
        if (!expectedRows.isEmpty()) {
            world.waitForTestId("ask.answer.source-card.0", TIMEOUT);
        }
        List<AskPanel.SourceCard> actual = AskPanel.sourceCards(world);
        if (actual.size() != expectedRows.size()) {
            throw new AssertionError("出處卡預期 " + expectedRows.size() + " 張，實際 "
                    + actual.size() + " 張：" + actual);
        }
        for (int i = 0; i < expectedRows.size(); i++) {
            Map<String, String> expected = expectedRows.get(i);
            AskPanel.SourceCard card = actual.get(i);
            assertCell(i, "類型", requireCell(expected, "類型"), card.type());
            assertCell(i, "標題", requireCell(expected, "標題"), card.title());
            assertCell(i, "內容", requireCell(expected, "內容"), card.excerpt());
            assertCell(i, "日期", requireCell(expected, "日期"), card.date());
        }
    }

    private static void assertCell(int index, String column, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("第 " + (index + 1) + " 張出處卡的「" + column + "」預期為「"
                    + expected + "」，實際為「" + actual + "」");
        }
    }

    /**
     * 必查：畫面顯示該訊息，回答區與出處卡都是空的。accessibility id：{@code ask.not-found-message}。
     *
     * <p>訊息比對用「含有」：查不到時畫面上那句由兩段組成——「查不到 {房號} 的相關記錄」
     * 加上「系統不會用猜的回答」（ui-plan 畫面 8 的失敗回饋），truth 點名的是前一段。
     * 「沒有任何回答內容」則是逐字照字面驗：回答區與出處卡兩種節點一個都不能在畫面上，
     * 不是存在但內容為空。
     */
    @那麼("查一下畫面顯示 {string}，沒有任何回答內容")
    public void askShowsMessageWithNoAnswer(String message) {
        world.waitForTestId("ask.not-found-message", TIMEOUT);
        String shown = String.valueOf(world.labelOf("ask.not-found-message"));
        if (!shown.contains(message)) {
            throw new AssertionError("查不到訊息預期含有「" + message + "」，實際為「" + shown + "」");
        }
        String answerShown = world.labelOf("ask.answer.text");
        if (answerShown != null) {
            throw new AssertionError("查不到時畫面不該有回答區，實際出現了「" + answerShown + "」");
        }
        List<AskPanel.SourceCard> cards = AskPanel.sourceCards(world);
        if (!cards.isEmpty()) {
            throw new AssertionError("查不到時畫面不該有出處卡，實際出現了 " + cards.size() + " 張：" + cards);
        }
    }

    @那麼("查一下畫面提供問主管按鈕")
    public void askOffersAskManagerButton() {
        if (!world.waitForTestId("ask.ask-manager-button", TIMEOUT).isEnabled()) {
            throw new AssertionError("查一下畫面的問主管按鈕存在但不可點");
        }
    }
}
