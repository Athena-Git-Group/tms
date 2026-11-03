package com.athena.tms.dto;

/**
 * 延後或中斷任務時附上的原因。可以是預設原因（例如「請勿打擾」）或自填文字；
 * 去掉頭尾空白後必須是 1 到 60 字，驗證在服務層做，不符回 422 reason_invalid。
 */
public record HoldReasonRequest(String reason) {
}
