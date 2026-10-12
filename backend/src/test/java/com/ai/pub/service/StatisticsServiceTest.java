package com.ai.pub.service;

import com.ai.pub.dto.StatisticsDTO;
import com.ai.pub.entity.WechatMaterial;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.repository.WechatMaterialRepository;
import com.ai.pub.repository.WechatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计聚合回归测试。
 *
 * 背景：StatisticsService 原先用 findByAccountId() 把该公众号的全部消息读进内存
 * 再 stream 聚合（消息量上万必然 OOM），现改为把计数/分组下推到数据库。
 * 本测试用真实 H2 库验证聚合结果正确，同时确保新 JPQL 能真正执行
 * （仅靠 Spring 上下文启动无法覆盖到查询本身）。
 */
@SpringBootTest
class StatisticsServiceTest {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private WechatMessageRepository messageRepository;

    @Autowired
    private WechatMaterialRepository materialRepository;

    @Autowired
    private com.ai.pub.repository.WechatAccountRepository accountRepository;

    /**
     * 注意：WechatAccount.id 是 GenerationType.IDENTITY，手工 set 的 id 会被忽略，
     * 必须用 save() 返回的对象取真实主键，否则外键约束会失败。
     */
    private Long accountId;
    private Long otherAccountId;

    @BeforeEach
    void setUp() {
        // wechat_message / wechat_material 对 wechat_account 有外键约束，
        // 需要先建出所属公众号
        messageRepository.deleteAll();
        materialRepository.deleteAll();
        accountRepository.deleteAll();

        accountId = createAccount("wx_stats_test");
        otherAccountId = createAccount("wx_stats_other");
    }

    private Long createAccount(String appId) {
        com.ai.pub.entity.WechatAccount a = new com.ai.pub.entity.WechatAccount();
        a.setName("统计测试号-" + appId);
        a.setAppId(appId);
        a.setAppSecret("secret");
        a.setToken("token");
        a.setStatus(1);
        return accountRepository.save(a).getId();
    }

    private WechatMessage message(String content, String msgType, String replyType, LocalDateTime createdAt) {
        WechatMessage m = new WechatMessage();
        m.setAccountId(accountId);
        m.setOpenid("openid_test");
        m.setContent(content);
        m.setMsgType(msgType);
        m.setReplyType(replyType);
        m.setReplyContent("回复内容");
        m.setCreatedAt(createdAt);
        return messageRepository.save(m);
    }

    @Test
    void shouldAggregateCountsAndDistributions() {
        LocalDateTime now = LocalDateTime.now();

        message("你好", "text", "KEYWORD", now);
        message("退款", "text", "KEYWORD", now);
        message("多少钱", "text", "AI", now);
        message("谢谢", "text", "AI", now);
        message("嗯", "text", "DEFAULT", now);
        message("[图片]", "image", null, now);
        // 其它公众号的数据不应计入
        WechatMessage other = new WechatMessage();
        other.setAccountId(otherAccountId);
        other.setOpenid("other");
        other.setMsgType("text");
        other.setContent("别人的消息");
        other.setCreatedAt(now);
        messageRepository.save(other);

        StatisticsDTO dto = statisticsService.getStatistics(accountId);

        assertEquals(6L, dto.getTotalMessages(), "总数应只统计当前公众号");
        assertEquals(6L, dto.getTodayMessages());
        assertEquals(6L, dto.getMonthMessages());

        // 回复方式分布（原先因 replyType 未落库而恒为 0）
        assertEquals(2L, dto.getAiReplyCount(), "AI 回复数");
        assertEquals(2L, dto.getKeywordReplyCount(), "关键词回复数");
        assertEquals(1L, dto.getDefaultReplyCount(), "默认回复数");

        // 消息类型分布
        assertEquals(5L, dto.getMessageTypeDistribution().get("text"));
        assertEquals(1L, dto.getMessageTypeDistribution().get("image"));

        // 每日消息量：key 必须是 yyyy-MM-dd 且与前端本地日期对齐
        String today = now.toLocalDate().toString();
        assertEquals(6L, dto.getDailyMessageCount().get(today),
                "dailyMessageCount 应包含今天的 6 条，实际 keys=" + dto.getDailyMessageCount().keySet());

        // Top 常见问题（只统计 text 类型）
        assertFalse(dto.getTopQuestions().isEmpty());
        assertEquals("你好", dto.getTopQuestions().get(0).get("content"));
    }

    @Test
    void shouldReturnZeroesWhenNoData() {
        StatisticsDTO dto = statisticsService.getStatistics(accountId);

        assertEquals(0L, dto.getTotalMessages());
        assertEquals(0L, dto.getTodayMessages());
        assertEquals(0L, dto.getAiReplyCount());
        assertEquals(0L, dto.getKeywordReplyCount());
        assertEquals(0L, dto.getDefaultReplyCount());
        assertTrue(dto.getTopQuestions().isEmpty());
    }

    @Test
    void shouldAggregateMaterialTypeDistribution() {
        for (String type : new String[]{"image", "image", "voice"}) {
            WechatMaterial m = new WechatMaterial();
            m.setAccountId(accountId);
            m.setType(type);
            m.setTitle("素材");
            materialRepository.save(m);
        }
        // 其它公众号的素材不应计入
        WechatMaterial other = new WechatMaterial();
        other.setAccountId(otherAccountId);
        other.setType("video");
        other.setTitle("别人的素材");
        materialRepository.save(other);

        StatisticsDTO dto = statisticsService.getStatistics(accountId);

        assertEquals(2L, dto.getMaterialTypeDistribution().get("image"));
        assertEquals(1L, dto.getMaterialTypeDistribution().get("voice"));
        assertNull(dto.getMaterialTypeDistribution().get("video"),
                "素材分布应限定到当前公众号");
    }
}
