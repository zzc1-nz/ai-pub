package com.ai.pub.controller;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatMaterial;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.repository.WechatAccountRepository;
import com.ai.pub.repository.WechatMaterialRepository;
import com.ai.pub.repository.WechatMessageRepository;
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
class WechatMaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WechatMaterialRepository materialRepository;

    @Autowired
    private WechatAccountRepository accountRepository;

    @Autowired
    private WechatMessageRepository messageRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        materialRepository.deleteAll();
        accountRepository.deleteAll();

        WechatAccount account = new WechatAccount();
        account.setName("测试号");
        account.setAppId("wx_test");
        account.setAppSecret("secret");
        account.setToken("token");
        accountId = accountRepository.save(account).getId();

        for (int i = 1; i <= 3; i++) {
            WechatMaterial m = new WechatMaterial();
            m.setAccountId(accountId);
            m.setType("image");
            m.setTitle("素材" + i);
            m.setUrl("/uploads/image" + i + ".jpg");
            materialRepository.save(m);
        }
    }

    @Test
    void shouldListMaterialsPaginated() throws Exception {
        mockMvc.perform(get("/api/wechat/materials?accountId=" + accountId + "&pageNum=1&pageSize=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records", hasSize(3)))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void shouldCreateAndDeleteMaterial() throws Exception {
        String newMaterial = "{\"accountId\":" + accountId + ",\"type\":\"news\",\"title\":\"新闻标题\",\"content\":\"<p>新闻内容</p>\",\"author\":\"作者\"}";

        String response = mockMvc.perform(post("/api/wechat/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newMaterial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("新闻标题"))
                .andReturn().getResponse().getContentAsString();

        Long materialId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/wechat/materials/" + materialId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
