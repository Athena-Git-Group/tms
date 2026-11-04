package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.當;

/** 查一下模組的 When。 */
public class AskWhenSteps {

    private final AppiumWorld world;

    public AskWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：進入任務頁，點查一下，等推薦問題載入完成。
     * accessibility id：{@code ask.suggestion.{index}}。
     */
    @當("{string} 在 {string} 的任務頁打開查一下")
    public void openAsk(String staffName, String task) {
        throw new PendingException();
    }

    /** 怎麼做：點該推薦問題，等回答載入完成。accessibility id：{@code ask.suggestion.{index}}。 */
    @當("{string} 點選推薦問題 {string}")
    public void tapSuggestedQuestion(String staffName, String question) {
        throw new PendingException();
    }
}
