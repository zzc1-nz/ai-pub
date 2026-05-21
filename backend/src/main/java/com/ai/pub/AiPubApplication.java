package com.ai.pub;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class AiPubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPubApplication.class, args);
    }
}
