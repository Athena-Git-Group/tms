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

/**
 * 房間交辦 API（FR-037、FR-038），路由對應 contracts 的 createDeskNote 與
 * markHandoverRead。寫入的冪等由全域的 Idempotency-Key 回放
 * （{@link com.athena.tms.filter.IdempotencyFilter}）加上 handover_reads 的
 * 主鍵衝突不重記雙層把關。
 */
@RestController
@RequestMapping("/api/v1")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /** 櫃檯為房間留備註，建立成功回 201 與該筆 DeskNote。 */
    @PostMapping("/desk-notes")
    public ResponseEntity<Map<String, Object>> createDeskNote(@RequestBody CreateDeskNoteRequest request) {
        Map<String, Object> created = handoverService.createDeskNote(request.roomId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 員工展開任務的交辦；重複展開回放第一筆已讀，不新增。 */
    @PostMapping("/tasks/{id}/handover-read")
    public Map<String, Object> markHandoverRead(@PathVariable UUID id) {
        return handoverService.markRead(id);
    }

    /** createDeskNote 的請求形狀，對應 contracts 的 CreateDeskNoteRequest。 */
    public record CreateDeskNoteRequest(UUID roomId, String content) {}
}
