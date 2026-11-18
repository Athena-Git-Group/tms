package com.athena.tms.dto;

import com.athena.tms.service.GuestTenantConfigReader;
import java.util.List;

public record GuestMenuItemView(String key, String label) {
    public static List<GuestMenuItemView> fromMenu(List<GuestTenantConfigReader.MenuItem> menu) {
        return menu.stream().map(item -> new GuestMenuItemView(item.key(), item.label())).toList();
    }
}
