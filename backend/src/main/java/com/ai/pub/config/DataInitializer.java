package com.ai.pub.config;

import com.ai.pub.entity.SysUser;
import com.ai.pub.repository.SysUserRepository;
import com.ai.pub.util.AesUtil;
import com.ai.pub.util.ImageGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123456";

    /** application.yml 中的 JWT 默认密钥，用于判断是否仍在使用不安全的默认值 */
    private static final String DEFAULT_JWT_SECRET = "ai-pub-secret-key-2024-for-jwt-token-generation";

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    @Value("${spring.ai.openai.api-key:}")
    private String aiApiKey;

    /** 素材存储目录，来自 app.upload-dir（默认 ./uploads） */
    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    public DataInitializer(SysUserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtConfig jwtConfig) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtConfig = jwtConfig;
    }

    @Override
    public void run(String... args) throws Exception {
        checkSecurityConfig();
        initAdminUser();
        generateTestImages();
    }

    /**
     * 启动自检：提示与安全相关的配置状态。
     * 这些不是致命错误（不影响启动），但必须让运维在日志里看得见。
     */
    private void checkSecurityConfig() {
        // 1) AES 密钥：决定公众号 AppSecret 是否真的被加密保护
        if (AesUtil.isUsingDefaultKey()) {
            log.warn("================================================================");
            log.warn(" 安全提示：正在使用内置默认 AES 密钥加密公众号 AppSecret。");
            log.warn(" 该默认值随源码公开，加密不具备实际保护作用。");
            log.warn(" 生产环境请设置环境变量 AES_SECRET_KEY（建议 32 字节随机值）：");
            log.warn("   openssl rand -base64 32");
            log.warn(" 当前密钥来源: {}", AesUtil.getKeySource());
            log.warn("================================================================");
        } else {
            log.info("AES 密钥来源: {}（非默认密钥）", AesUtil.getKeySource());
        }

        // 2) JWT 密钥：application.yml 中的默认值同样随源码公开
        if (DEFAULT_JWT_SECRET.equals(jwtConfig.getSecret())) {
            log.warn("安全提示：JWT 密钥仍是配置文件中的默认值，生产环境请通过环境变量 JWT_SECRET 覆盖。");
        }

        // 3) AI Key：未配置时所有 AI 回复都会降级为默认话术
        if (aiApiKey == null || aiApiKey.trim().isEmpty()) {
            log.warn("未配置 DEEPSEEK_API_KEY，AI 回复将全部降级为默认话术。");
        }
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
        String uploadDir = Paths.get(this.uploadDir, "images").toString();
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