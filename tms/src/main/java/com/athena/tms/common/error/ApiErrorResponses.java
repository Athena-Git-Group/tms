package com.athena.tms.common.error;

// Jackson 3（Boot 4 預設）在 tools.jackson；com.fasterxml 的 2.x 也在 classpath 但沒有 bean
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security filter 層的 401/403 出口。GlobalExceptionHandler 只接得到進了 MVC
 * 的例外，授權在 filter 就擋下的請求若不在這裡接手，會回 Spring Security 的
 * 預設空白回應——錯誤格式就裂成兩套。這裡複用 common 的 ApiError，
 * 讓 401/403 與業務錯誤長得一模一樣。
 */
@Component
public class ApiErrorResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiErrorResponses(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 未帶憑證（或匿名）打到需要身分的路徑 → 401。 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, 401, "unauthorized", "缺少有效憑證");
    }

    /** 身分有效但角色×動作矩陣不放行 → 403；已列前綴之外也一律 403。 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, 403, "forbidden", "此角色不允許呼叫此動作");
    }

    public void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiError.of(code, message));
    }
}
