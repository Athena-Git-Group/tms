package com.athena.tms.dao;

import com.athena.tms.entity.StaffAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID> {

    Optional<StaffAccount> findByTenantIdAndId(Long tenantId, UUID id);

    Optional<StaffAccount> findByTenantIdAndLoginId(Long tenantId, String loginId);

    List<StaffAccount> findByTenantIdAndActiveTrueOrderByNameAsc(Long tenantId);
}
