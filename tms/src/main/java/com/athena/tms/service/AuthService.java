package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.UnauthorizedException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.entity.StaffAccount;
import com.athena.tms.entity.StaffDevice;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dao.StaffDeviceRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.LoginRequest;
import com.athena.tms.dto.LoginResponse;

/**
 * 員工登入：驗帳密（bcrypt）→ 核發 JWT → 同交易登記推播裝置。
 * 錯帳號、錯密碼、停用帳號回同一句 401——區分訊息等於幫攻擊者枚舉
 * 哪些 login_id 存在。
 */
@Service
public class AuthService {

    private final StaffAccountRepository staffAccountRepository;
    private final StaffDeviceRepository staffDeviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(StaffAccountRepository staffAccountRepository,
                       StaffDeviceRepository staffDeviceRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffDeviceRepository = staffDeviceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 刻意不掛 @Transactional：Hibernate 在交易開始（關 autocommit）就取連線，
     * 早於下面的 TenantContext.set——RLS 變數會以「無租戶」注入，帳號查詢
     * 永遠零列、登入必敗。讓每個 repository 呼叫各走短交易，連線都在
     * set 之後才取。本方法唯一寫入是單筆裝置 save，不需要跨語句原子性。
     */
    public LoginResponse login(LoginRequest request) {
        validate(request);
        // 登入是「尚未有身分」的路徑，租戶脈絡只能取自請求體；不先 set，
        // tms_app 受 RLS 管的 staff_accounts 查詢必然零列、登入永遠失敗
        TenantContext.set(request.tenantId());

        StaffAccount account = staffAccountRepository
                .findByTenantIdAndLoginId(request.tenantId(), request.loginId())
                .orElse(null);
        if (account == null
                || !account.isActive()
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new UnauthorizedException("帳號或密碼錯誤，或帳號已停用");
        }

        registerDevice(request, account);

        JwtService.IssuedToken issued = jwtService.issue(
                account.getTenantId(), account.getId(), account.getRole(), account.getDept());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(),
                new LoginResponse.Staff(account.getId(), account.getTenantId(),
                        account.getName(), account.getRole(), account.getDept()));
    }

    /**
     * 登記 staff_devices 推播裝置，(tenant_id, staff_id, device_fingerprint) 去重
     * ——同一台手機重複登入只留一列，DB 唯一約束兜底併發競態。
     * 登入不綁裝置：沒帶 fingerprint 照樣登入，只是收不到推播。
     */
    private void registerDevice(LoginRequest request, StaffAccount account) {
        String fingerprint = request.deviceFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            return;
        }
        StaffDevice device = staffDeviceRepository
                .findByTenantIdAndStaffIdAndDeviceFingerprint(
                        account.getTenantId(), account.getId(), fingerprint)
                .orElseGet(() -> {
                    StaffDevice created = new StaffDevice();
                    created.setId(UuidV7.generate());
                    created.setTenantId(account.getTenantId());
                    created.setStaffId(account.getId());
                    created.setDeviceFingerprint(fingerprint);
                    created.setRegisteredAt(OffsetDateTime.now());
                    return created;
                });
        // revoked 不清掉的話，登出過的裝置重新登入後仍收不到推播——
        // 能通過帳密驗證即證明裝置回到本人手上，撤銷標記已無保護意義
        device.setRevokedAt(null);
        if (request.pushToken() != null && !request.pushToken().isBlank()) {
            device.setPushToken(request.pushToken());
        }
        staffDeviceRepository.save(device);
    }

    /** 手動驗而不用 @Valid：pom 沒有 validation starter，@Valid 會靜默不生效比缺依賴更危險。 */
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
