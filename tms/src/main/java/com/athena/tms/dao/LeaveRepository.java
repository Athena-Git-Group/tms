package com.athena.tms.dao;

import com.athena.tms.entity.Leave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface LeaveRepository extends JpaRepository<Leave, UUID> {

    List<Leave> findByTenantIdAndStaffIdOrderByFromAtDesc(Long tenantId, UUID staffId);

    /**
     * 「這個人此刻在假中嗎」：from_at <= at < to_at（同一時點傳兩次）。
     * 排房與退回連動只需要布林答案，不用把整段假單撈回來。
     */
    boolean existsByTenantIdAndStaffIdAndFromAtLessThanEqualAndToAtGreaterThan(
            Long tenantId, UUID staffId, OffsetDateTime at, OffsetDateTime atAgain);
}
