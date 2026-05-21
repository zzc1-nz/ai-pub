package com.ai.pub.controller;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatReplyRule;
import com.ai.pub.repository.WechatAccountRepository;
import com.ai.pub.repository.WechatReplyRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WechatReplyRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WechatReplyRuleRepository ruleRepository;

    @Autowired
    private WechatAccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        ruleRepository.deleteAll();
        accountRepository.deleteAll();

        WechatAccount account = new WechatAccount();
        account.setName("测试号");
        account.setAppId("wx_test");
        account.setAppSecret("secret");
        account.setToken("token");
        accountId = accountRepository.save(account).getId();

        for (int i = 1; i <= 5; i++) {
            WechatReplyRule rule = new WechatReplyRule();
            rule.setAccountId(accountId);
            rule.setName("规则" + i);
            rule.setKeyword("关键词" + i);
            rule.setMatchType(i == 3 ? "EXACT" : "CONTAINS");
            rule.setReplyType("text");
            rule.setReplyContent("回复内容" + i);
            rule.setSortOrder(i);
            rule.setStatus(1);
            ruleRepository.save(rule);
        }
    }

    @Test
    void shouldListRulesPaginated() throws Exception {
        mockMvc.perform(get("/api/wechat/reply-rules?accountId=" + accountId + "&pageNum=1&pageSize=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records", hasSize(5)))
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    void shouldCreateAndDeleteRule() throws Exception {
        String newRule = "{\"accountId\":" + accountId + ",\"name\":\"新规则\",\"keyword\":\"测试\",\"matchType\":\"CONTAINS\",\"replyType\":\"text\",\"replyContent\":\"测试回复\",\"sortOrder\":99,\"status\":1}";

        String response = mockMvc.perform(post("/api/wechat/reply-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newRule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("新规则"))
                .andReturn().getResponse().getContentAsString();

        Long ruleId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/wechat/reply-rules/" + ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldUpdateRule() throws Exception {
        Long id = ruleRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/wechat/reply-rules/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + accountId + ",\"name\":\"已更新\",\"keyword\":\"已更新\",\"matchType\":\"EXACT\",\"replyType\":\"text\",\"replyContent\":\"已更新\",\"sortOrder\":1,\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("已更新"));
    }
}
