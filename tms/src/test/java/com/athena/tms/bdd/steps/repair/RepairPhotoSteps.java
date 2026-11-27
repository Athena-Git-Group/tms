package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.PhotoFixtures;
import com.athena.tms.bdd.support.PhotoReads;
import com.athena.tms.bdd.support.PhotoStorageProbe;
import com.athena.tms.bdd.support.RepairCountGuard;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

public class RepairPhotoSteps {

    private static final Map<String, String> PURPOSE = Map.of("報修", "report", "完修", "completion");
    private static final String ATTACHED_CACHE_CONTROL = "private, max-age=86400, immutable";
    private static final String UNATTACHED_CACHE_CONTROL = "private, no-cache";
    private static final String OTHER_HOTEL_NAME = "別家飯店";
    private static final String OTHER_HOTEL_CODE = "other-hotel";

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final JdbcClient jdbcClient;
    private final MutableClock clock;
    private final TenantFixtures tenantFixtures;
    private final StaffFixtures staffFixtures;
    private final PhotoStorageProbe photoStorage;
    private final LastOperationGuard lastOperationGuard;
    private final RepairCountGuard repairCountGuard;
    private final JwtService jwtService;

    private int stepIndex;
    private int uploadStepIndex = -1;
    private long photoCountBefore = -1;
    private ViewSnapshot viewSnapshot;

