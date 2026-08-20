package org.thornex.musicparty.util;

import org.springframework.stereotype.Component;
import org.thornex.musicparty.config.AppProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 媒体缓存签名：为 /media/** 静态资源生成短时 HMAC 签名 URL（防未授权直接下载缓存音频）。
 * 签名内容 = {文件相对路径}:{过期时间戳}，密钥复用 JWT_SECRET（同一信任域）。
 */
@Component
public class MediaSigner {

    private static final long DEFAULT_TTL_MS = 30 * 60 * 1000L; // 30 分钟

    private final byte[] keyBytes;

    public MediaSigner(AppProperties appProperties) {
        String secret = appProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置，无法初始化媒体签名（启动应已被 JwtUtil 拦截）");
        }
        this.keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 为指定缓存文件生成带签名与过期时间的完整 URL（如 /media/abc.m4a?exp=...&sig=...） */
    public String signUrl(String mediaPath) {
        long exp = System.currentTimeMillis() + DEFAULT_TTL_MS;
        String sig = sign(mediaPath, exp);
        return mediaPath + "?exp=" + exp + "&sig=" + sig;
    }

    /** 校验签名与过期时间 */
    public boolean verify(String mediaPath, String expStr, String sig) {
        if (mediaPath == null || expStr == null || sig == null) return false;
        long exp;
        try {
            exp = Long.parseLong(expStr);
        } catch (NumberFormatException e) {
            return false;
        }
        if (System.currentTimeMillis() > exp) return false;
        String expected = sign(mediaPath, exp);
        return constantTimeEquals(expected, sig);
    }

    private String sign(String mediaPath, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] raw = mac.doFinal((mediaPath + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
