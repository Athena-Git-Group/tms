/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.data.jpa.repository.JpaRepository
 */
package com.athena.tms.dao;

import com.athena.tms.entity.SopItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SopItemRepository
extends JpaRepository<SopItem, UUID> {
    public List<SopItem> findByTenantIdAndAppliesToAndActiveTrueOrderBySortOrderAsc(Long var1, String var2);

    public Optional<SopItem> findByTenantIdAndAppliesToAndItemKey(Long var1, String var2, String var3);
}
