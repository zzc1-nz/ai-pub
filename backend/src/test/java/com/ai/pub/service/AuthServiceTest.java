package com.ai.pub.service;

import com.ai.pub.dto.LoginRequest;
import com.ai.pub.dto.LoginResponse;
import com.ai.pub.entity.SysUser;
import com.ai.pub.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.ai.pub.common.JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private SysUser adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new SysUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setPassword("$2a$10$encoded_password_hash");
        adminUser.setNickname("管理员");
        adminUser.setRole("ADMIN");
        adminUser.setStatus(1);
    }

    @Test
    void shouldLoginWithCorrectCredentials() {
        LoginRequest request = new LoginRequest("admin", "admin123456");
        when(sysUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("admin123456", adminUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(1L, "admin", "ADMIN")).thenReturn("mock.jwt.token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getToken());
        assertEquals("admin", response.getUsername());
        assertEquals("ADMIN", response.getRole());
    }

    @Test
    void shouldThrowWhenPasswordWrong() {
        LoginRequest request = new LoginRequest("admin", "wrong");
        when(sysUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrong", adminUser.getPassword())).thenReturn(false);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(request));
        assertEquals("密码错误", ex.getMessage());
    }

    @Test
    void shouldThrowWhenAccountDisabled() {
        adminUser.setStatus(0);
        LoginRequest request = new LoginRequest("admin", "admin123456");
        when(sysUserRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("admin123456", adminUser.getPassword())).thenReturn(true);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(request));
        assertEquals("账户已禁用", ex.getMessage());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        LoginRequest request = new LoginRequest("nobody", "whatever");
        when(sysUserRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login(request));
        assertEquals("用户不存在", ex.getMessage());
    }
}
