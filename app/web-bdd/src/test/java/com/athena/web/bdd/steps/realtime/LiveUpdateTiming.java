package com.athena.web.bdd.steps.realtime;

import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.BeforeStep;
import java.time.Duration;
import java.time.Instant;

public class LiveUpdateTiming {

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

    public void markSent() {
        origin = new Moment(Instant.now(), world.network().sentRequestCount());
    }

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

    public double remainingMillis(int seconds) {
        Instant deadline = origin().at().plusSeconds(seconds);
        return Duration.between(Instant.now(), deadline).toNanos() / 1_000_000.0;
    }
}
