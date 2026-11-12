package com.athena.tms.controller;

import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.dto.InspectionOutcome;
import com.athena.tms.filter.IdempotencyFilter;
import com.athena.tms.service.InspectionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InspectionApiController {

    private final InspectionService inspectionService;

    public InspectionApiController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    public record CreateInspectionRequest(UUID taskId, List<InspectionRequest.Item> items) {}

    @PostMapping("/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionOutcome create(
            @RequestHeader(value = IdempotencyFilter.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody CreateInspectionRequest request) {
        if (request == null || request.taskId() == null) {
            throw new UnprocessableEntityException("taskId 必填");
        }
        InspectionService.Judged judged = inspectionService.inspect(request.taskId(), new InspectionRequest(request.items()));
        return new InspectionOutcome(judged.inspectionId(), judged.taskId(), judged.roomNo(),
                judged.passed() ? "passed" : "failed", judged.destination(), judged.cleanerName(),
                judged.failedItems(), judged.createdAt());
    }
}
