package com.athena.tms.dto;

/**
 * contracts 員工帳號與登入.yaml 的 {@code ChangePasswordRequest}：本人設定自己的新密碼。
 * 明文密碼只用於這次比對與重新雜湊，不回傳也不落地存明文。
 *
 * <p>currentPassword 在臨時密碼登入後的強制改密情境可以省略（身分已由登入換得的權杖證明），
 * 自願變更密碼時必填。
 */
public record StaffChangePasswordRequest(String currentPassword, String newPassword) {}
