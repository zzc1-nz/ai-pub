package com.ai.pub.controller;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatMenu;
import com.ai.pub.repository.WechatAccountRepository;
import com.ai.pub.repository.WechatMenuRepository;
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
class WechatMenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WechatMenuRepository menuRepository;

    @Autowired
    private WechatAccountRepository accountRepository;

    private Long account1Id;
    private Long account2Id;

    @BeforeEach
    void setUp() {
        menuRepository.deleteAll();
        accountRepository.deleteAll();

        WechatAccount a1 = new WechatAccount();
        a1.setName("号A");
        a1.setAppId("wx_a");
        a1.setAppSecret("s");
        a1.setToken("t");
        account1Id = accountRepository.save(a1).getId();

        WechatAccount a2 = new WechatAccount();
        a2.setName("号B");
        a2.setAppId("wx_b");
        a2.setAppSecret("s");
        a2.setToken("t");
        account2Id = accountRepository.save(a2).getId();

        // 号A: 3个菜单
        for (int i = 1; i <= 3; i++) {
            WechatMenu m = new WechatMenu();
            m.setAccountId(account1Id);
            m.setParentId(0L);
            m.setName("菜单A-" + i);
            m.setType("click");
            m.setKey("KEY_A_" + i);
            m.setSortOrder(i);
            menuRepository.save(m);
        }

        // 号B: 2个菜单
        for (int i = 1; i <= 2; i++) {
            WechatMenu m = new WechatMenu();
            m.setAccountId(account2Id);
            m.setParentId(0L);
            m.setName("菜单B-" + i);
            m.setType("view");
            m.setUrl("https://b" + i + ".com");
            m.setSortOrder(i);
            menuRepository.save(m);
        }
    }

    @Test
    void shouldReturnDifferentMenusForDifferentAccounts() throws Exception {
        // 号A 应该有 3 个
        mockMvc.perform(get("/api/wechat/menus?accountId=" + account1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].name").value("菜单A-1"));

        // 号B 应该有 2 个
        mockMvc.perform(get("/api/wechat/menus?accountId=" + account2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("菜单B-1"));
    }

    @Test
    void shouldCreateAndDeleteMenu() throws Exception {
        String newMenu = "{\"accountId\":" + account1Id + ",\"parentId\":0,\"name\":\"新菜单\",\"type\":\"click\",\"key\":\"NEW\",\"sortOrder\":99}";

        String response = mockMvc.perform(post("/api/wechat/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newMenu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("新菜单"))
                .andReturn().getResponse().getContentAsString();

        // 提取 id 后删除
        Long menuId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/wechat/menus/" + menuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldUpdateMenu() throws Exception {
        WechatMenu existing = menuRepository.findAll().get(0);
        Long menuId = existing.getId();

        mockMvc.perform(put("/api/wechat/menus/" + menuId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + account1Id + ",\"parentId\":0,\"name\":\"已更新\",\"type\":\"click\",\"key\":\"UPDATED\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("已更新"));
    }
}
