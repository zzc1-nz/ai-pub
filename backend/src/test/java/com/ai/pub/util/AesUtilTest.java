package com.ai.pub.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AesUtil 回归测试。
 *
 * 背景：修复前用"Base64 解码后长度 > 12"判断一个值是否已加密，
 * 形如 "1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p" 的明文本身就是合法 Base64
 * 且解码长度 > 12，会被误判为"已加密"从而跳过加密，导致 AppSecret 明文入库。
 */
class AesUtilTest {

    private final AesUtil aes = new AesUtil();

    /** 这些正是旧启发式会误判的字符串，必须真正被加密。 */
    @Test
    void base64LookingPlaintextMustStillBeEncrypted() {
        String[] tricky = {
            "1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p",
            "abcdef1234567890abcdef1234567890",
            "mySecretKey123",
            "short",
            "wx_secret_abc123"
        };
        for (String plain : tricky) {
            String enc = aes.encrypt(plain);
            assertNotEquals(plain, enc, "明文被跳过加密: " + plain);
            assertTrue(AesUtil.isEncrypted(enc), "密文应带前缀: " + enc);
            assertEquals(plain, aes.decrypt(enc), "往返失败: " + plain);
        }
    }

    @Test
    void encryptShouldBeIdempotent() {
        String once = aes.encrypt("secret123");
        assertEquals(once, aes.encrypt(once), "重复加密不应套娃");
    }

    @Test
    void decryptShouldPassThroughLegacyPlaintext() {
        // 历史数据无前缀，应按明文原样返回，保证旧数据可读
        assertEquals("plainLegacyValue", aes.decrypt("plainLegacyValue"));
    }

    @Test
    void isEncryptedShouldBeNullSafe() {
        assertFalse(AesUtil.isEncrypted(null));
        assertFalse(AesUtil.isEncrypted(""));
    }

    @Test
    void encryptNullAndEmptyShouldBeNoop() {
        assertNull(aes.encrypt(null));
        assertEquals("", aes.encrypt(""));
    }

    /**
     * 不同密钥必须产生互不可解密的密文 —— 验证密钥真的参与了加解密，
     * 而不是被忽略（例如构造函数误用了默认密钥）。
     */
    @Test
    void differentKeysMustNotInteroperate() {
        AesUtil keyA = new AesUtil("key-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        AesUtil keyB = new AesUtil("key-BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

        String cipherA = keyA.encrypt("my-secret");
        assertNotEquals(cipherA, keyB.encrypt("my-secret"), "不同密钥的密文不应相同");
        assertEquals("my-secret", keyA.decrypt(cipherA), "同密钥应可解密");

        // 用 B 的密钥解 A 的密文必须失败（GCM 认证标签校验不通过）
        assertThrows(RuntimeException.class, () -> keyB.decrypt(cipherA),
                "跨密钥解密必须失败，否则说明密钥未生效");
    }

    @Test
    void emptyKeyShouldBeRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AesUtil(""));
        assertThrows(IllegalArgumentException.class, () -> new AesUtil(null));
    }

    /**
     * 短密钥会被 0 填充到 32 字节，仍应可用（但会打 WARN 日志）。
     */
    @Test
    void shortKeyShouldStillWorkWithPadding() {
        AesUtil shortKey = new AesUtil("short");
        String enc = shortKey.encrypt("value");
        assertEquals("value", shortKey.decrypt(enc));
    }

    /**
     * 密钥来源必须在类加载时即确定，不能依赖是否已 new 过 AesUtil。
     *
     * 回归点：早期实现把赋值放在 resolveKey()（仅由构造函数调用），
     * 导致 DataInitializer 的启动自检先调用 isUsingDefaultKey() 时拿到
     * "unresolved"/false，安全警告静默失效。
     */
    @Test
    void keySourceShouldBeResolvedWithoutInstantiating() {
        // 不 new AesUtil，直接读静态状态
        String source = AesUtil.getKeySource();
        assertNotEquals("unresolved", source,
                "密钥来源必须在类加载时确定，否则启动自检会漏报默认密钥警告");

        // 测试环境未设置 AES_SECRET_KEY，应判定为默认密钥
        assertTrue(AesUtil.isUsingDefaultKey(),
                "未配置 AES_SECRET_KEY 时应报告正在使用默认密钥，当前来源=" + source);
    }
}
