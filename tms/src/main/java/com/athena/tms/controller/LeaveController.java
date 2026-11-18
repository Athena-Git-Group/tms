package com.athena.tms.controller;

import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.entity.Leave;
import com.athena.tms.service.LeaveService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    public record CreateLeaveRequest(Long tenantId, UUID staffId, OffsetDateTime fromAt, OffsetDateTime toAt) {}

    @PostMapping("/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public Leave create(@RequestBody CreateLeaveRequest request) {
        if (request == null) {
            throw new UnprocessableEntityException("請求內容必填");
        }
        return leaveService.create(request.staffId(), request.fromAt(), request.toAt());
    }
}
