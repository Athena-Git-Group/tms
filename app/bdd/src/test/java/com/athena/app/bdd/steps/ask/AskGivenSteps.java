package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/** 查一下模組的 Given。 */
public class AskGivenSteps {

    private final AppiumWorld world;

    public AskGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料寫入該房的 {@code room_memory}。記錄內容為 feature 中的裸字
     * （無引號，含日期、標題與原文的業務描述），用 regex 形式的 step definition 擷取。
     */
    @假設("^後端 \"([^\"]+)\" 的房間記錄有 (.+)$")
    public void backendRoomMemoryHas(String roomNo, String record) {
        throw new PendingException();
    }

    /** 怎麼做：透過後端測試資料，把該 SOP 項標為跳過。 */
    @假設("{string} 在 {string} 剛跳過 SOP 項 {string}")
    public void justSkippedSop(String staffName, String task, String sopItem) {
        throw new PendingException();
    }

    /**
     * 怎麼做：進入任務頁，點查一下，等推薦問題載入完成。
     * accessibility id：{@code ask.suggestion.{index}}。
     */
    @假設("{string} 已在 {string} 的任務頁打開查一下")
    public void alreadyOpenedAsk(String staffName, String task) {
        throw new PendingException();
    }
}
