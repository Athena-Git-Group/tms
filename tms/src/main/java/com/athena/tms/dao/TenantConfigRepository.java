package com.athena.tms.dao;

import com.athena.tms.entity.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantConfigRepository extends JpaRepository<TenantConfig, TenantConfig.Pk> {

    Optional<TenantConfig> findByTenantIdAndKey(Long tenantId, String key);

    List<TenantConfig> findByTenantId(Long tenantId);
}
