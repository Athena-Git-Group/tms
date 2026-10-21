package com.athena.tms.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionResponse;
import com.athena.tms.service.InspectionService;

/**
 * 查房提交端點。/admin 前綴讓 SecurityConfig 的角色矩陣天然只放
 * manager 進來；「自己清的房不能自己查」仍在 service 再擋一次，矩陣擋的是
 * 「房務不能查房」，擋不住「主管自己清了自己查」。
 */
@RestController
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @PostMapping("/admin/inspections/{taskId}")
    public InspectionResponse inspect(@PathVariable UUID taskId,
                                      @RequestBody InspectionRequest request) {
        return inspectionService.inspect(taskId, request);
    }
}
