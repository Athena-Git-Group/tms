package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 查一下模組的 Then。 */
public class AskThenSteps {

    private final AppiumWorld world;

    public AskThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：推薦問題清單與範圍標籤的順序和內容剛好等於表格。
     * accessibility id：{@code ask.suggestion.{index}}。
     */
    @那麼("查一下畫面的推薦問題依序為：")
    public void askSuggestionsOrderIs(DataTable table) {
        throw new PendingException();
    }

    /** 必查：回答區全文相等，並與後端回應一致。accessibility id：TODO（回答區節點尚未命名）。 */
    @那麼("查一下畫面顯示回答 {string}")
    public void askShowsAnswer(String answer) {
        throw new PendingException();
    }

    /**
     * 必查：出處卡剛好等於表格。
     * accessibility id：{@code ask.answer.source-card}、{@code ask.answer.source-card.type}、
     * {@code ask.answer.source-card.date}。
     */
    @那麼("回答下方的出處卡如下：")
    public void askShowsSourceCards(DataTable table) {
        throw new PendingException();
    }

    /**
     * 必查：畫面顯示該訊息，回答區與出處卡都是空的。accessibility id：{@code ask.not-found-message}。
     */
    @那麼("查一下畫面顯示 {string}，沒有任何回答內容")
    public void askShowsMessageWithNoAnswer(String message) {
        throw new PendingException();
    }

    /** 必查：有可點的問主管按鈕。accessibility id：{@code ask.ask-manager-button}。 */
    @那麼("查一下畫面提供問主管按鈕")
    public void askOffersAskManagerButton() {
        throw new PendingException();
    }
}
