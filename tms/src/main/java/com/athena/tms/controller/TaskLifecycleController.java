package com.athena.tms.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 任務中斷／恢復（本 plan 新增，FR-018、FR-019）。
 *
 * <p>骨架階段：僅預留路由落點，一律回 501，不含業務邏輯。
 * 對照 {@code specs/truth/contracts/groups/現場作業/任務.yaml}：tasksInterrupt、tasksResume。
 *
 * <p>沿用既有 {@link StaffTaskController} 的扁平 controller 慣例與
 * {@code /staff} 路由前綴，新增獨立類別以避免動到既有（已反編譯）類別。
 */
@RestController
@RequestMapping("/staff/tasks")
public class TaskLifecycleController {

    @PostMapping("/{id}/interrupt")
    public ResponseEntity<Void> interrupt(@PathVariable UUID id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resume(@PathVariable UUID id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
