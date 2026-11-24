package com.athena.web.bdd.steps.answer;

import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.當;

public class AnswerWhenSteps {

    private static final String A_FEW_SPACES = "   ";

    private static final String ONE_CHINESE_CHARACTER = "字";

    private final AnswerPanel answer;

    public AnswerWhenSteps(WebWorld world) {
        this.answer = new AnswerPanel(world);
    }

    @當("{string} 點常用回覆 {string}")
    public void pickQuickReply(String staffName, String title) {
        answer.pickQuickReply(title);
    }

    @當("{string} 把回覆框改成 {string}")
    public void replaceReply(String staffName, String reply) {
        answer.clearReply();
        answer.typeIntoReply(reply);
    }

    @當("{string} 清空回覆框")
    public void clearReply(String staffName) {
        answer.clearReply();
    }

    @當("{string} 在回覆框只打了幾個空白")
    public void typeOnlySpaces(String staffName) {
        answer.typeIntoReply(A_FEW_SPACES);
    }

    @當("{string} 在回覆框打 {int} 個字")
    public void typeCharacters(String staffName, int count) {
        answer.typeIntoReply(ONE_CHINESE_CHARACTER.repeat(count));
    }
}
