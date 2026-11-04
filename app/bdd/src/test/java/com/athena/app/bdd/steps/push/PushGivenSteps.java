package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/** 推播模組的 Given。 */
public class PushGivenSteps {

    private final AppiumWorld world;

    public PushGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料建立去處所指的任務，再送出一則推播 payload 給裝置，內容依
     * 後端推播格式帶任務 id 或待認領入口。App 處於背景。
     * accessibility id：{@code system.push-banner}、{@code system.push-banner.title}。
     */
    @假設("{string} 收到一則推播 {string}，去處為 {string}")
    public void receivesPushWithDestination(String staffName, String title, String destination) {
        throw new PendingException();
    }
}
