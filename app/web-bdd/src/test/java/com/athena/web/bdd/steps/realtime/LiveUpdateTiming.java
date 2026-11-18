package com.athena.web.bdd.steps.realtime;

import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.BeforeStep;
import java.time.Duration;
import java.time.Instant;

/**
 * 給「{秒數} 秒內畫面跟上」這類結果句決定從哪一刻起算，用真實時間量。
 *
 * <p>即時更新模組自己的「當」步驟會呼叫 {@link #markSent()}，或把送出的時刻記在 {@link RealtimeScenario}，有記就從那一刻算。
 * 介面根的「當」步驟（例如手機上認領）不會記，這時改從結果句前一個步驟開始的時刻算；
 * 那一刻一定早於真正送出的時刻，所以只會量得更嚴，不會放寬。
 * 同一個場景第一次取用之後就固定下來，接在後面的第二句「{秒數} 秒內…」從同一刻算起。
 *
 * <p>每個步驟開始時只記時間與請求數、不碰畫面，所以這個掛勾套在其他模組的場景上也沒有副作用。
 */
public class LiveUpdateTiming {

    /**
     * 某一刻：真實時間，以及到那一刻為止這個場景的分頁送出過幾個請求。
     *
     * @param at 真實時間
     * @param requestCount 到那一刻為止送出過的請求數，之後的請求用 {@code Network.sentRequestsSince} 取
     */
    public record Moment(Instant at, int requestCount) {
    }

    private final WebWorld world;

    private final RealtimeScenario scenario;

    private Moment currentStepStart;

    private Moment previousStepStart;

    private Moment origin;

    public LiveUpdateTiming(WebWorld world, RealtimeScenario scenario) {
        this.world = world;
        this.scenario = scenario;
    }

    @BeforeStep
    public void rememberStepStart() {
        previousStepStart = currentStepStart;
        currentStepStart = new Moment(Instant.now(), world.network().sentRequestCount());
    }

    /** 「當」步驟在送出變化之前呼叫，之後的結果句就從這一刻起算。 */
    public void markSent() {
        origin = new Moment(Instant.now(), world.network().sentRequestCount());
    }

    /** 這個場景的起算點；第一次取用時決定，之後不變。 */
    public Moment origin() {
        if (origin == null) {
            if (previousStepStart == null) {
                throw new IllegalStateException("結果句前面沒有任何步驟，不知道從哪一刻起算");
            }
            Instant at = scenario.signalSentAt().orElse(previousStepStart.at());
            origin = new Moment(at, previousStepStart.requestCount());
        }
        return origin;
    }

    /** 從起算點到 {@code seconds} 秒為止還剩多少毫秒；已經過了就是 0 以下。 */
    public double remainingMillis(int seconds) {
        Instant deadline = origin().at().plusSeconds(seconds);
        return Duration.between(Instant.now(), deadline).toNanos() / 1_000_000.0;
    }
}
