package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dao.SopItemRepository;
import com.athena.tms.entity.SopItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SopItemController {

    private final SopItemRepository sopItemRepository;

    public SopItemController(SopItemRepository sopItemRepository) {
        this.sopItemRepository = sopItemRepository;
    }

    @GetMapping("/sop-items")
    public List<Map<String, Object>> listSopItems() {
        long tenantId = TenantContext.require();
        List<SopItem> items = sopItemRepository.findByTenantIdOrderByAppliesToAscSortOrderAscItemKeyAsc(tenantId);
        List<Map<String, Object>> body = new ArrayList<>(items.size());
        for (SopItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId() == null ? null : item.getId().toString());
            row.put("tenantId", item.getTenantId());
            row.put("appliesTo", item.getAppliesTo());
            row.put("itemKey", item.getItemKey());
            row.put("label", item.getLabel());
            row.put("sortOrder", item.getSortOrder());
            row.put("active", item.isActive());
            row.put("createdAt", item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
            body.add(row);
        }
        return body;
    }
}
