package com.athena.web.bdd.steps.realtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RealtimeScenario {

    private Instant signalSentAt;

    private Integer boardRequestsFrom;

    private final List<Instant> boardRequestTimes = new ArrayList<>();

    private Boolean alertSeenDuringReconnect;

    public void markAlertSeenDuringReconnect(boolean seen) {
        alertSeenDuringReconnect = seen;
    }

    public Optional<Boolean> alertSeenDuringReconnect() {
        return Optional.ofNullable(alertSeenDuringReconnect);
    }

    public void markSignalSent(Instant at) {
        signalSentAt = at;
    }

    public Optional<Instant> signalSentAt() {
        return Optional.ofNullable(signalSentAt);
    }

    public void markBoardRequestsFrom(int requestCount) {
        boardRequestsFrom = requestCount;
    }

    public Optional<Integer> boardRequestsFrom() {
        return Optional.ofNullable(boardRequestsFrom);
    }

    private Boolean resyncSeenAtWhenStart;

    void markResyncSeenAtWhenStart(boolean seen) {
        resyncSeenAtWhenStart = seen;
    }

    Optional<Boolean> resyncSeenAtWhenStart() {
        return Optional.ofNullable(resyncSeenAtWhenStart);
    }

    void recordBoardRequest(Instant at) {
        boardRequestTimes.add(at);
    }

    public List<Instant> boardRequestTimes() {
        return List.copyOf(boardRequestTimes);
    }
}
