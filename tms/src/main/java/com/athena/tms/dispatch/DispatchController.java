package com.athena.tms.dispatch;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 員工工作台（本 plan 新增，FR-028–032）。
 *
 * <p>骨架階段：僅預留路由落點，一律回 501，不含業務邏輯。
 * 對照 {@code specs/truth/contracts/groups/現場作業/員工工作台.yaml}：getMyToday。
 *
 * <p>listDeptPool（GET /pool）不在此新增：該落點已存在於既有
 * {@code com.athena.tms.controller.StaffTaskController#pool()}（{@code GET /staff/pool}）。
 * 新 contract 的 category 篩選與 counts 回應屬於行為擴充，留給後續 Phase 在原方法上加強，
 * 不在此另開一條會與既有路由衝突的重複路由。
 */
@RestController
public class DispatchController {

    @GetMapping("/staff/me/today")
    public ResponseEntity<Void> getMyToday() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
