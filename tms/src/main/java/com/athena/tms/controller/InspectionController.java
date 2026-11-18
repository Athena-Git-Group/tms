package com.athena.tms.controller;

import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionResponse;
import com.athena.tms.service.InspectionService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InspectionController {
    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @PostMapping(value={"/admin/inspections/{taskId}"})
    public InspectionResponse inspect(@PathVariable UUID taskId, @RequestBody InspectionRequest request) {
        InspectionService.Judged judged = this.inspectionService.inspect(taskId, request);
        String taskState = judged.passed() ? "inspected" : "pool".equals(judged.destination()) ? "pool" : "accepted";
        return new InspectionResponse(taskId, judged.passed() ? "passed" : "failed", taskState,
                judged.redoCount(), "pool".equals(judged.destination()));
    }
}
