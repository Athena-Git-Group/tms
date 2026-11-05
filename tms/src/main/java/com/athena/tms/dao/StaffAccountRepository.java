package com.athena.tms.dao;

import com.athena.tms.entity.StaffAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID> {

    Optional<StaffAccount> findByTenantIdAndId(Long tenantId, UUID id);

    Optional<StaffAccount> findByTenantIdAndLoginId(Long tenantId, String loginId);

    /**
     * 登入專屬：請求（contracts LoginRequest）沒有 tenantId，呼叫端要在不知道租戶的情況下
     * 用 loginId 找到帳號。配合 {@code TenantScopedExecutor} 逐租戶掃過去（見
     * {@code GuestScanService.resolveToken} 的既有先例），每次呼叫都已在對的租戶
     * context 下，這裡才不帶 tenantId 條件。
     */
    Optional<StaffAccount> findByLoginId(String loginId);

    List<StaffAccount> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
}
