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

    public AuthService(UserRepository userRepository,
                       JwtUtil jwtUtil,
                       AuthCenterClient authCenterClient) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.authCenterClient = authCenterClient;
    }

    /**
     * SSO 交换：验证认证中心 access token，建立本地会话。
     * 返回 { token(原样), user } 或抛异常。
     */
    @Transactional
    public User sso(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("缺少登录凭证");
        }

        // 1. 本地验签（共享 JWT_SECRET，HS256）
        Long uid;
        try {
            uid = jwtUtil.getUidFromToken(accessToken);
        } catch (Exception e) {
            log.warn("Invalid token signature: {}", e.getMessage());
            throw new RuntimeException("登录凭证无效或已过期");
        }

        // 2. 调认证中心确认 token 有效 + 获取最新用户信息
        Map<String, Object> info = authCenterClient.verifyToken(accessToken);
        if (info == null) {
            throw new RuntimeException("登录凭证无效或已过期，请重新登录");
        }
        Object idObj = info.get("id");
        Long infoUid = idObj instanceof Number n ? n.longValue() : uid;
        if (!infoUid.equals(uid)) {
            throw new RuntimeException("登录凭证不一致");
        }
        String username = String.valueOf(info.get("username"));
        String email = info.get("email") == null ? null : String.valueOf(info.get("email"));
        String authRole = String.valueOf(info.get("role"));

        // 3. 本地 upsert（uid 关联，认证中心角色 admin -> SUPER_ADMIN）
        User user = userRepository.findByAuthUid(uid).orElse(null);
        if (user == null) {
            user = User.builder()
                    .username(username)
                    .authUid(uid)
                    .email(email)
                    .emailVerified(true)
                    .role(mapRole(authRole))
                    .build();
        } else {
            user.setUsername(username);
            user.setEmail(email);
            user.setEmailVerified(true);
            user.setRole(mapRole(authRole));
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

    private User.UserRole mapRole(String authRole) {
        return "admin".equalsIgnoreCase(authRole) ? User.UserRole.SUPER_ADMIN : User.UserRole.USER;
    }
}
