/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.athena.tms.dao;

import com.athena.tms.entity.StaffAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository
extends JpaRepository<StaffAccount, UUID> {
    public Optional<StaffAccount> findByTenantIdAndId(Long var1, UUID var2);

    public Optional<StaffAccount> findByTenantIdAndLoginId(Long var1, String var2);

    public List<StaffAccount> findByTenantIdAndDeptAndActiveTrue(Long var1, String var2);
}
