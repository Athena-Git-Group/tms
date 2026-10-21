package com.athena.tms.dto;

/**
 * 建單請求：itemId（勾選單）與 text（自由文字）二選一，兩者都給
 * 代表客戶端搞混了兩條路徑，收下任一個都是猜——一律 422 退回。
 * lang 只在自由文字路徑有意義（翻譯輔助的輸入），選單項沒有語言問題。
 */
public record GuestRequestCreate(String itemId, String text, String lang) {
}
