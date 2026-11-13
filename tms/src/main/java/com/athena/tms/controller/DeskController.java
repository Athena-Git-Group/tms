package com.athena.tms.controller;

import com.athena.tms.dto.DeskTaskFilter;
import com.athena.tms.dto.TaskResponse;
import com.athena.tms.dto.TriageDecisionRequest;
import com.athena.tms.service.DeskService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/desk"})
public class DeskController {
    private final DeskService deskService;
    private final Clock clock;

    public DeskController(DeskService deskService, Clock clock) {
        this.deskService = deskService;
        this.clock = clock;
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
    public List<TaskResponse> storeTasks(
            @RequestParam(required=false) UUID assigneeId,
            @RequestParam(name="state", required=false) List<String> states,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        DeskTaskFilter filter = DeskTaskFilter.parse(assigneeId, states, from, to, LocalDate.now(this.clock));
        return this.deskService.storeTasks(filter).stream().map(TaskResponse::from).toList();
    }
}
