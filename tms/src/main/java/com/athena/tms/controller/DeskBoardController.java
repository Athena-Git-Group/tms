package com.athena.tms.controller;

import com.athena.tms.dto.desk.DeskAnswerRequest;
import com.athena.tms.dto.desk.DeskAssignRequest;
import com.athena.tms.dto.desk.DeskBoard;
import com.athena.tms.dto.desk.DeskQuickReply;
import com.athena.tms.dto.desk.DeskTask;
import com.athena.tms.dto.desk.DeskTaskDetail;
import com.athena.tms.filter.IdempotencyFilter;
import com.athena.tms.service.DeskBoardService;
import com.athena.tms.service.DeskQuickReplyService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/desk")
public class DeskBoardController {
    private final DeskBoardService deskBoardService;
    private final DeskQuickReplyService deskQuickReplyService;

    public DeskBoardController(DeskBoardService deskBoardService, DeskQuickReplyService deskQuickReplyService) {
        this.deskBoardService = deskBoardService;
        this.deskQuickReplyService = deskQuickReplyService;
    }

    @GetMapping("/board")
    public DeskBoard getDeskBoard() {
        return this.deskBoardService.board();
    }

    @GetMapping("/tasks/{id}")
    public DeskTaskDetail readDeskTask(@PathVariable UUID id) {
        return this.deskBoardService.detail(id);
    }

    @GetMapping("/quick-replies")
    public List<DeskQuickReply> listDeskQuickReplies() {
        return this.deskQuickReplyService.list();
    }

    @PostMapping("/tasks/{id}/assign")
    public DeskTask deskAssignTask(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody DeskAssignRequest request) {
        return this.deskBoardService.assign(id, idempotencyKey, request);
    }

    @PostMapping("/tasks/{id}/assign/undo")
    public DeskTask deskUndoAssign(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER) String idempotencyKey) {
        return this.deskBoardService.undoAssign(id, idempotencyKey);
    }

    @PostMapping("/tasks/{id}/answer")
    public DeskTask deskAnswerTask(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody DeskAnswerRequest request) {
        return this.deskBoardService.answer(id, idempotencyKey, request);
    }
}
