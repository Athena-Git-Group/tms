package com.athena.tms.dao;

import com.athena.tms.entity.Leave;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRepository
extends JpaRepository<Leave, UUID> {
    public List<Leave> findByTenantIdAndStaffIdOrderByFromAtDesc(Long var1, UUID var2);

    public boolean existsByTenantIdAndStaffIdAndFromAtLessThanEqualAndToAtGreaterThan(Long var1, UUID var2, OffsetDateTime var3, OffsetDateTime var4);
}
