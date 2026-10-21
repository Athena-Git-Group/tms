package com.athena.tms.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 錯誤回應格式，照 specs/tms-poc-to-backend/api/openapi.yaml 的 Error schema：
 * code（機器可讀，如 state_conflict / not_found）＋message（給人看）＋
 * details（欄位層級明細，非驗證錯誤時省略）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<Detail> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public record Detail(String field, String issue) {
    }
}
