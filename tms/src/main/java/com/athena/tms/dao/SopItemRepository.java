package com.athena.tms.dao;

import com.athena.tms.entity.SopItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SopItemRepository
extends JpaRepository<SopItem, UUID> {
    public List<SopItem> findByTenantIdAndAppliesToAndActiveTrueOrderBySortOrderAsc(Long var1, String var2);

    /**
     * 列出一個租戶的全部 SOP／查房項目（contracts listSopItems）。
     *
     * <p>不濾 {@code active}：契約的 {@code sop_items} 把 {@code active} 列為必填輸出欄位，
     * 又沒有任何查詢參數，代表停用的項目也要列出來、由呼叫端自己看旗標決定怎麼呈現；
     * 在這裡先濾掉就等於把契約的欄位變成永遠為 true 的擺設。
     * 排序以同一種類內的 {@code sortOrder} 為主，{@code itemKey} 只是並列時的決勝鍵，
     * 讓同一份資料每次回來的順序固定。
     */
    public List<SopItem> findByTenantIdOrderByAppliesToAscSortOrderAscItemKeyAsc(Long var1);

    public Optional<SopItem> findByTenantIdAndAppliesToAndItemKey(Long var1, String var2, String var3);
}
