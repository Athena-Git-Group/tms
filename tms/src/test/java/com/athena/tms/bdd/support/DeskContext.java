package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;

public class DeskContext {

    private String lastAssignIdempotencyKey;
    private ScenarioWorld.ApiResponse lastBoardResponse;
    private ScenarioWorld.ApiResponse lastDetailResponse;
    private String lastBoardReader;
    private ScenarioWorld.ApiResponse lastAssignResponse;
    private ScenarioWorld.ApiResponse firstAssignResponseBeforeResend;
    private ScenarioWorld.ApiResponse resentAssignResponse;
    private String lastSentAnswer;
    private java.util.Map<String, Integer> completedCountsBeforeAnswer;

    public void rememberAssignIdempotencyKey(String idempotencyKey) {
        this.lastAssignIdempotencyKey = idempotencyKey;
    }

    public String lastAssignIdempotencyKey() {
        if (lastAssignIdempotencyKey == null) {
            throw new IllegalStateException("本 scenario 尚未帶防重複鍵派過單");
        }
        return lastAssignIdempotencyKey;
    }

    public void rememberAssignResponse(ScenarioWorld.ApiResponse response) {
        this.lastAssignResponse = response;
    }

    public ScenarioWorld.ApiResponse lastAssignResponse() {
        if (lastAssignResponse == null) {
            throw new IllegalStateException("本 scenario 尚未帶防重複鍵派過單");
        }
        return lastAssignResponse;
    }

    public void rememberAssignResend(ScenarioWorld.ApiResponse first, ScenarioWorld.ApiResponse resent) {
        this.firstAssignResponseBeforeResend = first;
        this.resentAssignResponse = resent;
    }

    public ScenarioWorld.ApiResponse firstAssignResponseBeforeResend() {
        if (firstAssignResponseBeforeResend == null) {
            throw new IllegalStateException("本 scenario 尚未用同一把鍵重送派單");
        }
        return firstAssignResponseBeforeResend;
    }

    public ScenarioWorld.ApiResponse resentAssignResponse() {
        if (resentAssignResponse == null) {
            throw new IllegalStateException("本 scenario 尚未用同一把鍵重送派單");
        }
        return resentAssignResponse;
    }

    public void rememberSentAnswer(String answer) {
        this.lastSentAnswer = answer;
    }

    public String lastSentAnswer() {
        if (lastSentAnswer == null) {
            throw new IllegalStateException("本 scenario 尚未送出由測試產生的回覆");
        }
        return lastSentAnswer;
    }

    public void rememberCompletedCountsBeforeAnswer(java.util.Map<String, Integer> countsByStaffId) {
        this.completedCountsBeforeAnswer = java.util.Map.copyOf(countsByStaffId);
    }

    public java.util.Map<String, Integer> completedCountsBeforeAnswer() {
        if (completedCountsBeforeAnswer == null) {
            throw new IllegalStateException("本 scenario 尚未在當場回答前記下同仁的今日完成數");
        }
        return completedCountsBeforeAnswer;
    }

    public void rememberBoardResponse(ScenarioWorld.ApiResponse response) {
        this.lastBoardResponse = response;
    }

    public ScenarioWorld.ApiResponse lastBoardResponse() {
        if (lastBoardResponse == null) {
            throw new IllegalStateException("本 scenario 尚未讀取櫃檯看板");
        }
        return lastBoardResponse;
    }

    public void rememberDetailResponse(ScenarioWorld.ApiResponse response) {
        this.lastDetailResponse = response;
    }

    public ScenarioWorld.ApiResponse lastDetailResponse() {
        if (lastDetailResponse == null) {
            throw new IllegalStateException("本 scenario 尚未讀取櫃檯詳情");
        }
        return lastDetailResponse;
    }

    public void rememberBoardReader(String staffName) {
        this.lastBoardReader = staffName;
    }

    public String lastBoardReader() {
        if (lastBoardReader == null) {
            throw new IllegalStateException("本 scenario 尚未讀取櫃檯看板");
        }
        return lastBoardReader;
    }
}
