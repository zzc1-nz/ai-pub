package com.ai.pub.service;

import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.repository.WechatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WechatMessageService {

    private final WechatMessageRepository wechatMessageRepository;

    public WechatMessageService(WechatMessageRepository wechatMessageRepository) {
        this.wechatMessageRepository = wechatMessageRepository;
    }

    public PageResponse<WechatMessage> getPage(Long accountId, String openid, PageQuery request) {
        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<WechatMessage> page;
        boolean hasAccount = accountId != null && accountId > 0;
        boolean hasOpenid = openid != null && !openid.isEmpty();

        if (hasAccount && hasOpenid) {
            page = wechatMessageRepository.findByAccountIdAndOpenid(accountId, openid, pageable);
        } else if (hasAccount) {
            page = wechatMessageRepository.findByAccountId(accountId, pageable);
        } else {
            page = wechatMessageRepository.findAll(pageable);
        }

        return PageResponse.of(page.getContent(), page.getTotalElements(), request.getPageNum(), request.getPageSize());
    }

    public PageResponse<WechatMessage> getPage(Long accountId, String openid, String msgType,
                                                LocalDateTime startTime, LocalDateTime endTime,
                                                PageQuery request) {
        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        boolean hasAccount = accountId != null && accountId > 0;
        boolean hasOpenid = openid != null && !openid.isEmpty();
        boolean hasType = msgType != null && !msgType.isEmpty();
        boolean hasTime = startTime != null && endTime != null;

        Page<WechatMessage> page;
        if (hasAccount && hasType && hasTime) {
            page = wechatMessageRepository.findByAccountIdAndMsgTypeAndCreatedAtBetween(accountId, msgType, startTime, endTime, pageable);
        } else if (hasAccount && hasType) {
            page = wechatMessageRepository.findByAccountIdAndMsgType(accountId, msgType, pageable);
        } else if (hasAccount && hasTime) {
            page = wechatMessageRepository.findByAccountIdAndCreatedAtBetween(accountId, startTime, endTime, pageable);
        } else if (hasAccount && hasOpenid) {
            page = wechatMessageRepository.findByAccountIdAndOpenid(accountId, openid, pageable);
        } else if (hasAccount) {
            page = wechatMessageRepository.findByAccountId(accountId, pageable);
        } else {
            page = wechatMessageRepository.findAll(pageable);
        }

        return PageResponse.of(page.getContent(), page.getTotalElements(), request.getPageNum(), request.getPageSize());
    }

    public List<Object[]> getTopContents(Long accountId, int limit) {
        return wechatMessageRepository.findTopContents(accountId, limit);
    }

    public List<WechatMessage> getConversation(Long accountId, String openid) {
        return wechatMessageRepository.findByAccountIdAndOpenidOrderByCreatedAtAsc(accountId, openid);
    }

    public WechatMessage getById(Long id) {
        return wechatMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
    }

    @Transactional
    public WechatMessage create(WechatMessage message) {
        return wechatMessageRepository.save(message);
    }

    @Transactional
    public void updateReply(Long id, String replyType, String replyContent) {
        WechatMessage message = getById(id);
        message.setReplyType(replyType);
        message.setReplyContent(replyContent);
        wechatMessageRepository.save(message);
    }

    @Transactional
    public void delete(Long id) {
        wechatMessageRepository.deleteById(id);
    }

    @Transactional
    public void processMessage(WechatMessage message) {
        wechatMessageRepository.save(message);
    }

    @Transactional
    public WechatMessage update(Long id, WechatMessage message) {
        WechatMessage existing = getById(id);
        if (message.getContent() != null) {
            existing.setContent(message.getContent());
        }
        if (message.getReplyContent() != null) {
            existing.setReplyContent(message.getReplyContent());
        }
        // replyType 必须一并回写：MessageConsumer 在消息对象上设置了 KEYWORD/AI/DEFAULT，
        // 此处若漏拷贝，reply_type 列恒为 NULL，前端"回复方式"列空白，
        // 且 StatisticsService 的 AI/关键词/默认回复三项统计恒为 0。
        if (message.getReplyType() != null) {
            existing.setReplyType(message.getReplyType());
        }
        return wechatMessageRepository.save(existing);
    }
}
