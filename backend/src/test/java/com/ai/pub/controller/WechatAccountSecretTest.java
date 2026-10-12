package com.ai.pub.controller;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.repository.WechatAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 公众号凭证泄漏回归测试。
 *
 * 背景：appSecret / encodingAesKey 原先随 /api/wechat/accounts 明文下发，
 * 任何能打开"公众号管理"页面的账号都能拿到全部公众号的调用凭证。
 * 已改为 WRITE_ONLY（只写不读），本测试锁定该行为。
 *
 * 同时验证配套的"留空不覆盖"语义：否则前端编辑时因拿不到原值会把凭证清空。
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class WechatAccountSecretTest {

    private static final String SECRET = "SUPER_SECRET_VALUE_123";
    private static final String AES_KEY = "AESKEY_PROBE_VALUE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WechatAccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        WechatAccount a = new WechatAccount();
        a.setName("凭证测试公众号");
        a.setAppId("wx_secret_test");
        a.setAppSecret(SECRET);
        a.setToken("tok_secret");
        a.setEncodingAesKey(AES_KEY);
        a.setAiEnabled(true);
        a.setStatus(1);
        accountId = accountRepository.save(a).getId();
    }

    @Test
    void listShouldNotLeakCredentials() throws Exception {
        mockMvc.perform(get("/api/wechat/accounts?pageNum=1&pageSize=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("凭证测试公众号"))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString(AES_KEY))));
    }

    @Test
    void detailShouldNotLeakCredentials() throws Exception {
        mockMvc.perform(get("/api/wechat/accounts/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appId").value("wx_secret_test"))
                .andExpect(content().string(not(containsString(SECRET))))
                .andExpect(content().string(not(containsString(AES_KEY))));
    }

    /**
     * 编辑时只改名称、凭证留空 —— 数据库中的凭证必须保持不变。
     */
    @Test
    void updateWithBlankCredentialsShouldKeepExistingValues() throws Exception {
        mockMvc.perform(put("/api/wechat/accounts/" + accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"已更名\",\"appId\":\"wx_secret_test\",\"token\":\"tok_secret\","
                                + "\"appSecret\":\"\",\"encodingAesKey\":\"\",\"aiEnabled\":true,\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 直接查库校验：凭证未被清空
        WechatAccount saved = accountRepository.findById(accountId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(SECRET, saved.getAppSecret(),
                "留空提交不应清空已有 AppSecret");
        org.junit.jupiter.api.Assertions.assertEquals(AES_KEY, saved.getEncodingAesKey(),
                "留空提交不应清空已有 EncodingAESKey");
        org.junit.jupiter.api.Assertions.assertEquals("已更名", saved.getName());
    }

    /**
     * 显式提供新凭证时应当覆盖成功。
     */
    @Test
    void updateWithNewCredentialsShouldOverwrite() throws Exception {
        mockMvc.perform(put("/api/wechat/accounts/" + accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"凭证测试公众号\",\"appId\":\"wx_secret_test\",\"token\":\"tok_secret\","
                                + "\"appSecret\":\"NEW_SECRET_456\",\"encodingAesKey\":\"NEW_AESKEY_456\","
                                + "\"aiEnabled\":true,\"status\":1}"))
                .andExpect(status().isOk());

        WechatAccount saved = accountRepository.findById(accountId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("NEW_SECRET_456", saved.getAppSecret());
        org.junit.jupiter.api.Assertions.assertEquals("NEW_AESKEY_456", saved.getEncodingAesKey());
    }
}
