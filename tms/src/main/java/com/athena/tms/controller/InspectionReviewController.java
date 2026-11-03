package com.athena.tms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 查房抽查與合格率（本 plan 新增，FR-026、FR-027）。
 *
 * <p>骨架階段：僅預留路由落點，一律回 501，不含業務邏輯。
 * 對照 {@code specs/truth/contracts/groups/現場作業/查房.yaml}：createSpotCheck、getInspectionPassRate。
 *
 * <p>沿用既有 {@link InspectionController} 的扁平 controller 慣例與
 * {@code /admin} 路由前綴，新增獨立類別以避免動到既有（已反編譯）類別。
 */
@RestController
@RequestMapping("/admin/inspections")
public class InspectionReviewController {

    @PostMapping("/spot-checks")
    public ResponseEntity<Void> createSpotCheck() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @GetMapping("/pass-rate")
    public ResponseEntity<Void> getInspectionPassRate() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
