package com.athena.tms.dao;

import com.athena.tms.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository
extends JpaRepository<Tenant, Long> {
}
