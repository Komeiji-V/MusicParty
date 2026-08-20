package org.thornex.musicparty.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thornex.musicparty.config.AppProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
@Component
public class JwtUtil {

    /**
     * 已知的默认/示例密钥。REST 与 WebSocket 的过滤器都只做本地 JWT 验签，
     * 使用这些默认值部署时，任何人可伪造任意用户的 token（身份冒用），必须拒绝启动。
     */
    private static final Set<String> INSECURE_DEFAULT_SECRETS = Set.of(
            "musicparty-jwt-secret-change-in-production",
            "change-this-to-a-random-string",
            "CHANGE_ME_SAME_AS_AUTH_CENTER",
            "musicparty"
    );

    private final SecretKey secretKey;

    public JwtUtil(AppProperties appProperties) {
        String secret = appProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank() || INSECURE_DEFAULT_SECRETS.contains(secret)) {
            boolean allowInsecure = Boolean.parseBoolean(
                    System.getenv("ALLOW_INSECURE_JWT"));
            if (!allowInsecure) {
                throw new IllegalStateException(
                        "检测到默认或不安全的 JWT_SECRET，已拒绝启动：任何人可伪造用户身份。"
                                + "请设置与认证中心完全一致的 JWT_SECRET（生成方式：openssl rand -hex 32）。"
                                + "仅限本地/内网测试时可设置环境变量 ALLOW_INSECURE_JWT=true 跳过本检查。");
            }
            log.warn("!!! JWT_SECRET 为默认/不安全值且已显式放行（ALLOW_INSECURE_JWT=true），生产环境禁止如此部署 !!!");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUidFromToken(String token) {
        return validateToken(token).get("uid", Long.class);
    }

    public String getRoleFromToken(String token) {
        return validateToken(token).get("role", String.class);
    }
}
