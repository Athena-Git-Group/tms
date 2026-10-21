package com.athena.tms.common.error;

/**
 * 404：資源不存在。跨租戶存取一律走這裡（回 404 而非 403）——
 * 403 等於告訴攻擊者「資源存在只是你不能看」，404 不洩漏存在性。
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(404, "not_found", message);
    }
}
