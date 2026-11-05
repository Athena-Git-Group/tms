package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.假設;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 推播模組的 Given。 */
public class PushGivenSteps {

    private static final Pattern TASK_SCREEN = Pattern.compile("^(\\d+) 的任務頁$");
    private static final Pattern POOL_HIGHLIGHT = Pattern.compile("^待認領並標亮 (\\d+)$");
    private static final Pattern HOME = Pattern.compile("^首頁$");
    private static final Pattern POOL_PLAIN = Pattern.compile("^待認領$");

    private final AppiumWorld world;

    public PushGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料建立去處所指的任務，再送出一則推播 payload 給裝置，內容依
     * 後端推播格式帶任務 id 或待認領入口。App 處於背景。
     *
     * <p>「去處」是給人看的白話字串（dsl.md 的四種形式：X 的任務頁、待認領、待認領並
     * 標亮 X、首頁），這裡解析成 {@code lib/core/push_gateway.dart} 認得的
     * {@code dest}/{@code room}/{@code highlight} 欄位。去處指向「待認領並標亮 X」時，
     * 待認領池要有一張房號 X、狀態待認領的卡才有東西可以標亮，所以連帶補上這張卡的
     * fixture（{@link AppiumWorld#ensurePoolTask}）；指向任務頁時不用額外建資料，
     * 任務頁的標題本身就帶房號，不靠 fixture 存在與否。
     */
    @假設("{string} 收到一則推播 {string}，去處為 {string}")
    public void receivesPushWithDestination(String staffName, String title, String destination) {
        Matcher taskMatcher = TASK_SCREEN.matcher(destination);
        Matcher highlightMatcher = POOL_HIGHLIGHT.matcher(destination);

        if (taskMatcher.matches()) {
            world.sendPush(title, "", "task", taskMatcher.group(1), null);
        } else if (highlightMatcher.matches()) {
            String room = highlightMatcher.group(1);
            world.ensurePoolTask(room, title);
            world.sendPush(title, "", "pool", null, room);
        } else if (HOME.matcher(destination).matches()) {
            world.sendPush(title, "", "home", null, null);
        } else if (POOL_PLAIN.matcher(destination).matches()) {
            world.sendPush(title, "", "pool", null, null);
        } else {
            throw new IllegalArgumentException("認不得的去處字串：「" + destination + "」");
        }
    }
}
