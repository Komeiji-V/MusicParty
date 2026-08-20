package org.thornex.musicparty.util;

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
 * 敏感字段加密（M3）：AES-256-GCM，密钥由 JWT_SECRET 经 SHA-256 派生（同一信任域）。
 * 密文格式：enc:{Base64(iv + ciphertext)}。
 * decrypt 对无前缀的旧明文数据原样返回（兼容存量数据，写入时一律加密）。
 */
@Component
public class CryptoUtil {

    private static final String PREFIX = "enc:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoUtil(AppProperties appProperties) {
        String secret = appProperties.getJwt().getSecret();
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
        if (!data.startsWith(PREFIX)) {
            // 存量明文数据（兼容），下次写入会加密
            return data;
        }
        try {
            byte[] all = Base64.getDecoder().decode(data.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] pt = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败（密钥变更等）：按不可用处理，返回空串避免泄露密文
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
