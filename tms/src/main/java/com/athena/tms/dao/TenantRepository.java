package com.athena.tms.dao;

import com.athena.tms.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

/** 平台層主檔，無 tenant scope（本表就是租戶本身）；tms_app 只讀。 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
