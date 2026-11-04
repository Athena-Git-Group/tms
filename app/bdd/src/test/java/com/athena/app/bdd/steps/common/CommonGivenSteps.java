package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/**
 * 介面根共通句的 Given（dsl.md Given 句型區）。只建立/確認權威狀態，不含任何斷言——
 * 「必查」清單在 {@link CommonThenSteps}。
 */
public class CommonGivenSteps {

    private final AppiumWorld world;

    public CommonGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料建立員工與班次。權威狀態落地：後端 staff 資料已存在；
     * 待開通與停用的員工不出現在挑人名單、不能搶單、不收推播。回寫：登入帳號。
     */
    @假設("飯店有以下員工：")
    public void hotelHasStaff(DataTable table) {
        throw new PendingException();
    }

    /**
     * 怎麼做：透過後端測試資料建立任務。以該員工身分登入 App，停在首頁。
     * 權威狀態落地：後端任務真相已存在。
     */
    @假設("{string} 名下有以下任務：")
    public void staffHasTasks(String staffName, DataTable table) {
        throw new PendingException();
    }

    /** 怎麼做：把裝置時間與後端 {@code Clock} 設為今日該時刻。 */
    @假設("現在時間是 {string}")
    public void nowIs(String hhmm) {
        throw new PendingException();
    }

    /** 怎麼做：把裝置切到無網路。 */
    @假設("手機目前離線")
    public void phoneOffline() {
        throw new PendingException();
    }
}
