package com.athena.tms.dao;

import com.athena.tms.entity.ProcessedOp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedOpRepository extends JpaRepository<ProcessedOp, ProcessedOp.Pk> {

    Optional<ProcessedOp> findByTenantIdAndOpId(Long tenantId, String opId);
}
