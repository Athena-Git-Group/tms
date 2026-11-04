package com.athena.app.bdd.steps.handover;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/** 房間交辦模組的 Given。 */
public class HandoverGivenSteps {

    private final AppiumWorld world;

    public HandoverGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料建立並凍結交班單。權威狀態落地：後端有該交班項目。
     * 時刻（{@code HH:mm}）在 feature 中為裸字（無引號），用 regex 形式的 step definition 擷取。
     */
    @假設("^\"([^\"]+)\" (\\S+) 凍結的交班單中 \"([^\"]+)\" 的項目為 \"([^\"]+)\"$")
    public void frozenHandoverItemAt(String author, String time, String roomNo, String content) {
        throw new PendingException();
    }

    /**
     * 怎麼做：透過後端測試資料，依後端「房間交辦」模組的同義 Given 建立查房退回並換人的任務。
     * 權威狀態落地：後端任務為 accepted、redo 1，負責人是接手人。
     */
    @假設("後端的 {string} 是 {string} 清完後被判定 {string} 不合格、因原負責人下班改由 {string} 接手的單")
    public void backendRoomIsReassignedAfterInspectionFailure(
            String roomNo, String originalAssignee, String failedItem, String newAssignee) {
        throw new PendingException();
    }
}
