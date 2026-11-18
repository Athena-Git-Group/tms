package com.athena.app.bdd.steps.manager;

public class ManagerContext {

    private String openedQueue;

    public void openQueue(String queueKey) {
        this.openedQueue = queueKey;
    }

    public String openedQueue() {
        return openedQueue;
    }

    private final java.util.Map<String, String> judgements = new java.util.LinkedHashMap<>();

    public void recordJudgement(String itemLabel, String verdict) {
        judgements.put(itemLabel, verdict);
    }

    public void clearJudgements() {
        judgements.clear();
    }

    public java.util.Map<String, String> judgements() {
        return java.util.Collections.unmodifiableMap(judgements);
    }

    private final java.util.Map<String, Integer> queueCountsBefore = new java.util.LinkedHashMap<>();

    public void recordQueueCountBefore(String queueKey, int count) {
        queueCountsBefore.put(queueKey, count);
    }

    public Integer queueCountBefore(String queueKey) {
        return queueCountsBefore.get(queueKey);
    }

    private String inspectingRoomNo;

    public void openInspection(String roomNo) {
        this.inspectingRoomNo = roomNo;
    }

    public String inspectingRoomNo() {
        return inspectingRoomNo;
    }
}
