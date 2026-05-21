package com.ai.pub.service;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatReplyRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatReplyServiceTest {

    @Mock
    private WechatReplyRuleService wechatReplyRuleService;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private WechatReplyService wechatReplyService;

    private WechatAccount account;
    private WechatReplyRule helloRule;

    @BeforeEach
    void setUp() {
        account = new WechatAccount();
        account.setId(1L);
        account.setName("测试号");
        account.setAiEnabled(true);
        account.setDefaultReply("默认回复");

        helloRule = new WechatReplyRule();
        helloRule.setId(1L);
        helloRule.setName("欢迎语");
        helloRule.setKeyword("你好,hello,hi");
        helloRule.setMatchType("CONTAINS");
        helloRule.setReplyType("text");
        helloRule.setReplyContent("您好！欢迎使用智能客服。");

        WechatReplyRule exactRule = new WechatReplyRule();
        exactRule.setId(2L);
        exactRule.setName("精确匹配");
        exactRule.setKeyword("帮助");
        exactRule.setMatchType("EXACT");
        exactRule.setReplyType("text");
        exactRule.setReplyContent("精确匹配回复");
    }

    @Test
    void shouldMatchByContainsKeyword() {
        when(wechatReplyRuleService.getActiveRules(1L)).thenReturn(List.of(helloRule));

        String reply = wechatReplyService.processMessage(account, "openid1", "你好啊");

        assertEquals("您好！欢迎使用智能客服。", reply);
    }

    @Test
    void shouldNotMatchWhenKeywordNotFound() {
        when(wechatReplyRuleService.getActiveRules(1L)).thenReturn(List.of(helloRule));
        when(chatService.chat(1L, "openid1", "完全不匹配的内容")).thenReturn("AI 回复");

        String reply = wechatReplyService.processMessage(account, "openid1", "完全不匹配的内容");

        assertEquals("AI 回复", reply);
    }

    @Test
    void shouldUseAIWhenNoRulesExist() {
        when(wechatReplyRuleService.getActiveRules(1L)).thenReturn(Collections.emptyList());
        when(chatService.chat(1L, "openid1", "任意消息")).thenReturn("AI 生成回复");

        String reply = wechatReplyService.processMessage(account, "openid1", "任意消息");

        assertEquals("AI 生成回复", reply);
        verify(chatService).chat(1L, "openid1", "任意消息");
    }

    @Test
    void shouldUseDefaultReplyWhenAIDisabled() {
        account.setAiEnabled(false);
        when(wechatReplyRuleService.getActiveRules(1L)).thenReturn(Collections.emptyList());

        String reply = wechatReplyService.processMessage(account, "openid1", "任意消息");

        assertEquals("默认回复", reply);
        verify(chatService, never()).chat(anyLong(), anyString(), anyString());
    }

    @Test
    void findReplyShouldReturnNullWhenNoMatch() {
        when(wechatReplyRuleService.getActiveRules(1L)).thenReturn(Collections.emptyList());

        String result = wechatReplyService.findReply(1L, "无匹配内容");

        assertNull(result);
    }

    @Test
    void findReplyShouldReturnContentWhenMatched() {
        when(wechatReplyRuleService.getActiveRules(1L)).thenReturn(List.of(helloRule));

        String result = wechatReplyService.findReply(1L, "你好");

        assertEquals("您好！欢迎使用智能客服。", result);
    }
}
