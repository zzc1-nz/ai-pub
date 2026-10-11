package com.ai.pub.service;

import com.ai.pub.entity.WechatMessage;
import com.ai.pub.repository.WechatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * WechatMessageService 回归测试。
 *
 * 背景：MessageConsumer 在消息对象上设置了 replyType（KEYWORD/AI/DEFAULT），
 * 但 update() 原先只拷贝 content 与 replyContent，漏掉 replyType，
 * 导致 wechat_message.reply_type 恒为 NULL —— 前端"回复方式"列空白，
 * 且 StatisticsService 的 AI/关键词/默认回复三项统计恒为 0。
 */
@ExtendWith(MockitoExtension.class)
class WechatMessageServiceTest {

    @Mock
    private WechatMessageRepository repository;

    @InjectMocks
    private WechatMessageService service;

    @Test
    void updateShouldPersistReplyType() {
        WechatMessage existing = new WechatMessage();
        existing.setId(1L);
        existing.setContent("你好");
        existing.setAccountId(1L);
        existing.setOpenid("openid1");
        existing.setMsgType("text");

        WechatMessage incoming = new WechatMessage();
        incoming.setId(1L);
        incoming.setReplyContent("AI回复内容");
        incoming.setReplyType("AI");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        WechatMessage updated = service.update(1L, incoming);

        assertEquals("AI", updated.getReplyType(), "replyType 必须落库，否则统计与前端展示均为空");
        assertEquals("AI回复内容", updated.getReplyContent());
    }

    @Test
    void updateShouldNotClearExistingFieldsWhenIncomingIsNull() {
        WechatMessage existing = new WechatMessage();
        existing.setId(2L);
        existing.setContent("原始内容");
        existing.setReplyContent("已有回复");
        existing.setReplyType("KEYWORD");

        WechatMessage incoming = new WechatMessage();
        incoming.setId(2L);
        // content / replyContent / replyType 全为 null

        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        WechatMessage updated = service.update(2L, incoming);

        assertEquals("原始内容", updated.getContent());
        assertEquals("已有回复", updated.getReplyContent());
        assertEquals("KEYWORD", updated.getReplyType());
    }
}
