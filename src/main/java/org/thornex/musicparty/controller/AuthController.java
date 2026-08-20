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
    private final org.thornex.musicparty.config.AppProperties appProperties;

    private static final int AUTH_RATE_MAX = 30;        // 每 IP 每窗口最多请求数
    private static final long AUTH_RATE_WINDOW_MS = 60_000L;

    public AuthController(AuthService authService,
                          org.thornex.musicparty.util.IpRateLimiter ipRateLimiter,
                          org.thornex.musicparty.config.AppProperties appProperties) {
        this.authService = authService;
        this.ipRateLimiter = ipRateLimiter;
        this.appProperties = appProperties;
    }

    /**
     * SSO 交换（auth-center 登录页跨域 POST，配合 M1 新协议）：
     * - 来源校验：Origin（缺省时 Referer）必须命中 AUTH_CENTER_URL 解析出的 origin，
     *   且必须带 X-Requested-With: AuthCenter（防跨站 form POST 会话固定）
     * - 验签 + 确认 active + 本地 upsert 后，种 60s 一次性搬运 cookie（前端读取后清除）
     * 成功返回 { ok, user }（不再回传 token 到响应体，token 走 Set-Cookie 搬运）
     */
    @PostMapping("/sso")
    public ResponseEntity<?> sso(@RequestBody Map<String, String> body,
                                 jakarta.servlet.http.HttpServletRequest request,
                                 jakarta.servlet.http.HttpServletResponse response) {
        // 限流：SSO 交换按 IP 限流，防止被循环调用打爆认证中心
        if (!ipRateLimiter.allow(clientIp(request), AUTH_RATE_MAX, AUTH_RATE_WINDOW_MS)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "请求过于频繁，请稍后再试"));
        }
        // 1. 来源校验（CSRF 双重防护）
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            String referer = request.getHeader("Referer");
            origin = referer != null ? refererToOrigin(referer) : null;
        }
        String expectedOrigin = authCenterOrigin();
        if (origin == null || !origin.equals(expectedOrigin)) {
            log.warn("SSO rejected: origin mismatch (got {} expected {})", origin, expectedOrigin);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "非法来源"));
        }
        if (!"AuthCenter".equals(request.getHeader("X-Requested-With"))) {
            log.warn("SSO rejected: missing X-Requested-With: AuthCenter");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "非法来源"));
        }
        try {
            User user = authService.sso(body.get("token"));
            // 2. 种 60s 一次性搬运 cookie（前端读取后立即清除；最终凭证仍走 localStorage）
            jakarta.servlet.http.Cookie c = new jakarta.servlet.http.Cookie("music_sso_token", body.get("token"));
            c.setPath("/");
            c.setMaxAge(60);
            c.setHttpOnly(false); // 前端需读取
            c.setSecure(request.isSecure()); // 生产 https 自动 Secure；本地 http 不设
            c.setAttribute("SameSite", "Lax");
            response.addCookie(c);
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "user", Map.of(
                            "id", user.getId(),
                            "authUid", user.getAuthUid() != null ? user.getAuthUid() : 0L,
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

    /** 从配置的 AUTH_CENTER_URL 解析 origin（如 https://auth.komeijiv.cn） */
    private String authCenterOrigin() {
        String url = appProperties.getAuthCenter().getUrl();
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return null;
        }
    }

    /** Referer → origin（仅取 scheme://host[:port]） */
    private String refererToOrigin(String referer) {
        try {
            java.net.URI uri = java.net.URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return null;
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
                "authUid", user.getAuthUid() != null ? user.getAuthUid() : 0L,
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
