package com.athena.tms.dto;

import java.util.List;

/**
 * `POST /admin/inspections/{taskId}` 的請求體。
 * items 的 key 對應 sop_items（applies_to='inspection'）的 item_key；
 * pass 用包裝型 Boolean 而非 primitive——primitive 會把「漏填」靜默補成
 * false，一個沒填的欄位就足以把整間房退回重掃，必須當 422 擋下。
 */
public record InspectionRequest(List<Item> items) {

    public record Item(String key, Boolean pass) {
    }
}
