package org.thornex.musicparty.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.thornex.musicparty.config.AuthPrincipal;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final org.thornex.musicparty.util.IpRateLimiter ipRateLimiter;

    private static final int AUTH_RATE_MAX = 30;        // 每 IP 每窗口最多请求数
    private static final long AUTH_RATE_WINDOW_MS = 60_000L;

    public AuthController(AuthService authService, org.thornex.musicparty.util.IpRateLimiter ipRateLimiter) {
        this.authService = authService;
        this.ipRateLimiter = ipRateLimiter;
    }

    /**
     * SSO 交换：前端携带认证中心回跳的 access token 调用。
     * 成功返回 { token, user: { id, username, role } }
     */
    @PostMapping("/sso")
    public ResponseEntity<?> sso(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        // M7：SSO 交换按 IP 限流，防止被循环调用打爆认证中心
        if (!ipRateLimiter.allow(clientIp(request), AUTH_RATE_MAX, AUTH_RATE_WINDOW_MS)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "请求过于频繁，请稍后再试"));
        }
        try {
            User user = authService.sso(body.get("token"));
            return ResponseEntity.ok(Map.of(
                    "token", body.get("token"),
                    "user", Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "email", user.getEmail() != null ? user.getEmail() : "",
                            "emailVerified", user.getEmailVerified(),
                            "role", user.getRole().name()
                    )
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 刷新 token：转发认证中心 /api/refresh（轮换制）。
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        // M7：刷新按 IP 限流
        if (!ipRateLimiter.allow(clientIp(request), AUTH_RATE_MAX, AUTH_RATE_WINDOW_MS)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "请求过于频繁，请稍后再试"));
        }
        try {
            Map<String, Object> result = authService.refresh(body.get("refreshToken"));
            return ResponseEntity.ok(Map.of(
                    "token", result.get("token"),
                    "refreshToken", result.get("refresh_token"),
                    "expiresIn", result.get("expires_in")
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "已登出"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        AuthPrincipal principal = getCurrentPrincipal();
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录"));
        }
        User user = authService.getCurrentUser(principal.userId());
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "emailVerified", user.getEmailVerified(),
                "role", user.getRole().name()
        ));
    }

    private AuthPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        // 直连部署，不信任可伪造的 X-Forwarded-For
        return request.getRemoteAddr();
    }
}
