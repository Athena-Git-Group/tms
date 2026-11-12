package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class AskPanel {

    private AskPanel() {
    }

    record Suggestion(String question, String scope) {
    }

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

    static int indexOf(AppiumWorld world, String question) {
        List<Suggestion> suggestions = suggestions(world);
        for (int i = 0; i < suggestions.size(); i++) {
            if (question.equals(suggestions.get(i).question())) {
                return i;
            }
        }
        throw new AssertionError("查一下面板沒有推薦問題「" + question + "」（實際＝" + describe(suggestions) + "）");
    }

    record SourceCard(String type, String title, String excerpt, String date) {
    }

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
