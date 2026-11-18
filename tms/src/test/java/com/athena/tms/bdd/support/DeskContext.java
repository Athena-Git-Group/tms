package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;

/**
 * 櫃檯看板與派單的句型在同一個案例裡要互相接手的資料。
 *
 * <p>{@link ScenarioWorld#lastResponse()} 只留最後一次呼叫，案例裡只要再打一次別的端點就被蓋掉；
 * 但「用同一把鍵重送」要拿回前面派單那一把鍵，看板與詳情的 Then 也常隔著別的呼叫才檢查，
 * 所以這三樣另外記在這裡。每個案例一份，由 {@link DeskApi} 在呼叫當下寫入，句型只需要讀。
 */
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

    /** 最近一次帶防重複鍵派單的回應；用同一把鍵重送之前讀它，才拿得到第一次的回應。 */
    public ScenarioWorld.ApiResponse lastAssignResponse() {
        if (lastAssignResponse == null) {
            throw new IllegalStateException("本 scenario 尚未帶防重複鍵派過單");
        }
        return lastAssignResponse;
    }

    /** 用同一把鍵重送派單時，把第一次與重送的回應一起記下，給 Then 比對兩次是否相同。 */
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

    /** 由測試產生的回覆字串，Then 要比對後端存下的內容是否等於送出的那一段。 */
    public void rememberSentAnswer(String answer) {
        this.lastSentAnswer = answer;
    }

    public String lastSentAnswer() {
        if (lastSentAnswer == null) {
            throw new IllegalStateException("本 scenario 尚未送出由測試產生的回覆");
        }
        return lastSentAnswer;
    }

    /**
     * 當場回答送出前，房務與工務同仁各自的今日完成數，以員工 id 對應。
     * Then 拿回答後的完成數跟它比，才看得出這張單有沒有被算進某人的完成。
     */
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

    /** 看板上「本人今日張數」算的是讀看板的那一位，Then 要知道是誰才能對資料庫數他的事件。 */
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
