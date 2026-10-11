package com.ai.pub.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;

    /**
     * 连接超时（毫秒）。原实现直接 new OpenAiApi(baseUrl, apiKey)，
     * 使用的是默认 RestClient，application.yml 里的 spring.ai.openai.timeout.*
     * 因为绕过了 Spring AI 自动配置而完全不生效。
     */
    @Value("${spring.ai.openai.timeout.connect:5000}")
    private int connectTimeoutMs;

    /**
     * 读取超时（毫秒）。大模型生成一段中文回复通常需要数秒，
     * 原配置写的 2000ms（2 秒）过于激进，容易导致 AI 回复全部降级为默认话术。
     */
    @Value("${spring.ai.openai.timeout.read:30000}")
    private int readTimeoutMs;

    @Bean
    public OpenAiApi openAiApi() {
        // 显式构造带超时的 HttpClient，让超时配置真正生效
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        // WebClient.Builder 不能传 null：OpenAiApi 会调用其 baseUrl(...) 进行配置。
        // 本项目只用同步的 chatCompletionEntity（RestClient），WebClient 仅为满足构造签名。
        org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder =
                org.springframework.web.reactive.function.client.WebClient.builder();

        return new OpenAiApi(baseUrl, apiKey, restClientBuilder, webClientBuilder);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .build();
        return new OpenAiChatModel(openAiApi, options);
    }
}
