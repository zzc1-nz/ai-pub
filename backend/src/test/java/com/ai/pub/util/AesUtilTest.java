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
}
