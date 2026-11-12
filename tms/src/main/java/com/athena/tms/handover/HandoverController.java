package com.athena.tms.handover;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @PostMapping("/desk-notes")
    public ResponseEntity<Map<String, Object>> createDeskNote(@RequestBody CreateDeskNoteRequest request) {
        Map<String, Object> created = handoverService.createDeskNote(request.roomId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/tasks/{id}/handover-read")
    public Map<String, Object> markHandoverRead(@PathVariable UUID id) {
        return handoverService.markRead(id);
    }

    public record CreateDeskNoteRequest(UUID roomId, String content) {}
}
