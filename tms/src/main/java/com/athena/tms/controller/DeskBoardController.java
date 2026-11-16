package com.athena.tms.controller;

import com.athena.tms.common.error.ApiException;
import com.athena.tms.dto.desk.DeskAnswerRequest;
import com.athena.tms.dto.desk.DeskAssignRequest;
import com.athena.tms.dto.desk.DeskBoard;
import com.athena.tms.dto.desk.DeskQuickReply;
import com.athena.tms.dto.desk.DeskTask;
import com.athena.tms.dto.desk.DeskTaskDetail;
import com.athena.tms.filter.IdempotencyFilter;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 櫃檯 Web 的看板與派單入口，六支操作都在 {@code /api/v1/desk} 底下。
 *
 * <p>目前只有路徑與進出型別，每一支都回 501：櫃檯 Web 與測試可以先對著固定的路徑與形狀開發，
 * 規則之後逐支補上。舊的 {@code /desk/**}（{@link DeskController}）不在 {@code /api/v1} 底下，
 * 兩邊的路徑不會相撞。
 */
@RestController
@RequestMapping("/api/v1/desk")
public class DeskBoardController {

    @GetMapping("/board")
    public DeskBoard getDeskBoard() {
        throw new NotImplementedYet();
    }

    @GetMapping("/tasks/{id}")
    public DeskTaskDetail readDeskTask(@PathVariable UUID id) {
        throw new NotImplementedYet();
    }

    @GetMapping("/quick-replies")
    public List<DeskQuickReply> listDeskQuickReplies() {
        throw new NotImplementedYet();
    }

    // 三支寫入的 Idempotency-Key 先宣告成非必帶：沒帶時契約要回 400 idempotency_key_required，
    // 交給 Spring 擋會變成另一個錯誤碼，所以留給各支的規則自己判斷。
    @PostMapping("/tasks/{id}/assign")
    public DeskTask deskAssignTask(
            @PathVariable UUID id,
            @RequestHeader(value = IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody DeskAssignRequest request) {
        throw new NotImplementedYet();
    }

    @PostMapping("/tasks/{id}/assign/undo")
    public DeskTask deskUndoAssign(
            @PathVariable UUID id,
            @RequestHeader(value = IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        throw new NotImplementedYet();
    }

    @PostMapping("/tasks/{id}/answer")
    public DeskTask deskAnswerTask(
            @PathVariable UUID id,
            @RequestHeader(value = IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody DeskAnswerRequest request) {
        throw new NotImplementedYet();
    }

    /**
     * 還沒實作的操作回 501，錯誤內容沿用全站同一種形狀。
     * 只有這個控制器在過渡期用得到，六支都補上規則後連同這個類別一起拿掉。
     */
    private static final class NotImplementedYet extends ApiException {
        NotImplementedYet() {
            super(501, "not_implemented", "這個功能尚未實作");
        }
    }
}
