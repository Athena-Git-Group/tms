package com.athena.tms.controller;

import com.athena.tms.dto.TaskResponse;
import com.athena.tms.dto.TriageDecisionRequest;
import com.athena.tms.service.DeskService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/desk"})
public class DeskController {
    private final DeskService deskService;

    public DeskController(DeskService deskService) {
        this.deskService = deskService;
    }

    @GetMapping(value={"/triage"})
    public List<TaskResponse> triage() {
        return this.deskService.triageList().stream().map(TaskResponse::from).toList();
    }

    @PostMapping(value={"/triage/{taskId}"})
    public TaskResponse decide(@PathVariable UUID taskId, @RequestBody TriageDecisionRequest request) {
        return TaskResponse.from(this.deskService.decide(taskId, request));
    }

    @GetMapping(value={"/tasks"})
    public List<TaskResponse> storeTasks() {
        return this.deskService.storeTasks().stream().map(TaskResponse::from).toList();
    }
}
