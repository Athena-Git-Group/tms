package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 查一下面板的讀取工具：把「畫面上現在有哪幾則推薦問題、各自的問題與範圍是什麼」
 * 收在一處，給 When（要按第幾則）與 Then（順序與內容對不對）共用。
 *
 * <p>推薦問題的則數是執行期才知道的，所以先用 id 前綴掃出有哪幾個
 * {@code .question} 節點，再按同一個索引去讀它的 {@code .scope}。
 */
final class AskPanel {

    private AskPanel() {
    }

    /** 一則推薦問題：問題本身與範圍標籤。 */
    record Suggestion(String question, String scope) {
    }

    /** 畫面上的推薦問題，依畫面由上而下的順序。 */
    static List<Suggestion> suggestions(AppiumWorld world) {
        List<Suggestion> rows = new ArrayList<>();
        for (String questionId : world.findTestIdsWithPrefix("ask.suggestion.")) {
            if (!questionId.endsWith(".question")) {
                continue;
            }
            String scopeId = questionId.substring(0, questionId.length() - ".question".length()) + ".scope";
            rows.add(new Suggestion(
                    String.valueOf(world.labelOf(questionId)),
                    String.valueOf(world.labelOf(scopeId))));
        }
        return rows;
    }

    /** 第幾則推薦問題的文字是這一句；找不到時噴錯並列出實際有哪幾則。 */
    static int indexOf(AppiumWorld world, String question) {
        List<Suggestion> suggestions = suggestions(world);
        for (int i = 0; i < suggestions.size(); i++) {
            if (question.equals(suggestions.get(i).question())) {
                return i;
            }
        }
        throw new AssertionError("查一下面板沒有推薦問題「" + question + "」（實際＝" + describe(suggestions) + "）");
    }

    /** 一張出處卡的四欄。 */
    record SourceCard(String type, String title, String excerpt, String date) {
    }

    /** 畫面上的出處卡，依畫面由上而下的順序。 */
    static List<SourceCard> sourceCards(AppiumWorld world) {
        List<SourceCard> cards = new ArrayList<>();
        for (String typeId : world.findTestIdsWithPrefix("ask.answer.source-card.")) {
            if (!typeId.endsWith(".type")) {
                continue;
            }
            String prefix = typeId.substring(0, typeId.length() - ".type".length());
            cards.add(new SourceCard(
                    String.valueOf(world.labelOf(typeId)),
                    String.valueOf(world.labelOf(prefix + ".title")),
                    String.valueOf(world.labelOf(prefix + ".excerpt")),
                    String.valueOf(world.labelOf(prefix + ".date"))));
        }
        return cards;
    }

    /**
     * 等這一次提問的結果出現：查得到時是回答區，查不到時是查不到訊息——DSL 的
     * 「等回答載入完成」兩種結局都算載完，所以兩個節點任一個出現就回來。
     */
    static void waitForAnswerSettled(AppiumWorld world, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (world.labelOf("ask.answer.text") != null
                    || world.labelOf("ask.not-found-message") != null) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("查一下沒有在 " + timeout.getSeconds()
                + "s 內給出結果（既沒有回答區 ask.answer.text，也沒有查不到訊息 ask.not-found-message）");
    }

    static String describe(List<Suggestion> suggestions) {
        return suggestions.stream()
                .map(s -> "「" + s.question() + "／" + s.scope() + "」")
                .toList()
                .toString();
    }
}
