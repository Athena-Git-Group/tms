/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.springframework.security.access.AccessDeniedException
 *  org.springframework.security.core.AuthenticationException
 *  org.springframework.security.web.AuthenticationEntryPoint
 *  org.springframework.security.web.access.AccessDeniedHandler
 *  org.springframework.stereotype.Component
 *  tools.jackson.databind.ObjectMapper
 */
package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiErrorResponses
implements AuthenticationEntryPoint,
AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public ApiErrorResponses(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        this.write(response, 401, "unauthorized", "\u7f3a\u5c11\u6709\u6548\u6191\u8b49");
    }

    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        this.write(response, 403, "forbidden", "\u6b64\u89d2\u8272\u4e0d\u5141\u8a31\u547c\u53eb\u6b64\u52d5\u4f5c");
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        this.objectMapper.writeValue((Writer)response.getWriter(), (Object)ApiError.of(code, message));
    }
}
