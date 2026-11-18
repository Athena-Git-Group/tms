package com.athena.tms.dao;

import com.athena.tms.entity.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository
extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCode(String code);
}
