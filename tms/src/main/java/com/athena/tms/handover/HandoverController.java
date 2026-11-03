package com.athena.tms.handover;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 房間交辦（本 plan 新增，FR-037、FR-038）。
 *
 * <p>骨架階段：僅預留路由落點，一律回 501，不含業務邏輯。
 * 對照 {@code specs/truth/contracts/groups/現場作業/房間交辦.yaml}：
 * createDeskNote（櫃檯）、markHandoverRead（員工）。
 */
@RestController
public class HandoverController {

    @PostMapping("/desk/desk-notes")
    public ResponseEntity<Void> createDeskNote() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/staff/tasks/{id}/handover-read")
    public ResponseEntity<Void> markHandoverRead(@PathVariable UUID id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
