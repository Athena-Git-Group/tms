package com.athena.tms.dto;

/**
 * contracts 員工帳號與登入.yaml 的 {@code LoginRequest}：登入專屬請求形狀，與「建立員工帳號」
 * 的 {@code staff_accountsRequest} 分開——明文密碼只用於登入比對，不落地存儲。
 *
 * <p>和既有 {@link LoginRequest}（{@code /auth/login}，需要呼叫端自己帶 tenantId）是兩套
 * 平行的登入端點：這個類別對應新契約 {@code staffAccountsLogin}（{@code POST
 * /staff-accounts/login}），請求不帶 tenantId，tenantId 由後端依 loginId 逐租戶找出來
 * （見 {@code StaffAccountLoginService}）。
 */
public record StaffLoginRequest(String loginId, String password) {}
