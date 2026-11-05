package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

/** 推播模組的 When。 */
public class PushWhenSteps {

    private final AppiumWorld world;

    public PushWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 怎麼做：在通知中心點這則推播，等 App 切到前景並完成導頁。 */
    @當("{string} 點開這則推播")
    public void tapThisPush(String staffName) {
        world.tapPushNotification();
    }
}
