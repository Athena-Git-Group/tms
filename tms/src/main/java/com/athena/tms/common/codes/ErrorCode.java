package com.athena.tms.common.codes;

/** API 錯誤回應的 code 值（contracts shared.yaml 的 Error.code）。 */
public final class ErrorCode {
    /** 驗收不通過卻沒有附原因，或原因去掉頭尾空白後是空的，回 422。 */
    public static final String VERIFICATION_REASON_REQUIRED = "verification_reason_required";

    private ErrorCode() {
    }
}
