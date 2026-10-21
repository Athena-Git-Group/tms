package com.athena.tms.controller;

import com.athena.tms.dto.TaskResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import com.athena.tms.dto.TriageDecisionRequest;
import com.athena.tms.service.DeskService;

/**
 * 櫃檯端點。URL 照 spec 字面：`GET /desk/triage` 查待判讀清單、
 * `POST /desk/triage/{id}` 做三選一判讀。`GET /desk/tasks` 的
 * 「看得到本店所有任務」做成獨立清單端點而非塞進 /desk/triage 的參數，
 * 因為兩者讀的範圍不同（待判讀 vs 全店），混在一個端點靠參數切換
 * 遲早有人漏帶參數拿到錯的範圍。進門資格（frontdesk/manager）由
 * SecurityConfig 前綴矩陣統一把關，這裡不重複判。
 */
@RestController
@RequestMapping("/desk")
public class DeskController {

    private final DeskService deskService;

    public DeskController(DeskService deskService) {
        this.deskService = deskService;
    }

    /** 本店全部待判讀（triage）任務。 */
    @GetMapping("/triage")
    public List<TaskResponse> triage() {
        return deskService.triageList().stream().map(TaskResponse::from).toList();
    }

    /** 判讀三選一：assign（給房務／給工務）或 answer（當場回答）。 */
    @PostMapping("/triage/{taskId}")
    public TaskResponse decide(@PathVariable UUID taskId,
                               @RequestBody TriageDecisionRequest request) {
        return TaskResponse.from(deskService.decide(taskId, request));
    }

    /** 本店所有任務（防重複建單的查詢面）。 */
    @GetMapping("/tasks")
    public List<TaskResponse> storeTasks() {
        return deskService.storeTasks().stream().map(TaskResponse::from).toList();
    }
}
