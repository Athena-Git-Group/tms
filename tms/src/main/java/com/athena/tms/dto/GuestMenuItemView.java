package com.athena.tms.dto;

import com.athena.tms.service.GuestTenantConfigReader.MenuItem;

import java.util.List;

/**
 * 選單的住客可見面。刻意不含 dept/type：那是建單路由的內部事實，住客端
 * 拿到了也沒用，還把「哪種需求歸哪個部門」的內規暴露給任何掃到碼的人。
 */
public record GuestMenuItemView(String key, String label) {

    public static List<GuestMenuItemView> fromMenu(List<MenuItem> menu) {
        return menu.stream().map(item -> new GuestMenuItemView(item.key(), item.label())).toList();
    }
}
