package com.athena.tms.repair;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 工務單與照片（本 plan 新增，FR-001–021）。
 *
 * <p>骨架階段：僅預留路由落點，一律回 501，不含業務邏輯。
 * 對照 {@code specs/truth/contracts/groups/現場作業/工務單.yaml}：
 * createRepairPhoto、uploadRepairPhotoContent、
 * listRepairTickets／createRepairTicket、precheckRepairTicket、
 * readRepairTicket、appendRepairTicketPhotos、withdrawRepairTicket。
 */
@RestController
@RequestMapping("/staff")
public class RepairController {

    @PostMapping("/repair-photos")
    public ResponseEntity<Void> createRepairPhoto() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PutMapping("/repair-photos/{photoId}/content")
    public ResponseEntity<Void> uploadRepairPhotoContent(@PathVariable UUID photoId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @GetMapping("/repair-tickets")
    public ResponseEntity<Void> listRepairTickets() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/repair-tickets")
    public ResponseEntity<Void> createRepairTicket() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/repair-tickets/precheck")
    public ResponseEntity<Void> precheckRepairTicket() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @GetMapping("/repair-tickets/{ticketId}")
    public ResponseEntity<Void> readRepairTicket(@PathVariable UUID ticketId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/repair-tickets/{ticketId}/photos")
    public ResponseEntity<Void> appendRepairTicketPhotos(@PathVariable UUID ticketId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/repair-tickets/{ticketId}/withdraw")
    public ResponseEntity<Void> withdrawRepairTicket(@PathVariable UUID ticketId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
