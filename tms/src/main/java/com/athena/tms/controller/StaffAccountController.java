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

    /** 回應形狀取 contracts staff_accounts schema 的公開欄位，不含 password_hash 與 login_id。 */
    public record StaffAccountView(UUID id, String name, String role, String dept, boolean active) {}

    /**
     * 只回在職（active=true）的員工：這份名單給「選人」的場景用（例如挑查房者、派負責人），
     * 停用帳號若還列在裡面，就可能被選成操作者或收單人。回應不帶登入憑證相關欄位。
     */
    @GetMapping("/staff-accounts")
    public List<StaffAccountView> list() {
        long tenantId = TenantContext.require();
        return staffAccountRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(sa -> new StaffAccountView(sa.getId(), sa.getName(), sa.getRole(), sa.getDept(), sa.isActive()))
                .toList();
    }

    /** 登入前還沒有身分，不帶任何認證 header（SecurityConfig 對這條路徑 permitAll）。 */
    @PostMapping("/staff-accounts/login")
    public StaffLoginResponse login(@RequestBody StaffLoginRequest request) {
        return staffAccountLoginService.login(request);
    }

    /**
     * 本人設定自己的新密碼（contracts changeOwnPassword）。回應形狀與登入相同，帶換發的
     * 權杖，App 直接拿它進首頁。
     *
     * <p>臨時密碼登入後被強制改密的員工，其他需要身分的 API 一律被拒，只有這條放行
     * （見 {@code BearerTokenAuthFilter}）——擋住這條，員工就卡死在改密畫面出不來。
     */
    @PostMapping("/staff-accounts/change-password")
    public StaffLoginResponse changePassword(@RequestBody StaffChangePasswordRequest request) {
        CurrentActor actor = CurrentActor.require();
        return staffAccountLoginService.changePassword(TenantContext.require(), actor.staffId(), request);
    }
}
