package com.ai.pub.service;

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
import static org.mockito.Mockito.when;

/**
 * 关键词规则匹配测试。
 *
 * 说明：原先这里用 processMessage() 测试"规则 → AI → 默认回复"的完整流程，
 * 但该方法与 MessageConsumer 的流程重复且已删除，相关用例改为直接覆盖
 * findReply() 的匹配语义（AI 兜底由 MessageConsumerTest 负责）。
 *
 * 同时补齐原先缺失的覆盖：原测试构造了 exactRule 却从未使用，
 * EXACT 模式实际上没有被测到。
 */
@ExtendWith(MockitoExtension.class)
class WechatReplyServiceTest {

    @Mock
    private WechatReplyRuleService wechatReplyRuleService;

    @InjectMocks
    private WechatReplyService wechatReplyService;

    private WechatReplyRule containsRule;
    private WechatReplyRule exactRule;

    private static final Long ACCOUNT_ID = 1L;

    @BeforeEach
    void setUp() {
        containsRule = rule("欢迎语", "你好,hello,hi", "CONTAINS", "您好！欢迎使用智能客服。");
        exactRule = rule("精确匹配", "帮助", "EXACT", "精确匹配回复");
    }

    private WechatReplyRule rule(String name, String keyword, String matchType, String reply) {
        WechatReplyRule r = new WechatReplyRule();
        r.setName(name);
        r.setKeyword(keyword);
        r.setMatchType(matchType);
        r.setReplyType("TEXT");
        r.setReplyContent(reply);
        return r;
    }

    @Test
    void shouldMatchByContainsKeyword() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID)).thenReturn(List.of(containsRule));

        assertEquals("您好！欢迎使用智能客服。", wechatReplyService.findReply(ACCOUNT_ID, "你好啊"));
    }

    @Test
    void shouldReturnNullWhenNoRuleMatches() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID)).thenReturn(List.of(containsRule));

        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "完全不匹配的内容"));
    }

    @Test
    void shouldReturnNullWhenNoRulesExist() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID)).thenReturn(Collections.emptyList());

        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "任意消息"));
    }

    // ---- EXACT 模式（原测试构造了规则但从未断言，属覆盖缺口） ----

    @Test
    void exactModeShouldMatchOnlyFullEquality() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID)).thenReturn(List.of(exactRule));

        assertEquals("精确匹配回复", wechatReplyService.findReply(ACCOUNT_ID, "帮助"),
                "完全相等应命中");
        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "请帮助我"),
                "EXACT 模式下包含关键词不应命中");
        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "帮助我"),
                "EXACT 模式下前缀相同不应命中");
    }

    // ---- 其它匹配模式 ----

    @Test
    void startsWithAndEndsWithMode() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID))
                .thenReturn(List.of(rule("前缀", "查询", "STARTS_WITH", "前缀回复")));
        assertEquals("前缀回复", wechatReplyService.findReply(ACCOUNT_ID, "查询订单"));
        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "我要查询"));

        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID))
                .thenReturn(List.of(rule("后缀", "吗", "ENDS_WITH", "后缀回复")));
        assertEquals("后缀回复", wechatReplyService.findReply(ACCOUNT_ID, "有货吗"));
        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "吗啡"));
    }

    @Test
    void allContainsModeRequiresEveryKeyword() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID))
                .thenReturn(List.of(rule("全含", "退款,质量", "ALL_CONTAINS", "全含回复")));

        assertEquals("全含回复", wechatReplyService.findReply(ACCOUNT_ID, "我要退款，质量有问题"));
        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "我要退款"),
                "只命中部分关键词时不应匹配");
    }

    @Test
    void regexModeShouldMatchPattern() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID))
                .thenReturn(List.of(rule("正则", "\\d{11}", "REGEX", "正则回复")));

        assertEquals("正则回复", wechatReplyService.findReply(ACCOUNT_ID, "我的手机号是13800138000"));
        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "没有数字"));
    }

    @Test
    void regexModeShouldNotBreakOnInvalidPattern() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID))
                .thenReturn(List.of(rule("坏正则", "([unclosed", "REGEX", "不该命中")));

        // 语法错误应被捕获并视为不匹配，而不是抛异常
        assertNull(wechatReplyService.findReply(ACCOUNT_ID, "任意内容"));
    }

    @Test
    void nullContentShouldNotMatch() {
        when(wechatReplyRuleService.getActiveRules(ACCOUNT_ID)).thenReturn(List.of(containsRule));

        assertNull(wechatReplyService.findReply(ACCOUNT_ID, null));
    }
}
