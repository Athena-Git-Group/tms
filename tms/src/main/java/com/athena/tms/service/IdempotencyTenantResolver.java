package com.athena.tms.service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 冪等簿以 (tenant_id, op_id) 為鍵，filter 必須先知道請求屬於哪個租戶。
 * 抽成介面是為了跟認證模組解耦：auth filter
 * 以「驗證過的身分」提供租戶（覆寫 bean 或寫入 request attribute 皆可），
 * 本模組不須改碼。
 */
public interface IdempotencyTenantResolver {

    /** auth filter 驗完 JWT 後，把租戶 id 放進這個 request attribute 即可接上。 */
    String TENANT_ATTRIBUTE = "tms.tenant_id";

    Optional<Long> resolveTenantId(HttpServletRequest request);
}
