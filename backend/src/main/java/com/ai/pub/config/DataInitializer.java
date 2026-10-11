package com.ai.pub.config;

import com.ai.pub.entity.SysUser;
import com.ai.pub.repository.SysUserRepository;
import com.ai.pub.util.ImageGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123456";

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        initAdminUser();
        generateTestImages();
    }

    /**
     * 初始化管理员账号。
     *
     * 原实现为 findByUsername("admin").ifPresent(...)，只在 admin 已存在时重置密码，
     * 从不创建。而 deploy/schema.sql 实际是空目录（不会被 MySQL 执行），建表完全依赖
     * JPA ddl-auto: update，因此全新环境 sys_user 为空表，README/DEPLOY.md/启动脚本
     * 承诺的 admin/admin123456 必然登录失败（AuthService 抛"用户不存在"）。
     *
     * 这里改为：不存在则创建，存在则仅在其密码不是默认值时纠正为默认密码。
     */
    private void initAdminUser() {
        SysUser admin = userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).orElse(null);

        if (admin == null) {
            admin = new SysUser();
            admin.setUsername(DEFAULT_ADMIN_USERNAME);
            admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            admin.setNickname("系统管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            userRepository.save(admin);
            log.info("Created default admin user: {}", DEFAULT_ADMIN_USERNAME);
            return;
        }

        if (!passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            userRepository.save(admin);
            log.info("Updated admin password");
        }
    }

    private void generateTestImages() {
        String uploadDir = "/app/uploads/images";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String[] requiredFiles = {"banner1.jpg", "product1.jpg", "logo.png", "poster.jpg", "bg.jpg"};
        boolean needGenerate = false;
        
        for (String fileName : requiredFiles) {
            File file = new File(uploadDir + "/" + fileName);
            if (!file.exists()) {
                needGenerate = true;
                break;
            }
        }

        if (needGenerate) {
            try {
                ImageGenerator.generateTestImages(uploadDir);
                log.info("Generated test images successfully");
            } catch (Exception e) {
                log.warn("Failed to generate test images: {}", e.getMessage());
            }
        }
    }
}