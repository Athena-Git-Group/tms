package com.athena.tms.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.athena.tms.dto.LoginRequest;
import com.athena.tms.dto.LoginResponse;
import com.athena.tms.service.AuthService;

/**
 * 員工登入端點，URL 照 spec 字面：`POST /auth/login`。
 * login 不要求 Idempotency-Key：重送拿到新 token 無副作用。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
