package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dao.StaffAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 員工帳號查詢 API，對應 contracts 員工帳號與登入.yaml 的 listStaffAccounts
 * （GET /staff-accounts）。只回在職（active=true）的員工：這份名單給「選人」
 * 的場景用（例如挑查房者、派負責人），停用帳號若還列在裡面，就可能被選成
 * 操作者或收單人。回應不帶登入憑證相關欄位。
 */
@RestController
@RequestMapping("/api/v1")
public class StaffAccountController {

    private final StaffAccountRepository staffAccountRepository;

    public StaffAccountController(StaffAccountRepository staffAccountRepository) {
        this.staffAccountRepository = staffAccountRepository;
    }

    /** 回應形狀取 contracts staff_accounts schema 的公開欄位，不含 password_hash 與 login_id。 */
    public record StaffAccountView(UUID id, String name, String role, String dept, boolean active) {}

    @GetMapping("/staff-accounts")
    public List<StaffAccountView> list() {
        long tenantId = TenantContext.require();
        return staffAccountRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId).stream()
                .map(sa -> new StaffAccountView(sa.getId(), sa.getName(), sa.getRole(), sa.getDept(), sa.isActive()))
                .toList();
    }
}
