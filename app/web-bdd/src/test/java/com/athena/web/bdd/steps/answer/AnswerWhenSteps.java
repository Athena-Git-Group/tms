package com.athena.web.bdd.steps.answer;

import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.當;

/**
 * 「當場回答」模組的 When 句型。
 * 這幾句都在前面 Given 已經打開的當場回答面板上操作；句中的員工只是讓句子讀得通，登入的是誰由 Given 決定。
 */
public class AnswerWhenSteps {

    /** 「只打了幾個空白」的預設：三個半形空白，用來確認只有空白的回覆不能送出。 */
    private static final String A_FEW_SPACES = "   ";

    /** 「打幾個字」的預設是中文字；每一個都用同一個字，案例只在乎字數，不在乎內容。 */
    private static final String ONE_CHINESE_CHARACTER = "字";

    private final AnswerPanel answer;

    public AnswerWhenSteps(WebWorld world) {
        this.answer = new AnswerPanel(world);
    }

    @當("{string} 點常用回覆 {string}")
    public void pickQuickReply(String staffName, String title) {
        answer.pickQuickReply(title);
    }

    /** 要先清掉再打，否則新打的字會接在常用回覆帶進來的全文後面。 */
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

    /** 超過字數上限也照打不截斷：要驗的是畫面自己擋掉多打的字。 */
    @當("{string} 在回覆框打 {int} 個字")
    public void typeCharacters(String staffName, int count) {
        answer.typeIntoReply(ONE_CHINESE_CHARACTER.repeat(count));
    }
}
