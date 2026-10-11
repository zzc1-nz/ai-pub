package com.ai.pub.controller;

import com.ai.pub.entity.SysUser;
import com.ai.pub.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class SysUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickname("管理员");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        userRepository.save(admin);

        SysUser operator = new SysUser();
        operator.setUsername("operator");
        operator.setPassword(passwordEncoder.encode("op123"));
        operator.setNickname("运营员");
        operator.setRole("OPERATOR");
        operator.setStatus(1);
        userRepository.save(operator);
    }

    @Test
    void shouldListUsersPaginated() throws Exception {
        mockMvc.perform(get("/api/sys/users?pageNum=1&pageSize=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void shouldGetUserById() throws Exception {
        Long id = userRepository.findByUsername("admin").get().getId();

        mockMvc.perform(get("/api/sys/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.nickname").value("管理员"));
    }

    @Test
    void shouldCreateUser() throws Exception {
        mockMvc.perform(post("/api/sys/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"pass123\",\"nickname\":\"新用户\",\"role\":\"OPERATOR\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.nickname").value("新用户"));
    }

    @Test
    void shouldUpdateUser() throws Exception {
        Long id = userRepository.findByUsername("operator").get().getId();

        mockMvc.perform(put("/api/sys/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"nickname\":\"已更新运营员\",\"role\":\"ADMIN\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("已更新运营员"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void shouldDeleteUser() throws Exception {
        Long id = userRepository.findByUsername("operator").get().getId();

        mockMvc.perform(delete("/api/sys/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
