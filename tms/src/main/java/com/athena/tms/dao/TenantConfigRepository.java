/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.athena.tms.dao;

import com.athena.tms.entity.TenantConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantConfigRepository
extends JpaRepository<TenantConfig, TenantConfig.Pk> {
    public Optional<TenantConfig> findByTenantIdAndKey(Long var1, String var2);

    public List<TenantConfig> findByTenantId(Long var1);
}
