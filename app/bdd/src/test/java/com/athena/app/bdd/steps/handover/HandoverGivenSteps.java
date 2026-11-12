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

public class HandoverGivenSteps {

    private final AppiumWorld world;

    public HandoverGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    @假設("^\"([^\"]+)\" (\\S+) 凍結的交班單中 \"([^\"]+)\" 的項目為 \"([^\"]+)\"$")
    public void frozenHandoverItemAt(String author, String time, String roomNo, String content) {
        BackendChannel.CreatedStaff staff = world.staff(author);
        OffsetDateTime frozenAt = ZonedDateTime
                .of(LocalDate.now(), LocalTime.parse(time), ZoneId.systemDefault())
                .toOffsetDateTime();
        world.backend().freezeHandoverItem(staff.dept(), frozenAt, roomNo, content, author);
    }

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
