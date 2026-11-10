package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.codes.RolePermissions;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.entity.Inspection;
import com.athena.tms.service.InspectionReviewService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抽查與查房合格率 API，對應 contracts 查房.yaml 的 createSpotCheck
 * （POST /inspections/spot-checks）與 getInspectionPassRate（GET /inspections/pass-rate）。
 * 抽查和一般查房（{@link InspectionApiController}）同屬查房判定，進業務邏輯前
 * 先過同一套角色×動作矩陣：沒有查房判定（inspect_submit）權限的角色一律 403。
 */
@RestController
@RequestMapping("/api/v1/inspections")
public class InspectionReviewController {

    private final InspectionReviewService inspectionReviewService;

    public InspectionReviewController(InspectionReviewService inspectionReviewService) {
        this.inspectionReviewService = inspectionReviewService;
    }

    public record SpotCheckRequest(UUID taskId, List<InspectionRequest.Item> items) {}

    @PostMapping("/spot-checks")
    @ResponseStatus(HttpStatus.CREATED)
    public Inspection createSpotCheck(@RequestBody SpotCheckRequest request) {
        requireInspectSubmit();
        if (request == null || request.taskId() == null) {
            throw new UnprocessableEntityException("taskId 必填");
        }
        return inspectionReviewService.spotCheck(request.taskId(), request.items());
    }

    @GetMapping("/pass-rate")
    public InspectionReviewService.PassRate getInspectionPassRate(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        requireInspectSubmit();
        return inspectionReviewService.passRate(date);
    }

    private void requireInspectSubmit() {
        CurrentActor actor = CurrentActor.require();
        if (!RolePermissions.canInspectSubmit(actor.role())) {
            throw new ForbiddenException("此角色沒有查房判定（inspect_submit）權限");
        }
    }
}
