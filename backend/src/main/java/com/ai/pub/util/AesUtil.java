package com.ai.pub.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 * 用于加密存储敏感信息（如 AppSecret、EncodingAesKey）
 *
 * 密文格式：{@code enc:v1:} + Base64(IV(12字节) + 密文+认证标签)
 *
 * 为什么需要前缀：
 * 原实现用"Base64 解码后长度 > 12"来判断一个值是否已加密，该启发式不可靠 ——
 * 形如 "1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p" 或 "abcdef1234567890abcdef1234567890"
 * 的明文本身就是合法 Base64 且解码长度 > 12，会被误判为"已加密"从而跳过加密，
 * 导致 AppSecret 以明文入库。改用固定前缀后判断是确定性的。
 */
public class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /** 密文前缀标记，用于区分密文与明文（旧数据无前缀，按明文兼容处理） */
    private static final String PREFIX = "enc:v1:";

    /** 默认密钥（生产环境应通过环境变量 AES_SECRET_KEY 覆盖） */
    private static final String DEFAULT_KEY = "ai-pub-aes-256-secret-key-2024!";

    /** 环境变量 / 系统属性名，用于覆盖默认密钥 */
    private static final String KEY_ENV = "AES_SECRET_KEY";

    private final SecretKeySpec secretKey;

    public AesUtil() {
        this(resolveKey());
    }

    public AesUtil(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // 确保密钥为 32 字节（AES-256）
        byte[] paddedKey = new byte[32];
        System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
        this.secretKey = new SecretKeySpec(paddedKey, ALGORITHM);
    }

    /**
     * 优先读取环境变量 / JVM 系统属性 AES_SECRET_KEY，未配置时回退到默认值。
     * 这样生产环境可以不改代码就替换密钥。
     */
    private static String resolveKey() {
        String fromEnv = System.getenv(KEY_ENV);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        String fromProp = System.getProperty(KEY_ENV);
        if (fromProp != null && !fromProp.trim().isEmpty()) {
            return fromProp.trim();
        }
        return DEFAULT_KEY;
    }

    /**
     * 判断一个值是否为本工具加密产生的密文（确定性判断，基于前缀）
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 加密
     * @param plaintext 明文
     * @return 带前缀的 Base64 密文（enc:v1: + IV + 密文）
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        // 已加密的值不重复加密（幂等），避免 @PreUpdate 多次触发时套娃
        if (isEncrypted(plaintext)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 将 IV 和密文拼接后 Base64 编码
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 解密
     * @param ciphertext 带前缀的 Base64 密文；无前缀时按明文原样返回（兼容旧数据）
     * @return 明文
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        // 无前缀说明是历史遗留的明文，原样返回
        if (!isEncrypted(ciphertext)) {
            return ciphertext;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }
}
