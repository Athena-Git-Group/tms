package com.athena.tms.dao;

import com.athena.tms.entity.SopItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SopItemRepository extends JpaRepository<SopItem, UUID> {

    /** 現行項目清單（建單抄快照、查房鍵集比對都從這裡出發）；已下架不混進來。 */
    List<SopItem> findByTenantIdAndAppliesToAndActiveTrueOrderBySortOrderAsc(Long tenantId, String appliesTo);

    Optional<SopItem> findByTenantIdAndAppliesToAndItemKey(Long tenantId, String appliesTo, String itemKey);
}
