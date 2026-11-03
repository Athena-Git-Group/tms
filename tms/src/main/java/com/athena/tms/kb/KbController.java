package com.athena.tms.kb;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 查一下：推薦、回饋、問主管（本 plan 新增，FR-039–044）。
 *
 * <p>骨架階段：僅預留路由落點，一律回 501，不含業務邏輯。
 * 對照 {@code specs/truth/contracts/groups/知識與通知/房間記憶與問一下.yaml}：
 * listKbSuggestions、submitKbFeedback、escalateKbQuestion（員工）、
 * listKbEscalations、replyKbEscalation（主管）。
 */
@RestController
public class KbController {

    @GetMapping("/staff/room-memories/suggestions")
    public ResponseEntity<Void> listKbSuggestions() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/staff/room-memories/answers/{answerId}/feedback")
    public ResponseEntity<Void> submitKbFeedback(@PathVariable UUID answerId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/staff/room-memories/answers/{answerId}/escalate")
    public ResponseEntity<Void> escalateKbQuestion(@PathVariable UUID answerId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @GetMapping("/admin/room-memories/escalations")
    public ResponseEntity<Void> listKbEscalations() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/admin/room-memories/escalations/{escalationId}/reply")
    public ResponseEntity<Void> replyKbEscalation(@PathVariable UUID escalationId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
