package com.athena.tms.service;

import com.athena.tms.common.CredentialFingerprint;
import com.athena.tms.common.HotelCode;
import com.athena.tms.common.NewPasswordStrength;
import com.athena.tms.common.codes.RolePermissions;
import com.athena.tms.common.error.AccountDisabledException;
import com.athena.tms.common.error.AccountLockedException;
import com.athena.tms.common.error.CredentialsInvalidException;
import com.athena.tms.common.error.CurrentPasswordInvalidException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dao.TenantRepository;
import com.athena.tms.dto.StaffChangePasswordRequest;
import com.athena.tms.dto.StaffLoginRequest;
import com.athena.tms.dto.StaffLoginResponse;
import com.athena.tms.entity.StaffAccount;
import com.athena.tms.entity.Tenant;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class StaffAccountLoginService {

    private static final int LOCKOUT_THRESHOLD = 5;

    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private static final String CREDENTIALS_INVALID_MESSAGE = "員工編號或密碼不正確";

    private static final String FRONTDESK_WEB_CLIENT = "frontdesk_web";

    private final StaffAccountRepository staffAccountRepository;
    private final TenantRepository tenantRepository;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public StaffAccountLoginService(
            StaffAccountRepository staffAccountRepository,
            TenantRepository tenantRepository,
            TenantScopedExecutor tenantScopedExecutor,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.staffAccountRepository = staffAccountRepository;
        this.tenantRepository = tenantRepository;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public StaffLoginResponse login(StaffLoginRequest request) {
        validate(request);
        if (request.hotelCode() != null) {
            return loginAtHotel(request);
        }
        List<Long> candidates = tenantsHavingLoginId(request.loginId());
        if (candidates.isEmpty()) {
            throw new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE);
        }
        Long tenantId = candidates.size() == 1
                ? candidates.get(0)
                : firstTenantWithMatchingPassword(candidates, request).orElse(null);
        if (tenantId == null) {
            for (long candidate : candidates) {
                evaluateInTenant(candidate, request);
            }
            throw new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE);
        }
        Outcome outcome = evaluateInTenant(tenantId, request);
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.response();
    }

    private StaffLoginResponse loginAtHotel(StaffLoginRequest request) {
        long tenantId = Optional.of(request.hotelCode())
                .filter(HotelCode::isWellFormed)
                .flatMap(tenantRepository::findByCode)
                .map(Tenant::getId)
                .orElseThrow(() -> new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE));
        Outcome outcome = evaluateInTenant(tenantId, request);
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.response();
    }

    // TransactionTemplate 遇到往外拋的 RuntimeException 會整筆 rollback，失敗次數與鎖定就寫不進去，
    // 所以 evaluate() 回傳 Outcome，等交易 commit 之後才由呼叫端拋。
    private Outcome evaluateInTenant(long tenantId, StaffLoginRequest request) {
        return tenantScopedExecutor.callAsTenant(tenantId, () -> evaluate(tenantId, request));
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
        List<String> failedRules = NewPasswordStrength.failedRules(request.newPassword());
        if (!failedRules.isEmpty()) {
            throw new UnprocessableEntityException(
                    "new_password_too_weak",
                    "新密碼至少 8 碼，而且要有英文字母和數字",
                    Map.of("failedRules", failedRules));
        }
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new UnprocessableEntityException("new_password_invalid", "新密碼不能和目前密碼相同");
        }

        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setMustChangePassword(false);
        account.setUpdatedAt(OffsetDateTime.now());
        staffAccountRepository.save(account);

        return issueLoginResponse(tenantId, account);
    }

    private List<Long> tenantsHavingLoginId(String loginId) {
        return tenantScopedExecutor.tenantIds().stream()
                .sorted()
                .filter(tenantId -> tenantScopedExecutor.callAsTenant(
                        tenantId,
                        () -> staffAccountRepository.findByTenantIdAndLoginId(tenantId, loginId).isPresent()))
                .toList();
    }

    private Optional<Long> firstTenantWithMatchingPassword(List<Long> tenantIds, StaffLoginRequest request) {
        List<Long> matching = tenantIds.stream()
                .filter(tenantId -> accountInTenant(tenantId, request)
                        .map(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                        .orElse(false))
                .toList();
        return matching.stream()
                .filter(tenantId -> accountInTenant(tenantId, request)
                        .map(account -> canUseRequestedClient(request, account))
                        .orElse(false))
                .findFirst()
                .or(() -> matching.stream().findFirst());
    }

    private Optional<StaffAccount> accountInTenant(long tenantId, StaffLoginRequest request) {
        return tenantScopedExecutor.callAsTenant(
                tenantId, () -> staffAccountRepository.findByTenantIdAndLoginId(tenantId, request.loginId()));
    }

    private static boolean canUseRequestedClient(StaffLoginRequest request, StaffAccount account) {
        if (!FRONTDESK_WEB_CLIENT.equals(request.client())) {
            return true;
        }
        return account.isMustChangePassword() || RolePermissions.canUseFrontdeskWeb(account.getRole());
    }

    private Outcome evaluate(long tenantId, StaffLoginRequest request) {
        StaffAccount account = staffAccountRepository
                .findByTenantIdAndLoginId(tenantId, request.loginId())
                .orElse(null);
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

        if (!canUseRequestedClient(request, account)) {
            return Outcome.failure(new CredentialsInvalidException(CREDENTIALS_INVALID_MESSAGE));
        }

        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        staffAccountRepository.save(account);

        return Outcome.success(issueLoginResponse(tenantId, account));
    }

    private StaffLoginResponse issueLoginResponse(long tenantId, StaffAccount account) {
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
                account.getLoginId(),
                hotelName(tenantId),
                account.getDept(),
                account.getRole(),
                account.isMustChangePassword());
    }

    private String hotelName(long tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .orElseThrow(() -> new IllegalStateException("找不到飯店 " + tenantId));
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
