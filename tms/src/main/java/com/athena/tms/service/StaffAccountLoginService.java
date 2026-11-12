package com.athena.tms.service;

import com.athena.tms.common.CredentialFingerprint;
import com.athena.tms.common.error.AccountDisabledException;
import com.athena.tms.common.error.AccountLockedException;
import com.athena.tms.common.error.CredentialsInvalidException;
import com.athena.tms.common.error.CurrentPasswordInvalidException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dto.StaffChangePasswordRequest;
import com.athena.tms.dto.StaffLoginRequest;
import com.athena.tms.dto.StaffLoginResponse;
import com.athena.tms.entity.StaffAccount;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class StaffAccountLoginService {

    private static final int LOCKOUT_THRESHOLD = 5;

    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private static final String CREDENTIALS_INVALID_MESSAGE = "員工編號或密碼不正確";

    private final StaffAccountRepository staffAccountRepository;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public StaffAccountLoginService(
            StaffAccountRepository staffAccountRepository,
            TenantScopedExecutor tenantScopedExecutor,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.staffAccountRepository = staffAccountRepository;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public StaffLoginResponse login(StaffLoginRequest request) {
        validate(request);
        long tenantId = resolveTenantId(request.loginId());
        // TransactionTemplate 遇到往外拋的 RuntimeException 會整筆 rollback，失敗次數與鎖定就寫不進去，
        // 所以 evaluate() 回傳 Outcome，等交易 commit 之後才在這裡拋。
        Outcome outcome = tenantScopedExecutor.callAsTenant(tenantId, () -> evaluate(tenantId, request));
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.response();
    }

    public StaffLoginResponse changePassword(long tenantId, UUID staffId, StaffChangePasswordRequest request) {
        StaffAccount account = staffAccountRepository
                .findByTenantIdAndId(tenantId, staffId)
                .orElseThrow(() -> new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE));
        if (!account.isActive()) {
            throw new AccountDisabledException("帳號已停用，登入被拒");
        }
        if (request == null || request.newPassword() == null || request.newPassword().isBlank()) {
            throw new UnprocessableEntityException("newPassword 必填");
        }
        if (!account.isMustChangePassword()) {
            String currentPassword = request.currentPassword();
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new UnprocessableEntityException("current_password_required", "請輸入目前密碼");
            }
            if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
                throw new CurrentPasswordInvalidException("目前密碼不正確");
            }
        }
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new UnprocessableEntityException("new_password_invalid", "新密碼不能和目前密碼相同");
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setMustChangePassword(false);
        account.setUpdatedAt(OffsetDateTime.now());
        staffAccountRepository.save(account);

        JwtService.IssuedToken issued = jwtService.issue(
                tenantId,
                account.getId(),
                account.getRole(),
                account.getDept(),
                CredentialFingerprint.of(account.getPasswordHash()));
        return new StaffLoginResponse(
                issued.token(),
                issued.expiresAt(),
                account.getId(),
                account.getName(),
                account.getDept(),
                account.getRole(),
                false);
    }

    private long resolveTenantId(String loginId) {
        for (long tenantId : tenantScopedExecutor.tenantIds()) {
            boolean exists = tenantScopedExecutor.callAsTenant(
                    tenantId, () -> staffAccountRepository.findByLoginId(loginId).isPresent());
            if (exists) {
                return tenantId;
            }
        }
        throw new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE);
    }

    private Outcome evaluate(long tenantId, StaffLoginRequest request) {
        StaffAccount account = staffAccountRepository.findByLoginId(request.loginId()).orElse(null);
        if (account == null) {
            return Outcome.failure(new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE));
        }

        if (!account.isActive()) {
            return Outcome.failure(new AccountDisabledException("帳號已停用，登入被拒"));
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime lockedUntil = account.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            long remainingMinutes = Duration.between(now, lockedUntil).toMinutes() + 1;
            return Outcome.failure(new AccountLockedException(
                    "帳號已鎖定，請稍後再試",
                    Map.of("lockedUntil", lockedUntil.toString(), "remainingMinutes", remainingMinutes)));
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            recordFailedAttempt(account, now);
            return Outcome.failure(new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE));
        }

        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        staffAccountRepository.save(account);

        JwtService.IssuedToken issued =
                jwtService.issue(
                        tenantId,
                        account.getId(),
                        account.getRole(),
                        account.getDept(),
                        CredentialFingerprint.of(account.getPasswordHash()));
        return Outcome.success(new StaffLoginResponse(
                issued.token(),
                issued.expiresAt(),
                account.getId(),
                account.getName(),
                account.getDept(),
                account.getRole(),
                account.isMustChangePassword()));
    }

    private record Outcome(StaffLoginResponse response, RuntimeException failure) {
        static Outcome success(StaffLoginResponse response) {
            return new Outcome(response, null);
        }

        static Outcome failure(RuntimeException failure) {
            return new Outcome(null, failure);
        }
    }

    private void recordFailedAttempt(StaffAccount account, OffsetDateTime now) {
        int newCount = account.getFailedLoginCount() + 1;
        account.setFailedLoginCount(newCount);
        if (newCount >= LOCKOUT_THRESHOLD) {
            account.setLockedUntil(now.plus(LOCKOUT_DURATION));
        }
        staffAccountRepository.save(account);
    }

    private void validate(StaffLoginRequest request) {
        if (request.loginId() == null || request.loginId().isBlank()) {
            throw new UnprocessableEntityException("loginId 必填");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new UnprocessableEntityException("password 必填");
        }
    }
}
