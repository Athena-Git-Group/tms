/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package com.athena.tms.controller;

import com.athena.tms.dto.SopCheckRequest;
import com.athena.tms.dto.SopCheckResponse;
import com.athena.tms.dto.TaskResponse;
import com.athena.tms.service.StaffTaskService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/staff"})
public class StaffTaskController {
    private final StaffTaskService staffTaskService;

    public StaffTaskController(StaffTaskService staffTaskService) {
        this.staffTaskService = staffTaskService;
    }

    @GetMapping(value={"/pool"})
    public List<TaskResponse> pool() {
        return this.staffTaskService.pool().stream().map(TaskResponse::from).toList();
    }

    @PostMapping(value={"/tasks/{taskId}/claim"})
    public TaskResponse claim(@PathVariable UUID taskId) {
        return TaskResponse.from(this.staffTaskService.claim(taskId));
    }

    @PostMapping(value={"/tasks/{taskId}/start"})
    public TaskResponse start(@PathVariable UUID taskId) {
        return TaskResponse.from(this.staffTaskService.start(taskId));
    }

    @PostMapping(value={"/tasks/{taskId}/complete"})
    public TaskResponse complete(@PathVariable UUID taskId) {
        return TaskResponse.from(this.staffTaskService.complete(taskId));
    }

    @PostMapping(value={"/tasks/{taskId}/sop/{itemKey}"})
    public SopCheckResponse sopCheck(@PathVariable UUID taskId, @PathVariable String itemKey, @RequestBody SopCheckRequest request) {
        return SopCheckResponse.from(this.staffTaskService.sopCheck(taskId, itemKey, request));
    }
}
