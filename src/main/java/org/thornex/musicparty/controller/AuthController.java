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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * SSO 交换：前端携带认证中心回跳的 access token 调用。
     * 成功返回 { token, user: { id, username, role } }
     */
    @PostMapping("/sso")
    public ResponseEntity<?> sso(@RequestBody Map<String, String> body) {
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
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
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
}
