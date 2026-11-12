package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.codes.RolePermissions;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionOutcome;
import com.athena.tms.filter.IdempotencyFilter;
import com.athena.tms.service.InspectionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 員工 App 的查房判定 API，對應 contracts 的 createInspections（POST /inspections）。
 * 進業務邏輯前先過角色×動作矩陣：沒有查房判定（inspect_submit）權限的角色一律 403，
 * 權威狀態不得有任何改動。判定本體（涵蓋全集驗證、通過／退回的狀態轉移、PMS 回寫）
 * 委派既有 {@link InspectionService}。
 */
@RestController
@RequestMapping("/api/v1")
public class InspectionApiController {

    private final InspectionService inspectionService;

    public InspectionApiController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    /**
     * 請求形狀對應 contracts 查房.yaml 的 CreateInspectionRequest：只收 taskId 與逐項判定。
     * 租戶與查房者一律以認證身分為準，通過或退回由 items 推導，所以請求不收
     * tenantId、inspectorId 與 result。
     */
    public record CreateInspectionRequest(UUID taskId, List<InspectionRequest.Item> items) {}

    /**
     * 判定成功回 201 與 {@link InspectionOutcome}。Idempotency-Key 必帶，沒帶回 400。同一把 Idempotency-Key 重送時，全域
     * {@link IdempotencyFilter} 在進到這裡之前就回放第一次的回應，所以查房只判一次。
     */
    @PostMapping("/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionOutcome create(
            @RequestHeader(value = IdempotencyFilter.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody CreateInspectionRequest request) {
        requireInspectSubmit();
        if (request == null || request.taskId() == null) {
            throw new UnprocessableEntityException("taskId 必填");
        }
        InspectionService.Judged judged = inspectionService.inspect(request.taskId(), new InspectionRequest(request.items()));
        return new InspectionOutcome(judged.inspectionId(), judged.taskId(), judged.roomNo(),
                judged.passed() ? "passed" : "failed", judged.destination(), judged.cleanerName(),
                judged.failedItems(), judged.createdAt());
    }

    private void requireInspectSubmit() {
        CurrentActor actor = CurrentActor.require();
        if (!RolePermissions.canInspectSubmit(actor.role())) {
            throw new ForbiddenException("此角色沒有查房判定（inspect_submit）權限");
        }
    }
}
