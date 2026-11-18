package com.athena.tms.dao;

import com.athena.tms.entity.Inspection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository
extends JpaRepository<Inspection, UUID> {
    public List<Inspection> findByTenantIdAndTaskIdOrderByCreatedAtDesc(Long var1, UUID var2);
}
