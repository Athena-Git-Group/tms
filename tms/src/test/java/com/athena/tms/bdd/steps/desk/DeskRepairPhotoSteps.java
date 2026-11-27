package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.PhotoReads;
import com.athena.tms.bdd.support.PhotoStorageProbe;
import com.athena.tms.service.JwtService;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public class DeskRepairPhotoSteps {

    private static final Set<String> PHOTO_FIELDS = Set.of("photos", "completionPhotos", "photoIds", "photoId");

    private final ScenarioWorld world;
    private final DeskContext deskContext;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final PhotoStorageProbe photoStorage;
    private final JwtService jwtService;

    public DeskRepairPhotoSteps(
            ScenarioWorld world,
            DeskContext deskContext,
            BddApiClient apiClient,
            DbProbe dbProbe,
            PhotoStorageProbe photoStorage,
            JwtService jwtService) {
        this.world = world;
        this.deskContext = deskContext;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.photoStorage = photoStorage;
        this.jwtService = jwtService;
    }

    @那麼("櫃檯詳情的報修照片有 {int} 張、完修照片有 {int} 張，{string} 每一張都讀得到")
    public void detailPhotosReadable(int reportCount, int completionCount, String staffName) {
        JsonNode detail = detailJson();
        UUID taskId = UUID.fromString(detail.path("id").asString());
        JsonNode repair = detail.path("repair");
        List<String> reportIds = uploadedIds("repair.photos", repair.path("photos"), reportCount);
        List<String> completionIds = uploadedIds("repair.completionPhotos", repair.path("completionPhotos"), completionCount);

        ScenarioWorld.ApiResponse last = world.lastResponse();
        try {
            JsonNode ticket = readTicketAsAssignee(taskId);
            check("readRepairTicket 的 photos id 與順序", reportIds, idsOf(ticket.path("photos")));
            check("readRepairTicket 的 completionPhotos id 與順序", completionIds, idsOf(ticket.path("completionPhotos")));

            String jwt = world.jwt(staffName);
            for (String photoId : concat(reportIds, completionIds)) {
                PhotoReads.assertReadableAsStored(apiClient, photoStorage, dbProbe, jwt, photoId);
            }
        } finally {
            world.setLastResponse(last);
        }

        check("綁在這張單、purpose=report 的 repair_photos 筆數", (long) reportCount, dbProbe.count(
                "SELECT count(*) FROM repair_photos WHERE ticket_task_id = ? AND purpose = 'report'", taskId));
        check("綁在這張單、purpose=completion 且屬於這一輪的 repair_photos 筆數", (long) completionCount, dbProbe.count(
                """
                SELECT count(*)
                  FROM repair_photos p
                  JOIN repair_tickets rt ON rt.tenant_id = p.tenant_id AND rt.task_id = p.ticket_task_id
                 WHERE p.ticket_task_id = ? AND p.purpose = 'completion'
                   AND (rt.last_verify_failed_at IS NULL
                        OR COALESCE(p.uploaded_at, p.created_at) > rt.last_verify_failed_at)
                """,
                taskId));
    }

    @那麼("櫃檯詳情沒有工務單的照片")
    public void detailHasNoRepairPhotos() {
        JsonNode detail = detailJson();
        if ("repair".equals(detail.path("type").asString(null))) {
            throw new AssertionError("DeskTaskDetail.type 不應是 repair");
        }
        JsonNode repair = detail.get("repair");
        if (repair != null && !repair.isNull()) {
            throw new AssertionError("DeskTaskDetail.repair 應為 null，實際 " + repair);
        }
        List<String> found = new ArrayList<>();
        collectPhotoFields("", detail, found);
        if (!found.isEmpty()) {
            throw new AssertionError("櫃檯詳情不應帶任何照片編號，卻有欄位 " + found);
        }
    }

    private JsonNode detailJson() {
        ScenarioWorld.ApiResponse response = deskContext.lastDetailResponse();
        if (response.status() != 200 || response.json() == null) {
            throw new AssertionError("readDeskTask 預期 200，實際 " + response.status() + "，body=" + response.body());
        }
        return response.json();
    }

    private JsonNode readTicketAsAssignee(UUID taskId) {
        Map<String, Object> task = dbProbe
                .queryOne("SELECT tenant_id, assignee_id FROM tasks WHERE id = ?", taskId)
                .orElseThrow(() -> new AssertionError("tasks 查無 " + taskId));
        if (task.get("assignee_id") == null) {
            throw new IllegalStateException("這張工務單沒有處理人，無法以處理人身分讀單");
        }
        long tenantId = ((Number) task.get("tenant_id")).longValue();
        UUID assigneeId = UUID.fromString(String.valueOf(task.get("assignee_id")));
        Map<String, Object> staff = dbProbe
                .queryOne("SELECT role, dept FROM staff_accounts WHERE tenant_id = ? AND id = ?", tenantId, assigneeId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 查無處理人 " + assigneeId));
        String jwt = jwtService.issue(tenantId, assigneeId, (String) staff.get("role"), (String) staff.get("dept")).token();
        ScenarioWorld.ApiResponse response = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(jwt));
        if (response.status() != 200 || response.json() == null) {
            throw new AssertionError("處理人讀工務單預期 200，實際 " + response.status() + "，body=" + response.body());
        }
        return response.json();
    }

    private static List<String> uploadedIds(String field, JsonNode photos, int expectedCount) {
        if (!photos.isArray()) {
            throw new AssertionError("DeskTaskDetail." + field + " 應是陣列，實際 " + photos);
        }
        check("DeskTaskDetail." + field + " 張數", expectedCount, photos.size());
        for (JsonNode photo : photos) {
            check("DeskTaskDetail." + field + " 照片 " + photo.path("id").asString(null) + " 的 status",
                    "uploaded", photo.path("status").asString(null));
        }
        return idsOf(photos);
    }

    private static List<String> idsOf(JsonNode photos) {
        List<String> ids = new ArrayList<>();
        for (JsonNode photo : photos) {
            ids.add(photo.path("id").asString(null));
        }
        return ids;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static void collectPhotoFields(String path, JsonNode node, List<String> found) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String fieldPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                if (PHOTO_FIELDS.contains(field.getKey()) && !field.getValue().isNull()) {
                    found.add(fieldPath);
                }
                collectPhotoFields(fieldPath, field.getValue(), found);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectPhotoFields(path + "[" + i + "]", node.get(i), found);
            }
        }
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }
}
