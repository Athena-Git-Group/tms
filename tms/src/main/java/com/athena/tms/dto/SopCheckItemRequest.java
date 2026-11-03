package com.athena.tms.dto;

import java.util.UUID;

/**
 * 員工 App 勾記或跳過一個 SOP 項時送的請求（contracts tasksSopCheck）。
 * 既有的 {@link SopCheckRequest} 是舊 /staff 路由以 itemKey 定位的形狀，
 * contracts 拍板的新介面改以 SOP 項的 id 定位，所以另立一個 DTO，不動舊路由。
 */
public record SopCheckItemRequest(UUID sopItemId, String result) {
}
