package com.athena.tms.service;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.UnauthorizedException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dao.StaffDeviceRepository;
import com.athena.tms.dto.LoginRequest;
import com.athena.tms.dto.LoginResponse;
import com.athena.tms.entity.StaffAccount;
import com.athena.tms.entity.StaffDevice;
import com.athena.tms.service.JwtService;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final StaffAccountRepository staffAccountRepository;
    private final StaffDeviceRepository staffDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(StaffAccountRepository staffAccountRepository, StaffDeviceRepository staffDeviceRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffDeviceRepository = staffDeviceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        this.validate(request);
        TenantContext.set(request.tenantId());
        StaffAccount account = this.staffAccountRepository.findByTenantIdAndLoginId(request.tenantId(), request.loginId()).orElse(null);
        if (account == null || !account.isActive() || !this.passwordEncoder.matches((CharSequence)request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException("帳號或密碼錯誤，或帳號已停用");
        }
        this.registerDevice(request, account);
        JwtService.IssuedToken issued = this.jwtService.issue(account.getTenantId(), account.getId(), account.getRole(), account.getDept());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(), new LoginResponse.Staff(account.getId(), account.getTenantId(), account.getName(), account.getRole(), account.getDept()));
    }

    private void registerDevice(LoginRequest request, StaffAccount account) {
        String fingerprint = request.deviceFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        StaffDevice device = this.staffDeviceRepository.findByTenantIdAndStaffIdAndDeviceFingerprint(account.getTenantId(), account.getId(), fingerprint).orElseGet(() -> {
            StaffDevice created = new StaffDevice();
            created.setId(UuidV7.generate());
            created.setTenantId(account.getTenantId());
            created.setStaffId(account.getId());
            created.setDeviceFingerprint(fingerprint);
            created.setRegisteredAt(OffsetDateTime.now());
            return created;
        });
        device.setRevokedAt(null);
        if (request.pushToken() != null && !request.pushToken().isBlank()) {
            device.setPushToken(request.pushToken());
        }
        this.staffDeviceRepository.save(device);
    }

    private void validate(LoginRequest request) {
        if (request.tenantId() == null) {
            throw new UnprocessableEntityException("tenantId 必填");
        }
        if (request.loginId() == null || request.loginId().isBlank()) {
            throw new UnprocessableEntityException("loginId 必填");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new UnprocessableEntityException("password 必填");
        }
    }
}
