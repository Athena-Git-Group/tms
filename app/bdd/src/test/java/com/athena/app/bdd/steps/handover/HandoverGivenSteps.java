package com.athena.app.bdd.steps.handover;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.假設;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/** 房間交辦模組的 Given。 */
public class HandoverGivenSteps {

    private final AppiumWorld world;

    public HandoverGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料建立並凍結交班單。權威狀態落地：後端有該交班項目。
     * 時刻（{@code HH:mm}）在 feature 中為裸字（無引號），用 regex 形式的 step definition 擷取。
     *
     * <p>凍結時刻一律落在「今天」的那個時刻：交辦清單本身不挑日期，但任務頁要顯示
     * 的時間是從凍結時間格出來的，落在今天才和情境描述的同一個班次對得上。
     */
    @假設("^\"([^\"]+)\" (\\S+) 凍結的交班單中 \"([^\"]+)\" 的項目為 \"([^\"]+)\"$")
    public void frozenHandoverItemAt(String author, String time, String roomNo, String content) {
        BackendChannel.CreatedStaff staff = world.staff(author);
        OffsetDateTime frozenAt = ZonedDateTime
                .of(LocalDate.now(), LocalTime.parse(time), ZoneId.systemDefault())
                .toOffsetDateTime();
        world.backend().freezeHandoverItem(staff.dept(), frozenAt, roomNo, content, author);
    }

    /**
     * 怎麼做：透過後端測試資料，依後端「房間交辦」模組的同義 Given 建立查房退回並換人的任務。
     * 權威狀態落地：後端任務為 accepted、redo 1，負責人是接手人。
     *
     * <p>三步照後端同義 Given 的順序走：原負責人清完（任務帶到 done，完成事件的操作者
     * 就是他，退回資訊那句「原清房者 X 已下班」取的是這個人）→ 主管以正式的查房端點
     * 判定該項不合格（任務回到 accepted、重做次數 +1）→ 班末換人改掛給接手人。
     * 最後一步沒有端點可走，落地理由見 {@link BackendChannel#reassignAfterShiftEnd}。
     */
    @假設("後端的 {string} 是 {string} 清完後被判定 {string} 不合格、因原負責人下班改由 {string} 接手的單")
    public void backendRoomIsReassignedAfterInspectionFailure(
            String roomNo, String originalAssignee, String failedItem, String newAssignee) {
        BackendChannel backend = world.backend();
        BackendChannel.CreatedStaff original = world.staff(originalAssignee);
        BackendChannel.CreatedStaff successor = world.staff(newAssignee);

        UUID taskId = backend.createTask(
                roomNo, "退房清潔", "cleaning", "housekeeping", 35, "done", original, null, null);
        backend.failInspection(taskId, world.onlyManager(), failedItem);
        backend.reassignAfterShiftEnd(taskId, successor);
    }
}
