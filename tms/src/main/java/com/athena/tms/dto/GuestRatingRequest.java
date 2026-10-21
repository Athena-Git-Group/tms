package com.athena.tms.dto;

/**
 * 評分請求。rating 用包裝型別：缺欄位要走 422（客戶端 bug），
 * primitive int 會把「沒給」靜默變成 0、再被誤報成「0 不在 1–5」。
 */
public record GuestRatingRequest(Integer rating) {
}
