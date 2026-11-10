package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

public class AskWhenSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AppiumWorld world;

    public AskWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    @當("{string} 在 {string} 的任務頁打開查一下")
    public void openAsk(String staffName, String task) {
        world.openAskPanel(world.staff(staffName), task);
    }

    @當("{string} 點選推薦問題 {string}")
    public void tapSuggestedQuestion(String staffName, String question) {
        world.tapTestId("ask.suggestion." + AskPanel.indexOf(world, question), TIMEOUT);
        AskPanel.waitForAnswerSettled(world, TIMEOUT);
    }
}
