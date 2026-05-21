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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class WechatAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WechatAccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        for (int i = 1; i <= 3; i++) {
            WechatAccount a = new WechatAccount();
            a.setName("公众号" + i);
            a.setAppId("wx_test_" + i);
            a.setAppSecret("secret_" + i);
            a.setToken("token_" + i);
            a.setAiEnabled(i != 2);
            a.setStatus(1);
            accountRepository.save(a);
        }
    }

    @Test
    void shouldListAccountsPaginated() throws Exception {
        mockMvc.perform(get("/api/wechat/accounts?page=1&pageSize=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records", hasSize(3)))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void shouldGetAccountById() throws Exception {
        Long id = accountRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/wechat/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("公众号1"))
                .andExpect(jsonPath("$.data.appId").value("wx_test_1"));
    }

    @Test
    void shouldCreateAccount() throws Exception {
        mockMvc.perform(post("/api/wechat/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新公众号\",\"appId\":\"wx_new\",\"appSecret\":\"sec\",\"token\":\"tok\",\"aiEnabled\":true,\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("新公众号"));
    }

    @Test
    void shouldUpdateAccount() throws Exception {
        Long id = accountRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/wechat/accounts/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"已更名\",\"appId\":\"wx_updated\",\"appSecret\":\"sec_new\",\"token\":\"tok_new\",\"aiEnabled\":false,\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("已更名"));
    }

    @Test
    void shouldDeleteAccount() throws Exception {
        Long id = accountRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/wechat/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
