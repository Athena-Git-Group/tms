package com.athena.tms.dao;

import com.athena.tms.entity.StaffAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 查詢一律帶 tenant 參數（應用層 scope 第一道牆；RLS 是第二道）。
 * 以 id 取單筆也走 findByTenantIdAndId——跨租戶 id 直接查無，上層回 404。
 */
public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID> {

    Optional<StaffAccount> findByTenantIdAndId(Long tenantId, UUID id);

    Optional<StaffAccount> findByTenantIdAndLoginId(Long tenantId, String loginId);

    List<StaffAccount> findByTenantIdAndDeptAndActiveTrue(Long tenantId, String dept);
}
