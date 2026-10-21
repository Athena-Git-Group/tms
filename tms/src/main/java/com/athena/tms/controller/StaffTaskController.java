package com.athena.tms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import com.athena.tms.dto.SopCheckRequest;
import com.athena.tms.dto.SopCheckResponse;
import com.athena.tms.dto.TaskResponse;
import com.athena.tms.service.StaffTaskService;

/**
 * 員工任務端點。URL 照 spec 字面：`GET /staff/pool` 查本部門待接池、
 * `POST /staff/tasks/{id}/sop/{item}` 勾 SOP；claim/start/complete 掛在
 * 同一資源前綴下。/staff 的寫入權在 SecurityConfig 的角色矩陣限
 * housekeeping/engineering/manager，frontdesk 只有 GET。部門歸屬不在矩陣層，
 * 由引擎以條件式 UPDATE（WHERE 帶 CurrentActor.dept()）判定。
 */
@RestController
@RequestMapping("/staff")
public class StaffTaskController {

    private final StaffTaskService staffTaskService;

    public StaffTaskController(StaffTaskService staffTaskService) {
        this.staffTaskService = staffTaskService;
    }

    /** 本部門 pool 任務，先進先出（久等的單先被看到）。 */
    @GetMapping("/pool")
    public List<TaskResponse> pool() {
        return staffTaskService.pool().stream().map(TaskResponse::from).toList();
    }

    /** 認領（pool→accepted）。失敗一律 409 state_conflict。 */
    @PostMapping("/tasks/{taskId}/claim")
    public TaskResponse claim(@PathVariable UUID taskId) {
        return TaskResponse.from(staffTaskService.claim(taskId));
    }

    /** 開始（accepted→doing，記 started_at）。 */
    @PostMapping("/tasks/{taskId}/start")
    public TaskResponse start(@PathVariable UUID taskId) {
        return TaskResponse.from(staffTaskService.start(taskId));
    }

    /** 完成（doing→done，記 done_at；實際分鐘屬衍生值、不落欄）。 */
    @PostMapping("/tasks/{taskId}/complete")
    public TaskResponse complete(@PathVariable UUID taskId) {
        return TaskResponse.from(staffTaskService.complete(taskId));
    }

    /** SOP 勾選或跳過（{item}=sop_items.item_key，例如 bed_sheets）。 */
    @PostMapping("/tasks/{taskId}/sop/{itemKey}")
    public SopCheckResponse sopCheck(@PathVariable UUID taskId,
                                     @PathVariable String itemKey,
                                     @RequestBody SopCheckRequest request) {
        return SopCheckResponse.from(staffTaskService.sopCheck(taskId, itemKey, request));
    }
}
