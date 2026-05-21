package com.ai.pub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "HH:mm:ss";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 序列化
        javaTimeModule.addSerializer(new LocalDateTimeSerializer(
                DateTimeFormatter.ofPattern(DATETIME_PATTERN).withZone(ZONE)));
        javaTimeModule.addSerializer(new LocalDateSerializer(
                DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addSerializer(new LocalTimeSerializer(
                DateTimeFormatter.ofPattern(TIME_PATTERN)));

        // 反序列化（必须与序列化格式一致，否则 RabbitMQ 消息反序列化会失败）
        javaTimeModule.addDeserializer(java.time.LocalDateTime.class, new LocalDateTimeDeserializer(
                DateTimeFormatter.ofPattern(DATETIME_PATTERN)));
        javaTimeModule.addDeserializer(java.time.LocalDate.class, new LocalDateDeserializer(
                DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addDeserializer(java.time.LocalTime.class, new LocalTimeDeserializer(
                DateTimeFormatter.ofPattern(TIME_PATTERN)));

        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
