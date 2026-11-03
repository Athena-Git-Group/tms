/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.athena.tms.dao;

import com.athena.tms.entity.ProcessedOp;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOpRepository
extends JpaRepository<ProcessedOp, ProcessedOp.Pk> {
    public Optional<ProcessedOp> findByTenantIdAndOpId(Long var1, String var2);
}
