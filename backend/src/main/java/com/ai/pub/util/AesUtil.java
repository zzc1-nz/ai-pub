package com.ai.pub.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * 内置开发默认密钥。生产环境必须通过 AES_SECRET_KEY 覆盖：
     * 该值随源码公开，用它加密等于没有加密。
     */
    private static final String DEFAULT_KEY = "ai-pub-aes-256-secret-key-2024!";

    /** 环境变量名（Docker / systemd 等场景） */
    private static final String KEY_ENV = "AES_SECRET_KEY";

    /** JVM 系统属性名（java -D 或 IDE 运行配置场景） */
    private static final String KEY_PROP = "aes.secret-key";

    /** AES-256 要求 32 字节密钥 */
    private static final int KEY_LENGTH_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(AesUtil.class);

    private final SecretKeySpec secretKey;

    /**
     * 密钥来源（"env:AES_SECRET_KEY" / "property:aes.secret-key" / "default"）。
     *
     * 用静态初始化块在类加载时解析一次，而不是等到 new AesUtil() 才赋值：
     * 否则若调用方先读 isUsingDefaultKey()（如 DataInitializer 的启动自检）
     * 而尚无任何 AesUtil 实例，就会得到 "unresolved" 并漏报安全警告。
     */
    private static final String KEY_SOURCE;

    /** 类加载时解析生效的密钥，供实例构造与静态自检共用 */
    private static final String RESOLVED_KEY;

    static {
        String env = System.getenv(KEY_ENV);
        String prop = System.getProperty(KEY_PROP);

        if (env != null && !env.trim().isEmpty()) {
            KEY_SOURCE = "env:" + KEY_ENV;
            RESOLVED_KEY = env.trim();
        } else if (prop != null && !prop.trim().isEmpty()) {
            KEY_SOURCE = "property:" + KEY_PROP;
            RESOLVED_KEY = prop.trim();
        } else {
            KEY_SOURCE = "default";
            RESOLVED_KEY = DEFAULT_KEY;
            log.warn("未配置 AES 密钥（环境变量 {} 或系统属性 {}），正在使用内置默认密钥。"
                            + "该默认值随源码公开，仅适用于本地开发；"
                            + "生产环境请务必设置 {}，否则公众号 AppSecret 的加密存储不具备实际保护作用。",
                    KEY_ENV, KEY_PROP, KEY_ENV);
        }
    }

    public AesUtil() {
        this(RESOLVED_KEY);
    }

    public AesUtil(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("AES 密钥不能为空");
        }

        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < KEY_LENGTH_BYTES) {
            // 零填充会让短密钥的强度显著下降（尤其是后半段全为 0），必须显式告警
            log.warn("AES 密钥长度不足 {} 字节（实际 {} 字节），将用 0 填充至 32 字节。"
                            + "建议配置足够长的随机密钥，例如: openssl rand -base64 32",
                    KEY_LENGTH_BYTES, keyBytes.length);
        }

        // 确保密钥为 32 字节（AES-256）
        byte[] paddedKey = new byte[KEY_LENGTH_BYTES];
        System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, KEY_LENGTH_BYTES));
        this.secretKey = new SecretKeySpec(paddedKey, ALGORITHM);
    }

    /**
     * 当前生效的密钥来源，供启动自检/健康检查展示。
     */
    public static String getKeySource() {
        return KEY_SOURCE;
    }

    /**
     * 是否正在使用内置默认密钥（生产环境应避免）。
     */
    public static boolean isUsingDefaultKey() {
        return "default".equals(KEY_SOURCE);
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
