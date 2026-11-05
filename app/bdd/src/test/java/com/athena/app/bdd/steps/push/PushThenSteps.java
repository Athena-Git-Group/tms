package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.那麼;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 推播模組的 Then。 */
public class PushThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(6);

    private static final Pattern POOL_HIGHLIGHTED = Pattern.compile("^待認領，(\\d+) 標亮$");
    private static final Pattern TASK_SCREEN = Pattern.compile("^(\\d+) 的任務頁$");
    private static final Pattern HOME = Pattern.compile("^首頁$");
    private static final Pattern POOL_PLAIN = Pattern.compile("^待認領$");

    private final AppiumWorld world;

    public PushThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：目前畫面符合描述；有標亮時，該房的單卡在可視範圍內並有標亮樣式。
     *
     * <p>「畫面」字串跟 {@link PushGivenSteps} 解析「去處」用的是同一套四種白話形式
     * （dsl.md），這裡對應回 accessibility id：標亮用 {@code pool.card.{roomNo}--highlighted}
     * （{@code lib/features/pool/pool_screen.dart} 只在標亮時才掛這個 id，存在即代表
     * 樣式也對）；任務頁沒有獨立的房號節點，房號斷言改抓導覽列標題文字（本身就含房號）。
     */
    @那麼("App 停在 {string}")
    public void appStaysOn(String screenDescription) {
        Matcher highlightedMatcher = POOL_HIGHLIGHTED.matcher(screenDescription);
        Matcher taskMatcher = TASK_SCREEN.matcher(screenDescription);

        if (highlightedMatcher.matches()) {
            String room = highlightedMatcher.group(1);
            world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
            world.waitForTestId("pool.card." + room + "--highlighted", SCREEN_TIMEOUT);
        } else if (taskMatcher.matches()) {
            String room = taskMatcher.group(1);
            world.waitForTestId("task.screen", SCREEN_TIMEOUT);
            world.waitForLabelContaining(room + " 的任務頁", SCREEN_TIMEOUT);
        } else if (HOME.matcher(screenDescription).matches()) {
            world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        } else if (POOL_PLAIN.matcher(screenDescription).matches()) {
            world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
        } else {
            throw new IllegalArgumentException("認不得的畫面字串：「" + screenDescription + "」");
        }
    }

    /**
     * 必查：任務頁顯示工務已修好的提示，並有可點的恢復按鈕；後端任務仍為 interrupted。
     * accessibility id：{@code task.resume-banner}、{@code task.status-row.resume-button}。
     */
    @那麼("任務頁提示可以恢復 {string}")
    public void taskDetailOffersResume(String task) {
        world.waitForTestId("task.resume-banner", SCREEN_TIMEOUT);
        world.waitForTestId("task.status-row.resume-button", SCREEN_TIMEOUT);
        world.waitForLabelContaining(task, SCREEN_TIMEOUT);
    }
}
