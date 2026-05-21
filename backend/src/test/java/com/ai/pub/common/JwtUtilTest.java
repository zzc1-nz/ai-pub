package com.ai.pub.common;

import com.ai.pub.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    private JwtConfig jwtConfig;
    private JwtUtil jwtUtil;

    private static final String TEST_SECRET = "this-is-a-test-jwt-secret-key-that-is-long-enough-for-hs256";

    @BeforeEach
    void setUp() {
        jwtConfig = mock(JwtConfig.class);
        when(jwtConfig.getSecret()).thenReturn(TEST_SECRET);
        when(jwtConfig.getExpiration()).thenReturn(86400000L);
        jwtUtil = new JwtUtil(jwtConfig);
    }

    @Test
    void shouldGenerateAndValidateToken() {
        String token = jwtUtil.generateToken(1L, "admin", "ADMIN");

        assertNotNull(token);
        assertTrue(token.length() > 20);

        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void shouldExtractClaimsFromToken() {
        String token = jwtUtil.generateToken(42L, "testuser", "OPERATOR");

        assertEquals(42L, jwtUtil.getUserId(token));
        assertEquals("testuser", jwtUtil.getUsername(token));
        assertEquals("OPERATOR", jwtUtil.getRole(token));
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtConfig shortLivedConfig = mock(JwtConfig.class);
        when(shortLivedConfig.getSecret()).thenReturn(TEST_SECRET);
        when(shortLivedConfig.getExpiration()).thenReturn(1L);

        JwtUtil shortLivedJwt = new JwtUtil(shortLivedConfig);
        String token = shortLivedJwt.generateToken(1L, "admin", "ADMIN");

        Thread.sleep(10);

        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = jwtUtil.generateToken(1L, "admin", "ADMIN");
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";

        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        JwtConfig otherConfig = mock(JwtConfig.class);
        when(otherConfig.getSecret()).thenReturn("another-different-secret-key-that-is-also-long-enough-for-testing");
        when(otherConfig.getExpiration()).thenReturn(86400000L);
        JwtUtil otherJwt = new JwtUtil(otherConfig);

        String token = otherJwt.generateToken(1L, "admin", "ADMIN");

        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void shouldRejectMalformedToken() {
        assertFalse(jwtUtil.validateToken("not.a.valid.jwt.token"));
    }

    @Test
    void shouldRejectNullToken() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    void shouldRejectEmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    void shouldRejectBlankToken() {
        assertFalse(jwtUtil.validateToken("   "));
    }

    @Test
    void shouldSetCorrectExpirationTime() {
        when(jwtConfig.getExpiration()).thenReturn(60000L);
        jwtUtil = new JwtUtil(jwtConfig);

        String token = jwtUtil.generateToken(1L, "admin", "ADMIN");
        assertTrue(jwtUtil.validateToken(token));
    }
}
