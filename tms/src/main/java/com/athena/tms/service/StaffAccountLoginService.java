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

/**
 * contracts staffAccountsLogin（{@code POST /staff-accounts/login}）：員工編號＋明文密碼
 * 登入，後端雜湊比對，換發 JWT。密碼錯誤或員工編號不存在一律回同一句文案，不洩漏員工編號
 * 是否存在；連續輸入錯誤密碼達 5 次鎖定 15 分鐘；停用帳號一律被拒；臨時密碼登入成功仍換
 * 發憑證，但回應標示 {@code mustChangePassword}，之後其他需要身分的 API 一律被拒
 * （{@code BearerTokenAuthFilter} 負責擋）。
 *
 * <p>{@code LoginRequest} 沒有 tenantId（登入前還不知道租戶），用 {@link
 * TenantScopedExecutor} 逐租戶掃過去找 loginId，找到之後才在那個租戶的 context 下做
 * 後續的狀態檢查與落地——這是既有 {@code GuestScanService.resolveToken}（住客掃碼也是
 * 登入前不知道租戶）的同一套先例，不是本類別自己發明的做法。
 */
@Service
public class StaffAccountLoginService {

    /** dsl.md「連續輸入錯誤密碼達 5 次時帳號鎖定 15 分鐘」。 */
    private static final int LOCKOUT_THRESHOLD = 5;

    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    /** 員工編號或密碼不正確、帳號不存在共用的固定文案（不洩漏員工編號是否存在）。 */
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
        // evaluate() 回傳 Outcome 而不是直接拋例外：失敗次數累加／鎖定的落地要跟著
        // tenantScopedExecutor.callAsTenant 開的交易一起 commit；若在交易還開著時就
        // 往外拋 RuntimeException，TransactionTemplate 會整筆 rollback，剛寫的
        // failed_login_count／locked_until 就白寫了。交易結束（已 commit）之後
        // 才在這裡決定要不要拋。
        Outcome outcome = tenantScopedExecutor.callAsTenant(tenantId, () -> evaluate(tenantId, request));
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.response();
    }

    /**
     * contracts changeOwnPassword（{@code POST /staff-accounts/change-password}）：本人把自己的
     * 密碼換成新的一組，後端重新雜湊落地、清掉強制改密旗標，並換發一組新的權杖回去，App 直接
     * 拿它進首頁，不必再登入一次。
     *
     * <p>臨時密碼登入後的強制改密情境不要求帶目前密碼，身分已由登入換得的權杖證明；自願變更
     * 密碼時必須帶，省略回 422 current_password_required，比對不符回 401
     * current_password_invalid。新密碼與目前密碼相同回 422 new_password_invalid。
     *
     * <p>密碼強度（長度下限、字元組成）在 truth 還沒有定義，這裡不自行發明門檻。
     */
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

    /** 找不到任何租戶有這個 loginId 時，跟密碼錯誤回同一個例外，不洩漏員工編號是否存在。 */
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

        // 登入成功：清零失敗次數、解除鎖定（即使剛好鎖定到期後第一次登入成功，也順手清掉）。
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

    /** login() 交易內的計算結果：成功回應或待拋例外，二擇一，見 {@link #login}。 */
    private record Outcome(StaffLoginResponse response, RuntimeException failure) {
        static Outcome success(StaffLoginResponse response) {
            return new Outcome(response, null);
        }

        static Outcome failure(RuntimeException failure) {
            return new Outcome(null, failure);
        }
    }

    /** 累加失敗次數；達門檻（5 次）鎖定 15 分鐘，未達門檻維持未鎖定。 */
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
