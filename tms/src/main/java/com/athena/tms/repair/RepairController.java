package com.athena.tms.repair;

import com.athena.tms.common.error.UnprocessableEntityException;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 工務單與照片 API，路由對應 contracts 的 createRepairPhoto、
 * uploadRepairPhotoContent、precheckRepairTicket、createRepairTicket、
 * readRepairTicket、appendRepairTicketPhotos、withdrawRepairTicket。
 * listRepairTickets 屬於後續 phase，先維持 501 佔位。
 */
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
            String category,
            String description,
            List<UUID> photoIds,
            Boolean urgent,
            Boolean interruptSourceTask) {
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

    @PostMapping("/repair-tickets")
    public ResponseEntity<Map<String, Object>> createRepairTicket(@RequestBody CreateTicketRequest request) {
        if (request == null || request.sourceTaskId() == null || request.category() == null
                || request.urgent() == null || request.interruptSourceTask() == null) {
            throw new UnprocessableEntityException("sourceTaskId、category、photoIds、urgent、interruptSourceTask 必填");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(repairService.create(
                        request.sourceTaskId(),
                        request.category(),
                        request.description(),
                        request.photoIds() == null ? List.of() : request.photoIds(),
                        request.urgent(),
                        request.interruptSourceTask()));
    }

    @GetMapping("/repair-tickets")
    public ResponseEntity<Void> listRepairTickets() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
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
}
