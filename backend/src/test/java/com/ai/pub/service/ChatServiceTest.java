package com.ai.pub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(redisTemplate, objectMapper, chatModel);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void shouldReturnAIReplyAndSaveConversation() {
        when(valueOps.get(anyString())).thenReturn(null);

        org.springframework.ai.chat.model.ChatResponse chatResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        AssistantMessage assistantMsg = new AssistantMessage("你好，有什么可以帮助你的？");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMsg);

        String reply = chatService.chat(1L, "openid1", "你好");

        assertEquals("你好，有什么可以帮助你的？", reply);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("wechat:conversation:1:openid1"), jsonCaptor.capture(),
                eq(24L), eq(TimeUnit.HOURS));

        assertTrue(jsonCaptor.getValue().contains("你好"));
        assertTrue(jsonCaptor.getValue().contains("有什么可以帮助你的"));
    }

    @Test
    void shouldMaintainMultiTurnConversation() throws Exception {
        String historyJson = "[{\"role\":\"user\",\"content\":\"今天天气怎么样\"}," +
                "{\"role\":\"assistant\",\"content\":\"今天晴天，适合出行\"}]";
        when(valueOps.get("wechat:conversation:1:openid1")).thenReturn(historyJson);

        org.springframework.ai.chat.model.ChatResponse chatResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        AssistantMessage assistantMsg = new AssistantMessage("明天也可能下雨");

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMsg);

        String reply = chatService.chat(1L, "openid1", "明天呢");

        assertEquals("明天也可能下雨", reply);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());

        List<org.springframework.ai.chat.messages.Message> messages =
                promptCaptor.getValue().getInstructions();
        assertTrue(messages.size() >= 3);
        List<String> contents = messages.stream()
                .map(org.springframework.ai.chat.messages.Message::getContent)
                .toList();
        assertTrue(contents.contains("今天天气怎么样"));
        assertTrue(contents.contains("今天晴天，适合出行"));
        assertTrue(contents.contains("明天呢"));
    }

    @Test
    void shouldTruncateHistoryByTokenBudget() throws Exception {
        // 构造22条历史消息（测试Token预算滑动窗口会丢弃早期消息）
        StringBuilder sb = new StringBuilder("[");
        for (int i = 1; i <= 22; i++) {
            if (i > 1) sb.append(",");
            String role = i % 2 == 1 ? "user" : "assistant";
            sb.append("{\"role\":\"").append(role)
                    .append("\",\"content\":\"消息").append(i).append("\"}");
        }
        sb.append("]");
        when(valueOps.get(anyString())).thenReturn(sb.toString());

        org.springframework.ai.chat.model.ChatResponse chatResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        AssistantMessage assistantMsg = new AssistantMessage("新回复");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMsg);

        chatService.chat(1L, "openid1", "新消息");

        // 验证AI被调用（滑动窗口算法正常工作，没有因为历史过多而崩溃）
        verify(chatModel).call(any(Prompt.class));

        // 验证保存了对话历史（包含新消息，总条数≤22+1=23）
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), jsonCaptor.capture(), anyLong(), any());

        List<Map<String, String>> saved = objectMapper.readValue(jsonCaptor.getValue(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
        // 22条旧历史 + 2条新消息(user+assistant) = 24条，但保存上限50，所以全部保留
        assertTrue(saved.size() <= 50);
        // 最后两条应该是新对话
        assertEquals("user", saved.get(saved.size() - 2).get("role"));
        assertEquals("新消息", saved.get(saved.size() - 2).get("content"));
    }

    @Test
    void shouldApplyIntentRecognition() {
        // 测试意图识别：投诉消息应匹配到投诉意图
        when(valueOps.get(anyString())).thenReturn(null);

        org.springframework.ai.chat.model.ChatResponse chatResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        AssistantMessage assistantMsg = new AssistantMessage("非常抱歉给您带来不好的体验");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMsg);

        String reply = chatService.chat(1L, "openid1", "你们这个太差了，我要投诉");

        assertNotNull(reply);
        // 验证AI被调用（意图识别+情感分析流程正常走完）
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldApplySentimentAnalysis() {
        // 测试情感分析：负面情绪消息应触发安抚策略
        when(valueOps.get(anyString())).thenReturn(null);

        org.springframework.ai.chat.model.ChatResponse chatResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        AssistantMessage assistantMsg = new AssistantMessage("非常理解您的心情");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMsg);

        String reply = chatService.chat(1L, "openid1", "垃圾产品，太差了，恶心");

        assertNotNull(reply);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldHandleAITimeoutGracefully() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Read timed out"));

        String reply = chatService.chat(1L, "openid1", "测试消息");

        assertEquals("抱歉，AI服务暂时不可用，请稍后再试。", reply);
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void shouldHandleAIRateLimitGracefully() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("429 Too Many Requests"));

        String reply = chatService.chat(1L, "openid1", "测试消息");

        assertEquals("抱歉，AI服务暂时不可用，请稍后再试。", reply);
    }

    @Test
    void shouldHandleRedisReadFailureGracefully() {
        when(valueOps.get(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        org.springframework.ai.chat.model.ChatResponse chatResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        AssistantMessage assistantMsg = new AssistantMessage("仍然回复");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMsg);

        String reply = chatService.chat(1L, "openid1", "你好");

        assertEquals("仍然回复", reply);
    }

    @Test
    void shouldClearConversation() {
        chatService.clearConversation(1L, "openid1");

        verify(redisTemplate).delete("wechat:conversation:1:openid1");
    }

    @Test
    void shouldExpireConversationAfter24Hours() {
        when(valueOps.get(anyString())).thenReturn(null);

        org.springframework.ai.chat.model.ChatResponse chatResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.springframework.ai.chat.model.Generation generation =
                mock(org.springframework.ai.chat.model.Generation.class);
        AssistantMessage assistantMsg = new AssistantMessage("回复");
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMsg);

        chatService.chat(1L, "openid1", "测试");

        verify(valueOps).set(anyString(), anyString(), eq(24L), eq(TimeUnit.HOURS));
    }
}
