package com.ai.pub.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatService 意图识别回归测试。
 *
 * 背景：修复前 INTENT_THRESHOLD=0.15，而用户向量（TF-IDF 小值）与语料向量
 * （未归一化原始权重 3~6）尺度不一致，导致即使输入与关键词完全匹配，
 * 余弦值也低于阈值 —— 项目自带 30 条标注集准确率仅 23.3%。
 * 另有 12 个长度≥3 的语料关键词永远无法被 unigram/bigram 分词器命中。
 *
 * 这些测试锁定修复结果，防止阈值/语料库被改回错误状态。
 */
class ChatServiceIntentTest {

    private final ChatService chatService = new ChatService(null, new com.fasterxml.jackson.databind.ObjectMapper(), null);

    private String intentOf(String message) {
        List<String> tokens = chatService.tokenizeForExperiment(message);
        return chatService.classifyIntent(tokens, ChatService.productionIntentThreshold()).intent;
    }

    @Test
    void shouldClassifyEachIntentCorrectly() {
        assertEquals("问候", intentOf("您好，在吗"));
        assertEquals("投诉", intentOf("你们这个太差了，我要投诉"));
        assertEquals("退换", intentOf("我要退款，东西坏了"));
        assertEquals("咨询", intentOf("这个多少钱"));
        assertEquals("感谢", intentOf("谢谢你的帮助"));
    }

    @Test
    void shouldKeepOutOfDomainMessagesAsDefault() {
        assertEquals("默认", intentOf("今天天气不错"));
        assertEquals("默认", intentOf("随便说点什么"));
        assertEquals("默认", intentOf("嗯嗯"));
    }

    /**
     * 修复前：完美匹配"投诉"的余弦值仅 0.117 < 0.15，会被打回默认意图。
     */
    @Test
    void exactKeywordMatchShouldNotBeRejectedByThreshold() {
        assertNotEquals("默认", intentOf("投诉"));
        assertNotEquals("默认", intentOf("退款"));
        assertNotEquals("默认", intentOf("谢谢"));
    }

    /**
     * 修复前语料库含"多少钱""联系方式""早上好""七天无理由"等长度≥3 的关键词，
     * 而分词器只产出 unigram/bigram，这些词永远不会被命中。
     */
    @Test
    void multiCharCorpusKeywordsShouldBeMatchable() {
        // 这些消息原先完全无法命中语料（"多少钱""营业时间"是死词）
        assertNotEquals("默认", intentOf("这个多少钱"));
        assertNotEquals("默认", intentOf("营业时间是几点到几点"));
        assertNotEquals("默认", intentOf("七天无理由退货怎么操作"));
    }

    /**
     * 30 条标注集整体准确率应显著高于修复前的 23.3%。
     */
    @Test
    void accuracyOnLabeledDatasetShouldBeHigh() {
        String[][] dataset = {
            {"你好，请问有什么活动吗", "问候"}, {"您好，在吗", "问候"}, {"hi，有人吗", "问候"},
            {"早上好", "问候"}, {"嗨，我想问一下", "问候"},
            {"你们这个太差了，我要投诉", "投诉"}, {"垃圾产品，非常不满", "投诉"},
            {"服务态度太差了，差评", "投诉"}, {"骗人的，要举报你们", "投诉"}, {"太坑了，我要维权", "投诉"},
            {"我要退款，东西坏了", "退换"}, {"想退货，质量有问题", "退换"}, {"可以换货吗，发错了", "退换"},
            {"七天无理由退货怎么操作", "退换"}, {"售后在哪里，要退换", "退换"},
            {"这个多少钱", "咨询"}, {"怎么买，在哪里下单", "咨询"}, {"你们的地址和电话是什么", "咨询"},
            {"有优惠活动吗，怎么买", "咨询"}, {"营业时间是几点到几点", "咨询"},
            {"谢谢你的帮助", "感谢"}, {"太感谢了，服务很好", "感谢"}, {"非常满意，点赞", "感谢"},
            {"你们的服务很棒", "感谢"}, {"辛苦了，好评", "感谢"},
            {"今天天气不错", "默认"}, {"随便说点什么", "默认"}, {"嗯嗯", "默认"},
            {"哈哈哈哈哈", "默认"}, {"哦", "默认"}
        };

        int correct = 0;
        for (String[] row : dataset) {
            if (intentOf(row[0]).equals(row[1])) correct++;
        }
        double accuracy = 100.0 * correct / dataset.length;
        assertTrue(accuracy >= 90.0,
                "标注集准确率应 ≥90%（修复前为 23.3%），实际=" + accuracy + "%");
    }

    @Test
    void productionThresholdShouldBeTheTunedValue() {
        assertEquals(0.05, ChatService.productionIntentThreshold(), 1e-9);
    }

    /**
     * AI 调用失败时应返回干净话术，不能带 "need_manual:" 前缀
     * （该前缀会原样发给微信用户，且调用方会误判为成功 AI 回复）。
     */
    @Test
    void failureReplyShouldNotLeakInternalPrefix() {
        // chatModel 为 null 会触发内部异常分支
        String reply = chatService.chat(1L, "openid", "你好");
        assertFalse(reply.contains("need_manual:"), "降级话术不应包含内部前缀: " + reply);
        assertTrue(reply.contains("抱歉"), "应返回用户可读的降级话术");
    }
}
