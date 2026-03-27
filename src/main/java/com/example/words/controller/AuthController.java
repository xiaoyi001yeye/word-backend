package com.example.words.controller;

import com.example.words.dto.LoginRequest;
import com.example.words.dto.LoginResponse;
import com.example.words.dto.QuoteResponse;
import com.example.words.dto.UserResponse;
import com.example.words.security.AuthCookieService;
import com.example.words.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        LoginResponse response = authService.login(request);
        String authCookie = authCookieService
                .createAuthCookie(response.getToken(), isSecureRequest(httpServletRequest))
                .toString();
        return ResponseEntity.ok()
                .header("Set-Cookie", authCookie)
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpServletRequest) {
        return ResponseEntity.noContent()
                .header("Set-Cookie", authCookieService.clearAuthCookie(isSecureRequest(httpServletRequest)).toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(authService.me());
    }

    @GetMapping("/quote")
    public ResponseEntity<QuoteResponse> quote() {
        return ResponseEntity.ok(authService.getLoginQuote());
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }
}
