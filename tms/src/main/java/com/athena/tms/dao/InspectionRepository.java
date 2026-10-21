package com.athena.tms.dao;

import com.athena.tms.entity.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    /** 退回重查會有多列，新查在前；「最後一次查房結果」就是第一筆。 */
    List<Inspection> findByTenantIdAndTaskIdOrderByCreatedAtDesc(Long tenantId, UUID taskId);
}
