package com.ai.pub.consumer;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.service.*;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageConsumerTest {

    @Mock
    private WechatAccountService wechatAccountService;

    @Mock
    private WechatMessageService wechatMessageService;

    @Mock
    private WechatReplyService wechatReplyService;

    @Mock
    private ChatService chatService;

    @Mock
    private WechatApiService wechatApiService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Channel channel;

    @InjectMocks
    private MessageConsumer messageConsumer;

    private WechatAccount account;
    private WechatMessage message;
    private Message amqpMessage;

    @BeforeEach
    void setUp() {
        account = new WechatAccount();
        account.setId(1L);
        account.setName("测试号");
        account.setAiEnabled(true);
        account.setDefaultReply("默认回复");

        message = new WechatMessage();
        message.setId(1L);
        message.setAccountId(1L);
        message.setOpenid("openid123");
        message.setContent("你好");
        message.setMsgType("text");

        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(1L);
        amqpMessage = new Message(new byte[0], props);
    }

    @Test
    void shouldSkipWhenReplyAlreadyExists() throws IOException {
        message.setReplyContent("已回复内容");
        when(wechatMessageService.getById(1L)).thenReturn(message);

        messageConsumer.handleMessage(message, channel, amqpMessage);

        verify(wechatMessageService, never()).update(anyLong(), any());
        verify(wechatApiService, never()).sendCustomerMessage(anyLong(), anyString(), anyString());
        verify(channel).basicAck(eq(1L), eq(false));
    }

    @Test
    void shouldProcessByKeywordMatch() throws IOException {
        when(wechatMessageService.getById(1L)).thenReturn(message);
        when(wechatAccountService.getById(1L)).thenReturn(account);
        when(wechatReplyService.findReply(1L, "你好")).thenReturn("关键词回复");
        when(wechatApiService.sendCustomerMessage(1L, "openid123", "关键词回复")).thenReturn(true);

        messageConsumer.handleMessage(message, channel, amqpMessage);

        verify(wechatMessageService).update(eq(1L), any());
        verify(wechatApiService).sendCustomerMessage(1L, "openid123", "关键词回复");
        verify(chatService, never()).chat(anyLong(), anyString(), anyString());
        verify(channel).basicAck(eq(1L), eq(false));
    }

    @Test
    void shouldFallbackToAIServiceWhenNoKeywordMatch() throws IOException {
        when(wechatMessageService.getById(1L)).thenReturn(message);
        when(wechatAccountService.getById(1L)).thenReturn(account);
        when(wechatReplyService.findReply(1L, "你好")).thenReturn(null);
        when(chatService.chat(1L, "openid123", "你好")).thenReturn("AI智能回复");
        when(wechatApiService.sendCustomerMessage(1L, "openid123", "AI智能回复")).thenReturn(true);

        messageConsumer.handleMessage(message, channel, amqpMessage);

        verify(wechatMessageService).update(eq(1L), any());
        verify(chatService).chat(1L, "openid123", "你好");
        verify(wechatApiService).sendCustomerMessage(1L, "openid123", "AI智能回复");
        verify(channel).basicAck(eq(1L), eq(false));
    }

    @Test
    void shouldUseDefaultReplyWhenAIThrowsException() throws IOException {
        when(wechatMessageService.getById(1L)).thenReturn(message);
        when(wechatAccountService.getById(1L)).thenReturn(account);
        when(wechatReplyService.findReply(1L, "你好")).thenReturn(null);
        when(chatService.chat(1L, "openid123", "你好")).thenThrow(new RuntimeException("AI不可用"));
        when(wechatApiService.sendCustomerMessage(1L, "openid123", "默认回复")).thenReturn(true);

        messageConsumer.handleMessage(message, channel, amqpMessage);

        verify(wechatMessageService).update(eq(1L), any());
        verify(wechatApiService).sendCustomerMessage(1L, "openid123", "默认回复");
        verify(channel).basicAck(eq(1L), eq(false));
    }
}
