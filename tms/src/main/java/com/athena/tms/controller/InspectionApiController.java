package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.codes.RolePermissions;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionResponse;
import com.athena.tms.service.InspectionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * 請求形狀對應 contracts 查房.yaml 的 inspectionsRequest：tenantId 與 inspectorId
     * 不採信請求值（租戶與操作者一律以認證身分為準），result 由逐項判定推導。
     */
    public record CreateInspectionRequest(
            Long tenantId, UUID taskId, UUID inspectorId, String result, List<InspectionRequest.Item> items) {}

    @PostMapping("/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionResponse create(@RequestBody CreateInspectionRequest request) {
        requireInspectSubmit();
        if (request == null || request.taskId() == null) {
            throw new UnprocessableEntityException("taskId 必填");
        }
        return inspectionService.inspect(request.taskId(), new InspectionRequest(request.items()));
    }

    private void requireInspectSubmit() {
        CurrentActor actor = CurrentActor.require();
        if (!RolePermissions.canInspectSubmit(actor.role())) {
            throw new ForbiddenException("此角色沒有查房判定（inspect_submit）權限");
        }
    }
}
