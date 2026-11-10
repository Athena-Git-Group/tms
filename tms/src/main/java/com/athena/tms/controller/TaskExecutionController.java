package com.athena.tms.controller;

import com.athena.tms.dto.CompleteTaskRequest;
import com.athena.tms.dto.HoldReasonRequest;
import com.athena.tms.dto.RequeueRequest;
import com.athena.tms.dto.SopCheckItemRequest;
import com.athena.tms.service.TaskExecutionService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TaskExecutionController {

    private final TaskExecutionService taskExecutionService;

    public TaskExecutionController(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    @GetMapping("/tasks/{id}")
    public Map<String, Object> readTask(@PathVariable UUID id) {
        return taskExecutionService.detail(id);
    }

    @PostMapping("/tasks/{id}/defer")
    public Map<String, Object> defer(@PathVariable UUID id, @RequestBody(required = false) HoldReasonRequest request) {
        return taskExecutionService.deferBySelf(id, request);
    }

    @PostMapping("/tasks/{id}/claim")
    public Map<String, Object> claim(@PathVariable UUID id) {
        return taskExecutionService.claim(id);
    }

    @PostMapping("/tasks/{id}/start")
    public Map<String, Object> start(@PathVariable UUID id) {
        return taskExecutionService.start(id);
    }

    @PostMapping("/tasks/{id}/sop-check")
    public Map<String, Object> sopCheck(@PathVariable UUID id, @RequestBody SopCheckItemRequest request) {
        return taskExecutionService.sopCheck(id, request);
    }

    @PostMapping("/tasks/{id}/interrupt")
    public Map<String, Object> interrupt(
            @PathVariable UUID id, @RequestBody(required = false) HoldReasonRequest request) {
        return taskExecutionService.interrupt(id, request);
    }

    @PostMapping("/tasks/{id}/complete")
    public Map<String, Object> complete(
            @PathVariable UUID id, @RequestBody(required = false) CompleteTaskRequest request) {
        return taskExecutionService.complete(id, request);
    }

    @PostMapping("/tasks/{id}/requeue")
    public Map<String, Object> requeue(
            @PathVariable UUID id, @RequestBody(required = false) RequeueRequest request) {
        return taskExecutionService.requeue(id, request);
    }

    public record CancelTaskRequest(String reason, String note) {}

    @PostMapping("/tasks/{id}/cancel")
    public Map<String, Object> cancel(
            @PathVariable UUID id, @RequestBody(required = false) CancelTaskRequest request) {
        return taskExecutionService.cancel(
                id, request == null ? null : request.reason(), request == null ? null : request.note());
    }

    public record ReassignTaskRequest(UUID assigneeId) {}

    @PostMapping("/tasks/{id}/reassign")
    public Map<String, Object> reassign(
            @PathVariable UUID id, @RequestBody(required = false) ReassignTaskRequest request) {
        return taskExecutionService.reassign(id, request == null ? null : request.assigneeId());
    }

    @PostMapping("/tasks/{id}/resume")
    public Map<String, Object> resume(@PathVariable UUID id) {
        return taskExecutionService.resume(id);
    }

    @GetMapping("/pool")
    public Map<String, Object> pool(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean urgent,
            @RequestParam(required = false) String source) {
        return taskExecutionService.deptPool(dept, category, urgent, source);
    }

    /** 查看呼叫者的今日進度與下一間；統計口徑與排序規則在 {@link TaskExecutionService#myToday()}。 */
    @GetMapping("/me/today")
    public Map<String, Object> myToday() {
        return taskExecutionService.myToday();
    }
}
