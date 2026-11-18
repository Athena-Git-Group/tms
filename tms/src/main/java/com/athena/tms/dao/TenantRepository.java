package com.athena.tms.dao;

import com.athena.tms.entity.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository
extends JpaRepository<Tenant, Long> {

    /** 用飯店識別代碼找飯店。比對一字不差、分大小寫，帶大寫或前後空白的寫法都找不到。 */
    Optional<Tenant> findByCode(String code);
}
