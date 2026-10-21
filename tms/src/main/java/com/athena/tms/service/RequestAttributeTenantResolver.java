package com.athena.tms.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 租戶唯一來源是認證層寫入的 request attribute（BearerTokenAuthFilter 驗完
 * 憑證後以 TenantContext 的值設入）。不提供 header 之類的退路：header
 * 誰都能偽造。沒有已驗證身分的請求（如 /auth/login、/guest/scan）不做
 * 冪等，由上游放行。
 */
@Component
public class RequestAttributeTenantResolver implements IdempotencyTenantResolver {

    @Override
    public Optional<Long> resolveTenantId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TENANT_ATTRIBUTE);
        if (attribute instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (attribute instanceof String text && !text.isBlank()) {
            return parse(text);
        }
        return Optional.empty();
    }

    private static Optional<Long> parse(String value) {
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            // 認證層只會放 Long，走到這裡是程式錯誤；但冪等層的語意是「解析不到
            // 就不做冪等」，不讓整條請求炸 500
            return Optional.empty();
        }
    }
}
