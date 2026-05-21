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

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        userRepository.findByUsername("admin").ifPresent(user -> {
            if (!passwordEncoder.matches("admin123456", user.getPassword())) {
                user.setPassword(passwordEncoder.encode("admin123456"));
                userRepository.save(user);
                log.info("Updated admin password");
            }
        });

        generateTestImages();
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