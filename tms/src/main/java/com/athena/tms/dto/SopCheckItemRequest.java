package com.athena.tms.dto;

import java.util.UUID;

/**
 * 員工 App 勾記或跳過一個 SOP 項時送的請求（contracts tasksSopCheck）。
 * 以 SOP 項的 id 定位，而不是 item_key——id 是 {@code sop_items} 的主鍵，
 * item_key 只在同一個 applies_to 裡唯一，單靠它定位還要再帶一個種類才夠。
 */
public record SopCheckItemRequest(UUID sopItemId, String result) {}
