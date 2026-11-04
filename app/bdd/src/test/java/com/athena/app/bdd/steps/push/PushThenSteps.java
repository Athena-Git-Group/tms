package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 推播模組的 Then。 */
public class PushThenSteps {

    private final AppiumWorld world;

    public PushThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 必查：目前畫面符合描述；有標亮時，該房的單卡在可視範圍內並有標亮樣式。 */
    @那麼("App 停在 {string}")
    public void appStaysOn(String screenDescription) {
        throw new PendingException();
    }

    /**
     * 必查：任務頁顯示工務已修好的提示，並有可點的恢復按鈕；後端任務仍為 interrupted。
     * accessibility id：{@code task.status-row.resume-button}。
     */
    @那麼("任務頁提示可以恢復 {string}")
    public void taskDetailOffersResume(String task) {
        throw new PendingException();
    }
}
