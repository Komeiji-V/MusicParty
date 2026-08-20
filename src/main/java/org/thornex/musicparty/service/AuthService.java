package org.thornex.musicparty.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.util.JwtUtil;

import java.util.Map;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AuthCenterClient authCenterClient;
    private final org.thornex.musicparty.config.AppProperties appProperties;

    public AuthService(UserRepository userRepository,
                       JwtUtil jwtUtil,
                       AuthCenterClient authCenterClient,
                       org.thornex.musicparty.config.AppProperties appProperties) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.authCenterClient = authCenterClient;
        this.appProperties = appProperties;
    }

    /**
     * SSO 交换：验证认证中心 access token，建立本地会话。
     * 返回 { token(原样), user } 或抛异常。
     *
     * 安全说明（H2/M1/M7）：
     * - 身份与角色均取自【已验签 token】的 claim（H2：不再信任明文 userinfo 的 role，防中间人/伪造响应提权）
     * - userinfo 仅用于确认"token 有效 + uid 一致"（M1：id 缺失/非数字/不一致一律拒绝，无回退分支）
     * - SUPER_ADMIN 受环境变量白名单约束 + 角色变更审计（M7）
     */
    @Transactional
    public User sso(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("缺少登录凭证");
        }

        // 1. 本地验签（共享 JWT_SECRET，HS256）——身份与角色来源
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtUtil.validateToken(accessToken);
        } catch (Exception e) {
            log.warn("Invalid token signature: {}", e.getMessage());
            throw new RuntimeException("登录凭证无效或已过期");
        }
        Long uid = claims.get("uid", Long.class);
        String tokenRole = claims.get("role", String.class);
        if (uid == null) {
            throw new RuntimeException("登录凭证无效或已过期");
        }

        // 2. 调认证中心确认 token 有效 + uid 一致（M1：id 必须存在、为 Number、且 == token uid，无回退）
        Map<String, Object> info = authCenterClient.verifyToken(accessToken);
        if (info == null) {
            throw new RuntimeException("登录凭证无效或已过期，请重新登录");
        }
        Object idObj = info.get("id");
        if (!(idObj instanceof Number n) || n.longValue() != uid) {
            throw new RuntimeException("登录凭证不一致");
        }
        String username = String.valueOf(info.get("username"));
        String email = info.get("email") == null ? null : String.valueOf(info.get("email"));

        // 3. 角色：来自已验签 token（auth-center 已把 role 签进 claim）；
        //    SUPER_ADMIN_AUTH_UIDS 白名单非空时，仅白名单内 uid 可成为 SUPER_ADMIN（M7）
        User.UserRole newRole = mapRole(tokenRole, uid);

        // 4. 本地 upsert（uid 关联）
        User user = userRepository.findByAuthUid(uid).orElse(null);
        if (user == null) {
            user = User.builder()
                    .username(username)
                    .authUid(uid)
                    .email(email)
                    .emailVerified(true)
                    .role(newRole)
                    .build();
        } else {
            // M7：角色变更审计
            if (user.getRole() != newRole) {
                log.warn("[SECURITY-AUDIT] Role change for uid={} username={}: {} -> {}",
                        uid, username, user.getRole(), newRole);
            }
            user.setUsername(username);
            user.setEmail(email);
            user.setEmailVerified(true);
            user.setRole(newRole);
        }
        userRepository.save(user);
        log.info("SSO login: uid={} username={} role={}", uid, username, user.getRole());
        return user;
    }

    /**
     * 刷新：转发认证中心 /api/refresh（轮换制），返回新 access token。
     */
    public Map<String, Object> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("缺少刷新凭证");
        }
        Map<String, Object> result = authCenterClient.refreshToken(refreshToken);
        if (result == null || !Boolean.TRUE.equals(result.get("ok")) || result.get("token") == null) {
            throw new RuntimeException("登录已过期，请重新登录");
        }
        return result;
    }

    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    /**
     * 角色映射（M7）：token 的 role claim "admin" → SUPER_ADMIN，
     * 但受 SUPER_ADMIN_AUTH_UIDS 白名单约束（非空时仅白名单内 uid 有效）。
     */
    private User.UserRole mapRole(String tokenRole, Long uid) {
        boolean wantsAdmin = "admin".equalsIgnoreCase(tokenRole);
        if (!wantsAdmin) {
            return User.UserRole.USER;
        }
        String whitelist = appProperties.getSuperAdminAuthUids();
        if (whitelist != null && !whitelist.isBlank()) {
            boolean allowed = java.util.Arrays.stream(whitelist.split(","))
                    .map(String::trim)
                    .anyMatch(s -> s.equals(String.valueOf(uid)));
            if (!allowed) {
                log.warn("[SECURITY-AUDIT] uid={} claims admin role but is NOT in SUPER_ADMIN_AUTH_UIDS whitelist; downgraded to USER", uid);
                return User.UserRole.USER;
            }
        }
        return User.UserRole.SUPER_ADMIN;
    }
}
