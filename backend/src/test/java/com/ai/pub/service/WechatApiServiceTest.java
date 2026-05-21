package com.ai.pub.service;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.repository.WechatAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WechatApiServiceTest {

    @Mock
    private WechatAccountRepository accountRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WechatApiService wechatApiService;

    private WechatAccount account;

    @BeforeEach
    void setUp() {
        wechatApiService = new WechatApiService(accountRepository, objectMapper, redisTemplate);
        ReflectionTestUtils.setField(wechatApiService, "restTemplate", restTemplate);

        account = new WechatAccount();
        account.setId(1L);
        account.setAppId("wx_test");
        account.setAppSecret("test_secret");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(7000L);
    }

    @Test
    void shouldFetchAndCacheAccessTokenInRedis() throws Exception {
        when(valueOperations.get("wechat:access_token:1")).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        String tokenResponse = "{\"access_token\":\"fresh_access_token_12345\",\"expires_in\":7200}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(tokenResponse);

        String token = wechatApiService.getAccessToken(1L);

        assertEquals("fresh_access_token_12345", token);
        verify(valueOperations).set(eq("wechat:access_token:1"), eq("fresh_access_token_12345"), eq(7140L), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldUseCachedTokenFromRedis() {
        when(valueOperations.get("wechat:access_token:1")).thenReturn("cached_token_456");

        String token = wechatApiService.getAccessToken(1L);

        assertEquals("cached_token_456", token);
        verify(restTemplate, never()).getForObject(anyString(), any());
        verify(accountRepository, never()).findById(anyLong());
    }

    @Test
    void shouldRefreshTokenWhenRedisCacheEmpty() throws Exception {
        when(valueOperations.get("wechat:access_token:1")).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        String tokenResponse = "{\"access_token\":\"new_access_token_78900\",\"expires_in\":7200}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(tokenResponse);

        String token = wechatApiService.getAccessToken(1L);

        assertEquals("new_access_token_78900", token);
        verify(restTemplate).getForObject(anyString(), any());
    }

    @Test
    void shouldThrowWhenAccessTokenApiReturnsError() throws Exception {
        when(valueOperations.get("wechat:access_token:1")).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        String errorResponse = "{\"errcode\":40001,\"errmsg\":\"invalid credential\"}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(errorResponse);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wechatApiService.getAccessToken(1L));
        assertTrue(ex.getMessage().contains("获取access_token失败"));
    }

    @Test
    void shouldThrowWhenAccessTokenNetworkFails() {
        when(valueOperations.get("wechat:access_token:1")).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wechatApiService.getAccessToken(1L));
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        when(valueOperations.get("wechat:access_token:99")).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> wechatApiService.getAccessToken(99L));
    }

    @Test
    void sendCustomerMessageShouldReturnFalseOnApiError() throws Exception {
        when(valueOperations.get("wechat:access_token:1")).thenReturn("valid_token");
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(7000L);

        String errorResponse = "{\"errcode\":45015,\"errmsg\":\"response out of time limit\"}";
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(errorResponse);

        boolean result = wechatApiService.sendCustomerMessage(1L, "openid1", "回复内容");

        assertFalse(result);
    }

    @Test
    void sendCustomerMessageShouldReturnFalseOnNetworkError() {
        when(valueOperations.get("wechat:access_token:1")).thenReturn("valid_token");
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(7000L);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));

        boolean result = wechatApiService.sendCustomerMessage(1L, "openid1", "回复内容");

        assertFalse(result);
    }

    @Test
    void sendCustomerMessageShouldReturnTrueOnSuccess() throws Exception {
        when(valueOperations.get("wechat:access_token:1")).thenReturn("valid_token");
        String successResponse = "{\"errcode\":0,\"errmsg\":\"ok\"}";
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(successResponse);

        boolean result = wechatApiService.sendCustomerMessage(1L, "openid1", "回复内容");

        assertTrue(result);
    }

    @Test
    void syncMenuShouldThrowOnApiFailure() throws Exception {
        when(valueOperations.get("wechat:access_token:1")).thenReturn("valid_token");
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(7000L);

        String failResponse = "{\"errcode\":40016,\"errmsg\":\"invalid menu size\"}";
        when(restTemplate.postForObject(contains("create"), any(), eq(String.class)))
                .thenReturn(failResponse);
        when(restTemplate.getForObject(contains("delete"), eq(String.class)))
                .thenReturn("{\"errcode\":0}");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> wechatApiService.syncMenu(1L, Collections.emptyList()));
        assertTrue(ex.getMessage().contains("invalid menu size"));
    }
}
