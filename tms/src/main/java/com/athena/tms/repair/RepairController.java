package com.athena.tms.repair;

import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.RepairVerificationOutcome;
import com.athena.tms.dto.VerifyRepairTicketRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    public record CreatePhotoRequest(String purpose, String roomId, String takenAt) {
    }

    public record PrecheckRequest(UUID sourceTaskId, String category) {
    }

    public record CreateTicketRequest(
            UUID sourceTaskId,
            UUID roomId,
            Boolean isPublicArea,
            UUID assigneeId,
            String category,
            String description,
            List<UUID> photoIds,
            Boolean interruptSourceTask) {
    }

    public record DowngradeUrgencyRequest(String note) {
    }

    @PostMapping("/repair-photos")
    public ResponseEntity<Map<String, Object>> createRepairPhoto(@RequestBody CreatePhotoRequest request) {
        if (request == null || request.purpose() == null) {
            throw new UnprocessableEntityException("purpose 必填（report 或 completion）");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repairService.createPhoto(request.purpose(), request.takenAt()));
    }

    /**
     * 照片內容本體在 App 端已完成壓縮與浮水印、後端不做影像處理（contracts
     * createRepairPhoto 的 description），所以這裡不解讀 request body，只把
     * 這張照片標成已上傳。
     */
    @PutMapping("/repair-photos/{photoId}/content")
    public Map<String, Object> uploadRepairPhotoContent(@PathVariable UUID photoId) {
        return repairService.markUploaded(photoId);
    }

    @PostMapping("/repair-tickets/precheck")
    public Map<String, Object> precheckRepairTicket(@RequestBody PrecheckRequest request) {
        if (request == null || request.sourceTaskId() == null || request.category() == null) {
            throw new UnprocessableEntityException("sourceTaskId、category 必填");
        }
        return repairService.precheck(request.sourceTaskId(), request.category());
    }

    /**
     * 三種開單路徑擇一（contracts CreateRepairTicketRequest）：①帶 sourceTaskId（房務
     * 從自己未取消的任務開單）；②③不帶 sourceTaskId，改帶 roomId 或 isPublicArea=true
     * （工務自開或櫃檯交辦）。兩組互斥——同時帶回 422 repair_source_conflict，
     * 都沒帶回 422 repair_source_missing；interruptSourceTask／assigneeId 只在對應路徑
     * 才有意義，省略視為 false／null。
     */
    @PostMapping("/repair-tickets")
    public ResponseEntity<Map<String, Object>> createRepairTicket(@RequestBody CreateTicketRequest request) {
        if (request == null || request.category() == null) {
            throw new UnprocessableEntityException("category、photoIds 必填");
        }
        boolean hasSourceTask = request.sourceTaskId() != null;
        boolean hasDirectRoom = request.roomId() != null || Boolean.TRUE.equals(request.isPublicArea());
        if (hasSourceTask && hasDirectRoom) {
            throw new UnprocessableEntityException(
                    "repair_source_conflict", "sourceTaskId 與 roomId／isPublicArea 不可同時提供");
        }
        if (!hasSourceTask && !hasDirectRoom) {
            throw new UnprocessableEntityException(
                    "repair_source_missing", "sourceTaskId、roomId、isPublicArea 必須擇一提供");
        }
        boolean interrupt = hasSourceTask && Boolean.TRUE.equals(request.interruptSourceTask());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repairService.create(
                        request.sourceTaskId(),
                        request.roomId(),
                        request.isPublicArea(),
                        request.assigneeId(),
                        request.category(),
                        request.description(),
                        request.photoIds() == null ? List.of() : request.photoIds(),
                        interrupt));
    }

    /**
     * 列出同房未撤回的工務單（contracts listRepairTickets）。{@code roomId} 是契約
     * 唯一的查詢參數而且必填——缺了就沒有「哪一間房」可問，回 422 而不是回整個
     * 租戶的單。契約沒有「我報的工務單」這種本人視角參數，這裡也不自己加。
     */
    @GetMapping("/repair-tickets")
    public List<Map<String, Object>> listRepairTickets(@RequestParam(required = false) UUID roomId) {
        if (roomId == null) {
            throw new UnprocessableEntityException("roomId 必填");
        }
        return repairService.listByRoom(roomId);
    }

    public record AppendPhotosRequest(List<UUID> photoIds) {
    }

    @GetMapping("/repair-tickets/{ticketId}")
    public Map<String, Object> readRepairTicket(@PathVariable UUID ticketId) {
        return repairService.read(ticketId);
    }

    @PostMapping("/repair-tickets/{ticketId}/photos")
    public Map<String, Object> appendRepairTicketPhotos(
            @PathVariable UUID ticketId, @RequestBody AppendPhotosRequest request) {
        if (request == null || request.photoIds() == null || request.photoIds().isEmpty()) {
            throw new UnprocessableEntityException("photoIds 必填且至少 1 張");
        }
        return repairService.appendPhotos(ticketId, request.photoIds());
    }

    @PostMapping("/repair-tickets/{ticketId}/withdraw")
    public Map<String, Object> withdrawRepairTicket(@PathVariable UUID ticketId) {
        return repairService.withdraw(ticketId);
    }

    @PostMapping("/repair-tickets/{ticketId}/downgrade-urgency")
    public Map<String, Object> downgradeRepairTicketUrgency(
            @PathVariable UUID ticketId, @RequestBody DowngradeUrgencyRequest request) {
        if (request == null || request.note() == null || request.note().isBlank()) {
            throw new UnprocessableEntityException("note 必填");
        }
        return repairService.downgradeUrgency(ticketId, request.note());
    }

    @PostMapping("/repair-tickets/{ticketId}/release-to-pool")
    public Map<String, Object> releaseRepairTicketToPool(@PathVariable UUID ticketId) {
        return repairService.releaseToPool(ticketId);
    }

    @PostMapping("/repair-tickets/{ticketId}/approve")
    public Map<String, Object> approveRepairTicket(@PathVariable UUID ticketId) {
        return repairService.approve(ticketId);
    }

    public record RejectRepairTicketRequest(String reason) {
    }

    @PostMapping("/repair-tickets/{ticketId}/reject")
    public Map<String, Object> rejectRepairTicket(
            @PathVariable UUID ticketId, @RequestBody RejectRepairTicketRequest request) {
        return repairService.reject(ticketId, request == null ? null : request.reason());
    }

    @PostMapping("/repair-tickets/{ticketId}/resubmit")
    public Map<String, Object> resubmitRepairTicket(@PathVariable UUID ticketId) {
        return repairService.resubmit(ticketId);
    }

    @PostMapping("/repair-tickets/{ticketId}/verify")
    public RepairVerificationOutcome verifyRepairTicket(
            @PathVariable UUID ticketId, @RequestBody VerifyRepairTicketRequest request) {
        return repairService.verify(ticketId, request);
    }
}
