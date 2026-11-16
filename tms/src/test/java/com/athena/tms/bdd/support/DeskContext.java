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

    public void rememberAssignIdempotencyKey(String idempotencyKey) {
        this.lastAssignIdempotencyKey = idempotencyKey;
    }

    public String lastAssignIdempotencyKey() {
        if (lastAssignIdempotencyKey == null) {
            throw new IllegalStateException("本 scenario 尚未帶防重複鍵派過單");
        }
        return lastAssignIdempotencyKey;
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
}
