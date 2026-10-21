package com.athena.tms.dto;

import com.athena.tms.service.GuestTenantConfigReader.InfoPage;

import java.util.List;

/**
 * 資訊頁回應。與 ScanResponse 分開而不是共用：home 是「重讀設定」
 * 的唯讀動作，不帶 sessionToken/expiresAt——把簽發面的欄位混進來，前端就會
 * 有兩個地方都可能更新 session 的錯覺。
 */
public record GuestHomeView(List<InfoPage> infoPages, List<GuestMenuItemView> menu) {
}
