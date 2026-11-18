package com.athena.tms.kb;

import com.athena.tms.common.error.UnprocessableEntityException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class KbController {

    private final KbSuggestionService kbSuggestionService;
    private final KbAskService kbAskService;

    public KbController(KbSuggestionService kbSuggestionService, KbAskService kbAskService) {
        this.kbSuggestionService = kbSuggestionService;
        this.kbAskService = kbAskService;
    }

    public record AskRequest(UUID taskId, String question) {
    }

    public record FeedbackRequest(Boolean helpful) {
    }

    public record ReplyRequest(String reply) {
    }

    @GetMapping("/room-memories/suggestions")
    public List<KbSuggestion> listKbSuggestions(@RequestParam UUID taskId) {
        return kbSuggestionService.listSuggestions(taskId);
    }

    @PostMapping("/room-memories/ask")
    public KbAnswer roomMemoryAsk(@RequestBody AskRequest request) {
        if (request == null || request.taskId() == null
                || request.question() == null || request.question().isBlank()) {
            throw new UnprocessableEntityException("taskId、question 必填");
        }
        return kbAskService.ask(request.taskId(), request.question());
    }

    @PostMapping("/room-memories/answers/{answerId}/feedback")
    public ResponseEntity<Void> submitKbFeedback(
            @PathVariable UUID answerId, @RequestBody FeedbackRequest request) {
        if (request == null || request.helpful() == null) {
            throw new UnprocessableEntityException("helpful 必填");
        }
        kbAskService.submitFeedback(answerId, request.helpful());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/room-memories/answers/{answerId}/escalate")
    public ResponseEntity<KbEscalationView> escalateKbQuestion(@PathVariable UUID answerId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kbAskService.escalate(answerId));
    }

    @GetMapping("/room-memories/escalations")
    public List<KbEscalationView> listKbEscalations(@RequestParam(required = false) String status) {
        return kbAskService.listEscalations(status);
    }

    @PostMapping("/room-memories/escalations/{escalationId}/reply")
    public KbEscalationView replyKbEscalation(
            @PathVariable UUID escalationId, @RequestBody ReplyRequest request) {
        if (request == null || request.reply() == null || request.reply().isBlank()) {
            throw new UnprocessableEntityException("reply 必填");
        }
        return kbAskService.reply(escalationId, request.reply());
    }
}
