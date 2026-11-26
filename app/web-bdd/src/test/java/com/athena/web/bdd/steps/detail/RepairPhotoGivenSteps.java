package com.athena.web.bdd.steps.detail;

import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.steps.common.CommonGivenSteps;
import com.athena.web.bdd.support.PhotoRequests;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.zh_tw.假設;
import java.util.List;
import java.util.UUID;

public class RepairPhotoGivenSteps {

    private final WebWorld world;

    private final ShellFrame shell;

    private final DetailPanel detail;

    private final CommonGivenSteps common;

    /** photoRequests 只為了在登入前就開始記錄讀照片的請求；有照片的場景都先跑這裡的照片句。 */
    public RepairPhotoGivenSteps(WebWorld world, PhotoRequests photoRequests, CommonGivenSteps common) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.detail = new DetailPanel(world);
        this.common = common;
    }

    @假設("{string} 的工務單有 {int} 張報修照片、{int} 張完修照片")
    public void ticketHasPhotos(String roomNo, int reportCount, int completionCount) {
        UUID ticketId = world.tasks().idOf(roomNo);
        world.backend().replaceRepairPhotos(ticketId, reportCount, completionCount);
        JsonNode repair = world.backend().getJson("/desk/tasks/" + ticketId, RepairPhotos.reader(world)).path("repair");
        int reported = repair.path("photos").size();
        int completed = repair.path("completionPhotos").size();
        if (reported != reportCount || completed != completionCount) {
            throw new IllegalStateException("櫃檯詳情讀回 " + roomNo + " 的報修照片 " + reported + " 張、完修照片 "
                    + completed + " 張，準備的是 " + reportCount + " 張、" + completionCount + " 張：" + repair);
        }
    }

    @假設("{string} 的工務單第 {int} 張報修照片是舊版的假照片")
    public void reportPhotoIsLegacy(String roomNo, int nth) {
        UUID ticketId = world.tasks().idOf(roomNo);
        List<UUID> ids = new java.util.ArrayList<>();
        world.backend().getJson("/desk/tasks/" + ticketId, RepairPhotos.reader(world))
                .path("repair").path("photos")
                .forEach(photo -> ids.add(UUID.fromString(photo.path("id").asText())));
        if (nth < 1 || nth > ids.size()) {
            throw new IllegalArgumentException(roomNo + " 只有 " + ids.size() + " 張報修照片，沒有第 " + nth + " 張");
        }
        UUID photoId = ids.get(nth - 1);
        world.backend().markLegacyPhoto(photoId);
        try {
            world.backend().photoContent(photoId, RepairPhotos.reader(world));
        } catch (IllegalStateException expected) {
            if (expected.getMessage().contains("HTTP 404")) {
                return;
            }
            throw expected;
        }
        throw new IllegalStateException("把照片 " + photoId + " 改成舊版的假照片後，後端照樣讀得到內容");
    }

    @假設("{string} 開著 {string} 詳情面板 {string} 第 {int} 張照片的大圖")
    public void viewerOpen(String staffName, String roomNo, String rowTitle, int nth) {
        common.detailPanelOpen(staffName, roomNo);
        RepairPhotos.openViewer(world, detail, shell, rowTitle, nth);
    }
}
