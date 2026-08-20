package org.thornex.musicparty.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thornex.musicparty.config.AppProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加密（M4）：AES-256-GCM。
 * 密钥优先取环境变量 COOKIE_ENCRYPTION_KEY（生产必须独立设置，与 JWT_SECRET 分离）；
 * 未设置时回退 JWT_SECRET 经 SHA-256 派生（仅限本地/测试，启动打告警）。
 * 密文格式：enc:v1:{Base64(iv + ciphertext)}；decrypt 兼容旧格式 enc:（无版本）与无前缀明文（存量数据）。
 */
@Slf4j
@Component
public class CryptoUtil {

    private static final String PREFIX = "enc:v1:";
    private static final String LEGACY_PREFIX = "enc:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoUtil(AppProperties appProperties) {
        String envKey = System.getenv("COOKIE_ENCRYPTION_KEY");
        String secret;
        if (envKey != null && !envKey.isBlank()) {
            secret = envKey;
        } else {
            secret = appProperties.getJwt().getSecret();
            if (secret != null && !secret.isBlank()) {
                log.warn("COOKIE_ENCRYPTION_KEY 未设置，Cookie 加密回退使用 JWT_SECRET 派生密钥（生产环境请设置独立密钥）");
            }
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置，无法初始化加密组件");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("无法派生加密密钥", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return plain;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String data) {
        if (data == null || data.isBlank()) return data;
        // 兼容旧格式 enc:（同一密钥）与无前缀明文（存量数据，下次写入会加密）
        String payload = null;
        if (data.startsWith(PREFIX)) {
            payload = data.substring(PREFIX.length());
        } else if (data.startsWith(LEGACY_PREFIX)) {
            payload = data.substring(LEGACY_PREFIX.length());
        } else {
            return data;
        }
        try {
            byte[] all = Base64.getDecoder().decode(payload);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] pt = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // M6：解密失败（密钥轮换/数据损坏）明确告警，返回空串由调用方跳过，不泄露密文
            log.error("敏感字段解密失败（密钥不匹配或数据损坏），该条数据将被跳过: {}", e.getMessage());
            return "";
        }
    }

    /** 对外展示用掩码：前 4 + *** + 后 4 */
    public static String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 12) return "****";
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
