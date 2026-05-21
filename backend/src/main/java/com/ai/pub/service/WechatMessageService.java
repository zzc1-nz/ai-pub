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
        return wechatMessageRepository.save(existing);
    }
}
