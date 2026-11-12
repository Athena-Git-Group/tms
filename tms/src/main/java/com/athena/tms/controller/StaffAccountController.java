package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dto.StaffChangePasswordRequest;
import com.athena.tms.dto.StaffLoginRequest;
import com.athena.tms.dto.StaffLoginResponse;
import com.athena.tms.service.StaffAccountLoginService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StaffAccountController {

    private final StaffAccountRepository staffAccountRepository;
    private final StaffAccountLoginService staffAccountLoginService;

    public StaffAccountController(
            StaffAccountRepository staffAccountRepository, StaffAccountLoginService staffAccountLoginService) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffAccountLoginService = staffAccountLoginService;
    }

    public record StaffAccountView(UUID id, String name, String role, String dept, boolean active) {}

    @GetMapping("/staff-accounts")
    public List<StaffAccountView> list() {
        long tenantId = TenantContext.require();
        return staffAccountRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(sa -> new StaffAccountView(sa.getId(), sa.getName(), sa.getRole(), sa.getDept(), sa.isActive()))
                .toList();
    }

    @PostMapping("/staff-accounts/login")
    public StaffLoginResponse login(@RequestBody StaffLoginRequest request) {
        return staffAccountLoginService.login(request);
    }

    @PostMapping("/staff-accounts/change-password")
    public StaffLoginResponse changePassword(@RequestBody StaffChangePasswordRequest request) {
        CurrentActor actor = CurrentActor.require();
        return staffAccountLoginService.changePassword(TenantContext.require(), actor.staffId(), request);
    }
}