    public RepairPhotoSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            JdbcClient jdbcClient,
            MutableClock clock,
            TenantFixtures tenantFixtures,
            StaffFixtures staffFixtures,
            PhotoStorageProbe photoStorage,
            LastOperationGuard lastOperationGuard,
            RepairCountGuard repairCountGuard,
            JwtService jwtService) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.tenantFixtures = tenantFixtures;
        this.staffFixtures = staffFixtures;
        this.photoStorage = photoStorage;
        this.lastOperationGuard = lastOperationGuard;
        this.repairCountGuard = repairCountGuard;
        this.jwtService = jwtService;
    }

    @BeforeStep
    public void countStep() {
        stepIndex++;
    }

    @假設("{string} 已上傳照片 {string}，用途為 {string}")
    public void staffUploadedPhoto(String staffName, String label, String purposeChinese) {
        String jwt = world.jwt(staffName);
        String photoId = createPhotoArranged(jwt, purposeChinese, label);
        PhotoFixtures.PhotoContent content = PhotoFixtures.of(label, "一張 JPEG 照片");
        uploadArranged(jwt, photoId, content, label);
        world.rememberPhoto(label, new ScenarioWorld.PhotoLabel(photoId, Optional.of(content.bytes()), uploadedAt(photoId)));
    }

    @假設("{string} 已建立照片 {string}，用途為 {string}，還沒上傳內容")
    public void staffCreatedPhotoWithoutContent(String staffName, String label, String purposeChinese) {
        String photoId = createPhotoArranged(world.jwt(staffName), purposeChinese, label);
        world.rememberPhoto(label, new ScenarioWorld.PhotoLabel(photoId, Optional.empty(), null));
    }

    @假設("照片 {string} 已附在 {string}")
    public void photoAttachedToTicket(String label, String ticketNo) {
        UUID photoId = UUID.fromString(world.photo(label).photoId());
        UUID ticketTaskId = RepairTicketRefs.taskId(world, ticketNo);
        int updated = jdbcClient
                .sql("UPDATE repair_photos SET ticket_task_id = ? WHERE tenant_id = ? AND id = ?")
                .params(ticketTaskId, tenantFixtures.testTenantId(), photoId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("前提「照片 " + label + " 已附在 " + ticketNo + "」沒鋪起來：本飯店查無這張照片");
        }
    }

    @假設("別家飯店的員工已上傳照片 {string}，用途為 {string}")
    public void otherHotelStaffUploadedPhoto(String label, String purposeChinese) {
        long otherTenantId = tenantFixtures.createTenant(OTHER_HOTEL_NAME, OTHER_HOTEL_CODE);
        if (otherTenantId == tenantFixtures.testTenantId()) {
            throw new IllegalStateException("別家飯店與本案例的飯店是同一個 tenant_id：" + otherTenantId);
        }
        String jwt = staffFixtures.createEngineerInTenant(otherTenantId, "別家的工務").jwt();
        String photoId = createPhotoArranged(jwt, purposeChinese, label);
        PhotoFixtures.PhotoContent content = PhotoFixtures.of(label, "一張 JPEG 照片");
        uploadArranged(jwt, photoId, content, label);
        world.rememberPhoto(label, new ScenarioWorld.PhotoLabel(photoId, Optional.of(content.bytes()), uploadedAt(photoId)));
    }

    @假設("照片 {string} 不存在")
    public void photoDoesNotExist(String label) {
        UUID photoId = UUID.randomUUID();
        if (photoRow(photoId.toString()) != null) {
            throw new IllegalStateException("隨機產生的照片 id 已存在：" + photoId);
        }
        world.rememberPhoto(label, new ScenarioWorld.PhotoLabel(photoId.toString(), Optional.empty(), null));
    }

    @假設("照片 {string} 是舊版的假照片")
    public void photoIsLegacyFake(String label) {
        ScenarioWorld.PhotoLabel photo = world.photo(label);
        long tenantId = tenantFixtures.testTenantId();
        photoStorage.delete(PhotoStorageProbe.key(tenantId, photo.photoId()));
        int updated = jdbcClient
                .sql("UPDATE repair_photos SET storage_ref = ? WHERE tenant_id = ? AND id = ?")
                .params("upload:" + photo.photoId(), tenantId, UUID.fromString(photo.photoId()))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("前提「照片 " + label + " 是舊版的假照片」沒鋪起來：本飯店查無這張照片");
        }
        world.rememberPhoto(label, new ScenarioWorld.PhotoLabel(photo.photoId(), Optional.empty(), photo.uploadedAt()));
    }

    @當("{string} 以 {string} 上傳照片 {string} 的內容")
    public void staffUploadsPhotoContent(String staffName, String contentName, String label) {
        ScenarioWorld.PhotoLabel photo = world.photo(label);
        PhotoFixtures.PhotoContent content = PhotoFixtures.of(label, contentName);
        lastOperationGuard.captureWithoutTask();
        photoCountBefore = photoCount();
        uploadStepIndex = stepIndex;

        ScenarioWorld.ApiResponse response = apiClient.exchangeBytes(
                HttpMethod.PUT, contentPath(photo.photoId()), BddApiClient.Auth.staff(world.jwt(staffName)),
                content.bytes(), content.contentType());
        if (isSuccess(response)) {
            OffsetDateTime uploadedAt = photo.uploadedAt() != null ? photo.uploadedAt() : uploadedAt(photo.photoId());
            world.rememberPhoto(label, new ScenarioWorld.PhotoLabel(photo.photoId(), Optional.of(content.bytes()), uploadedAt));
        }
    }

    @當("{string} 查看照片 {string}")
    public void staffViewsPhoto(String staffName, String label) {
        String photoId = world.photo(label).photoId();
        lastOperationGuard.captureWithoutTask();
        viewSnapshot = viewSnapshot();
        apiClient.exchangeBytes(HttpMethod.GET, contentPath(photoId), BddApiClient.Auth.staff(world.jwt(staffName)),
                null, null);
    }

    @當("{string} 建立照片，用途為 {string}、拍攝時間為 {string}")
    public void staffCreatesPhoto(String staffName, String purposeText, String takenAtText) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!"不帶".equals(purposeText)) {
            body.put("purpose", PURPOSE.getOrDefault(purposeText, purposeText));
        }
        body.put("takenAt", "現在".equals(takenAtText) ? OffsetDateTime.now(clock).toString() : takenAtText);
        lastOperationGuard.captureWithoutTask();
        photoCountBefore = photoCount();
        apiClient.post("/repair-photos", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("{string} 附照片 {string} 以已修好完成 {string}")
    public void engineerCompletesWithPhoto(String staffName, String label, String ticketNo) {
        completeWithPhotos(staffName, "fixed", ticketNo, List.of(label));
    }

    @當("{string} 附照片 {string}、{string} 以已修好完成 {string}")
    public void engineerCompletesWithTwoPhotos(String staffName, String label, String otherLabel, String ticketNo) {
        completeWithPhotos(staffName, "fixed", ticketNo, List.of(label, otherLabel));
    }

    @當("{string} 附照片 {string} 以完修結果 {string} 完成 {string}")
    public void engineerCompletesWithPhotoAndResolution(
            String staffName, String label, String resolutionChinese, String ticketNo) {
        completeWithPhotos(staffName, Terms.resolutionType(resolutionChinese), ticketNo, List.of(label));
    }

    private void completeWithPhotos(String staffName, String resolutionType, String ticketNo, List<String> labels) {
        UUID ticketTaskId = RepairTicketRefs.taskId(world, ticketNo);
        List<String> photoIds = labels.stream().map(label -> world.photo(label).photoId()).toList();
        lastOperationGuard.captureFor(ticketTaskId, photoIds.stream().map(UUID::fromString).toList());
        Map<String, Object> body = Map.of("resolutionType", resolutionType, "photoIds", photoIds);
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("{string} 補照片 {string} 到 {string}")
    public void staffAppendsPhoto(String staffName, String label, String ticketNo) {
        UUID ticketTaskId = RepairTicketRefs.taskId(world, ticketNo);
        lastOperationGuard.captureFor(ticketTaskId, List.of(UUID.fromString(world.photo(label).photoId())));
        Map<String, Object> body = Map.of("photoIds", List.of(world.photo(label).photoId()));
        apiClient.post("/repair-tickets/" + ticketTaskId + "/photos", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("{string} 從 {string} 附照片 {string} 開立水電工務單")
    public void staffOpensPlumbingTicketWithPhoto(String staffName, String taskKey, String label) {
        UUID sourceTaskId = UUID.fromString(world.taskId(taskKey));
        lastOperationGuard.captureFor(sourceTaskId, List.of(UUID.fromString(world.photo(label).photoId())));
        repairCountGuard.capture();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceTaskId", sourceTaskId.toString());
        body.put("category", Terms.repairCategory("水電"));
        body.put("photoIds", List.of(world.photo(label).photoId()));
        body.put("interruptSourceTask", false);
        ScenarioWorld.ApiResponse response =
                apiClient.post("/repair-tickets", BddApiClient.Auth.staff(world.jwt(staffName)), body);
        if (response.status() == 201 && response.json() != null) {
            String ticketNo = response.json().path("ticketNo").asText(null);
            String taskId = response.json().path("taskId").asText(null);
            if (ticketNo != null && taskId != null) {
                world.rememberRepairTicket(ticketNo, UUID.fromString(taskId).toString());
            }
        }
    }

    @那麼("照片 {string} 已上傳，存的是 {string}")
    public void photoUploadedWithContent(String label, String contentName) {
        String photoId = world.photo(label).photoId();
        if (uploadStepIndex == stepIndex - 1) {
            ScenarioWorld.ApiResponse upload = world.lastResponse();
            if (upload.status() != 200) {
                throw new AssertionError("上傳照片 " + label + " 預期 200，實際 " + upload.status() + "，body=" + upload.body());
            }
            String status = upload.json() == null ? null : upload.json().path("status").asText(null);
            if (!"uploaded".equals(status)) {
                throw new AssertionError("上傳回應的 RepairPhoto.status 預期 uploaded，實際 " + status + "，body=" + upload.body());
            }
        }

        Map<String, Object> row = requirePhotoRow(photoId);
        long tenantId = ((Number) row.get("tenant_id")).longValue();
        String expectedKey = PhotoStorageProbe.key(tenantId, photoId);
        check("照片 " + label + " 的 repair_photos.status", "uploaded", row.get("status"));
        check("照片 " + label + " 的 repair_photos.storage_ref", expectedKey, row.get("storage_ref"));
        if (row.get("uploaded_at") == null) {
            throw new AssertionError("照片 " + label + " 的 repair_photos.uploaded_at 應有值");
        }

        byte[] expected = PhotoFixtures.of(label, contentName).bytes();
        PhotoStorageProbe.StoredObject stored = photoStorage.read(expectedKey)
                .orElseThrow(() -> new AssertionError("照片儲存裡沒有 " + expectedKey));
        if (!Arrays.equals(expected, stored.bytes())) {
            throw new AssertionError("照片儲存裡 " + expectedKey + " 的內容不是「" + contentName + "」（"
                    + stored.bytes().length + " vs " + expected.length + " 位元組）");
        }
        check("照片儲存裡 " + expectedKey + " 的 Content-Type", PhotoFixtures.JPEG, stored.contentType());

        String creatorJwt = jwtOf(tenantId, UUID.fromString(String.valueOf(row.get("uploaded_by"))));
        ScenarioWorld.ApiResponse last = world.lastResponse();
        try {
            ScenarioWorld.ApiResponse reread = apiClient.exchangeBytes(
                    HttpMethod.GET, contentPath(photoId), BddApiClient.Auth.staff(creatorJwt), null, null);
            if (reread.status() != 200) {
                throw new AssertionError("建立照片的員工再讀照片 " + label + " 預期 200，實際 " + reread.status()
                        + "，body=" + reread.body());
            }
            if (!Arrays.equals(expected, reread.bytes())) {
                throw new AssertionError("建立照片的員工再讀照片 " + label + " 拿到的位元組不是「" + contentName + "」（"
                        + PhotoReads.lengthOf(reread.bytes()) + " vs " + expected.length + " 位元組）");
            }
        } finally {
            world.setLastResponse(last);
        }
    }

    @那麼("照片 {string} 仍未上傳")
    public void photoStillPending(String label) {
        String photoId = world.photo(label).photoId();
        Map<String, Object> row = requirePhotoRow(photoId);
        check("照片 " + label + " 的 repair_photos.status", "pending", row.get("status"));
        check("照片 " + label + " 的 repair_photos.storage_ref", null, row.get("storage_ref"));
        check("照片 " + label + " 的 repair_photos.uploaded_at", null, row.get("uploaded_at"));
        String key = PhotoStorageProbe.key(((Number) row.get("tenant_id")).longValue(), photoId);
        if (photoStorage.read(key).isPresent()) {
            throw new AssertionError("照片 " + label + " 仍未上傳，照片儲存裡卻有 " + key);
        }
    }

    @那麼("看到的照片是 {string} 存的內容")
    public void viewedPhotoIsStoredContent(String label) {
        ScenarioWorld.PhotoLabel photo = world.photo(label);
        ScenarioWorld.ApiResponse response = world.lastResponse();
        if (response.status() != 200) {
            throw new AssertionError("查看照片 " + label + " 預期 200，實際 " + response.status() + "，body=" + response.body());
        }
        PhotoReads.assertJpegContentType("查看照片 " + label, response);
        byte[] uploaded = photo.content().orElseThrow(() ->
                new IllegalStateException("照片「" + label + "」沒有成功上傳過的內容可比對"));
        if (!Arrays.equals(uploaded, response.bytes())) {
            throw new AssertionError("查看照片 " + label + " 拿到的位元組不是最後一次成功上傳的內容（"
                    + PhotoReads.lengthOf(response.bytes()) + " vs " + uploaded.length + " 位元組）");
        }
        boolean attached = requirePhotoRow(photo.photoId()).get("ticket_task_id") != null;
        check("查看照片 " + label + " 的 Cache-Control",
                attached ? ATTACHED_CACHE_CONTROL : UNATTACHED_CACHE_CONTROL,
                response.header(HttpHeaders.CACHE_CONTROL));

        if (viewSnapshot == null) {
            throw new IllegalStateException("沒有查看照片之前的資料快照：前一個 When 要是「查看照片」");
        }
        ViewSnapshot after = viewSnapshot();
        check("查看照片後 repair_photos 的內容", viewSnapshot.repairPhotos(), after.repairPhotos());
        check("查看照片後 tasks 的內容", viewSnapshot.tasks(), after.tasks());
        check("查看照片後 task_events 筆數", viewSnapshot.taskEvents(), after.taskEvents());
        check("查看照片後 outbox 筆數", viewSnapshot.outbox(), after.outbox());
    }

    @那麼("照片 {string} 的第一次上傳時間是 {string}")
    public void photoFirstUploadedAt(String label, String hhmm) {
        ScenarioWorld.PhotoLabel photo = world.photo(label);
        OffsetDateTime actual = uploadedAt(photo.photoId());
        if (actual == null) {
            throw new AssertionError("照片 " + label + " 的 repair_photos.uploaded_at 為空");
        }
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        OffsetDateTime expected = ZonedDateTime.of(today, LocalTime.parse(hhmm), clock.getZone()).toOffsetDateTime();
        check("照片 " + label + " 的 repair_photos.uploaded_at（到分鐘）",
                expected.toInstant(), actual.toInstant().truncatedTo(ChronoUnit.MINUTES));
        if (photo.uploadedAt() == null || !photo.uploadedAt().toInstant().equals(actual.toInstant())) {
            throw new AssertionError("照片 " + label + " 的 uploaded_at 被改成 " + actual + "，Given 記下的是 "
                    + photo.uploadedAt());
        }
    }

    @那麼("系統沒有建立照片")
    public void noPhotoCreated() {
        if (photoCountBefore < 0) {
            throw new IllegalStateException("沒有呼叫前的 repair_photos 筆數：前一個 When 要是建立或上傳照片");
        }
        check("repair_photos 筆數", photoCountBefore, photoCount());
    }

    @那麼("{string} 打開 {string} 時，{string} 照片 {int} 張都看得到")
    public void staffSeesTicketPhotos(String staffName, String ticketNo, String kindChinese, int count) {
        String purpose = lookupPurpose(kindChinese);
        UUID ticketTaskId = RepairTicketRefs.taskId(world, ticketNo);
        String jwt = world.jwt(staffName);
        ScenarioWorld.ApiResponse last = world.lastResponse();
        try {
            ScenarioWorld.ApiResponse ticket =
                    apiClient.get("/repair-tickets/" + ticketTaskId, BddApiClient.Auth.staff(jwt));
            if (ticket.status() != 200 || ticket.json() == null) {
                throw new AssertionError(staffName + " 讀 " + ticketNo + " 預期 200，實際 " + ticket.status()
                        + "，body=" + ticket.body());
            }
            String field = "report".equals(purpose) ? "photos" : "completionPhotos";
            JsonNode photos = ticket.json().path(field);
            check(ticketNo + " 的 " + field + " 張數", count, photos.size());
            for (JsonNode photo : photos) {
                String photoId = photo.path("id").asText(null);
                check(ticketNo + " 的 " + field + " 照片 " + photoId + " 的 status", "uploaded",
                        photo.path("status").asText(null));
                ScenarioWorld.ApiResponse content =
                        PhotoReads.assertReadableAsStored(apiClient, photoStorage, dbProbe, jwt, photoId);
                check("讀照片 " + photoId + " 的 Cache-Control", ATTACHED_CACHE_CONTROL,
                        content.header(HttpHeaders.CACHE_CONTROL));
                byte[] bytes = content.bytes();
                if (bytes.length < 3 || bytes[0] != (byte) 0xFF || bytes[1] != (byte) 0xD8 || bytes[2] != (byte) 0xFF) {
                    throw new AssertionError("讀照片 " + photoId + " 的內容不是 FF D8 FF 開頭");
                }
            }
        } finally {
            world.setLastResponse(last);
        }
        check(ticketNo + " 綁定、purpose=" + purpose + " 的 repair_photos 筆數", (long) count, dbProbe.count(
                "SELECT count(*) FROM repair_photos WHERE ticket_task_id = ? AND purpose = ?", ticketTaskId, purpose));
    }

    private String createPhotoArranged(String jwt, String purposeChinese, String label) {
        Map<String, Object> body = Map.of(
                "purpose", lookupPurpose(purposeChinese),
                "takenAt", OffsetDateTime.now(clock).toString());
        ScenarioWorld.ApiResponse response = apiClient.post("/repair-photos", BddApiClient.Auth.staff(jwt), body);
        String photoId = response.json() == null ? null : response.json().path("id").asText(null);
        if (!isSuccess(response) || photoId == null) {
            throw new IllegalStateException("前提「建立照片 " + label + "」沒鋪起來：HTTP " + response.status()
                    + "，body=" + response.body());
        }
        return photoId;
    }

    private void uploadArranged(String jwt, String photoId, PhotoFixtures.PhotoContent content, String label) {
        ScenarioWorld.ApiResponse response = apiClient.exchangeBytes(
                HttpMethod.PUT, contentPath(photoId), BddApiClient.Auth.staff(jwt), content.bytes(), content.contentType());
        if (!isSuccess(response)) {
            throw new IllegalStateException("前提「上傳照片 " + label + " 的內容」沒鋪起來：HTTP " + response.status()
                    + "，body=" + response.body());
        }
    }

    private String jwtOf(long tenantId, UUID staffId) {
        Map<String, Object> staff = dbProbe
                .queryOne("SELECT role, dept FROM staff_accounts WHERE tenant_id = ? AND id = ?", tenantId, staffId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 查無建立照片的員工 " + staffId));
        return jwtService.issue(tenantId, staffId, (String) staff.get("role"), (String) staff.get("dept")).token();
    }

    private OffsetDateTime uploadedAt(String photoId) {
        return jdbcClient
                .sql("SELECT uploaded_at FROM repair_photos WHERE id = ?")
                .param(UUID.fromString(photoId))
                .query(OffsetDateTime.class)
                .optional()
                .orElse(null);
    }

    private Map<String, Object> photoRow(String photoId) {
        return dbProbe.queryOne("SELECT * FROM repair_photos WHERE id = ?", UUID.fromString(photoId)).orElse(null);
    }

    private Map<String, Object> requirePhotoRow(String photoId) {
        Map<String, Object> row = photoRow(photoId);
        if (row == null) {
            throw new AssertionError("repair_photos 查無照片 " + photoId);
        }
        return row;
    }

    private long photoCount() {
        return dbProbe.count("SELECT count(*) FROM repair_photos");
    }

    private record ViewSnapshot(String repairPhotos, String tasks, long taskEvents, long outbox) {
    }

    private ViewSnapshot viewSnapshot() {
        return new ViewSnapshot(
                tableDigest("repair_photos"),
                tableDigest("tasks"),
                dbProbe.count("SELECT count(*) FROM task_events"),
                dbProbe.count("SELECT count(*) FROM outbox"));
    }

    private String tableDigest(String table) {
        return jdbcClient
                .sql("SELECT md5(COALESCE(string_agg(t::text, '|' ORDER BY t::text), '')) FROM " + table + " t")
                .query(String.class)
                .single();
    }

    private static String lookupPurpose(String chinese) {
        String purpose = PURPOSE.get(chinese);
        if (purpose == null) {
            throw new IllegalArgumentException("照片用途只認報修、完修，收到「" + chinese + "」");
        }
        return purpose;
    }

    private static String contentPath(String photoId) {
        return "/repair-photos/" + photoId + "/content";
    }

    private static boolean isSuccess(ScenarioWorld.ApiResponse response) {
        return response.status() >= 200 && response.status() < 300;
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }
}
