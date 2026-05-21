package com.ai.pub.service;

import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatReplyRule;
import com.ai.pub.repository.WechatReplyRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WechatReplyRuleService {

    private final WechatReplyRuleRepository wechatReplyRuleRepository;

    public WechatReplyRuleService(WechatReplyRuleRepository wechatReplyRuleRepository) {
        this.wechatReplyRuleRepository = wechatReplyRuleRepository;
    }

    public PageResponse<WechatReplyRule> getPage(Long accountId, PageQuery request) {
        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<WechatReplyRule> page = wechatReplyRuleRepository.findByAccountId(accountId, pageable);

        return PageResponse.of(page.getContent(), page.getTotalElements(), request.getPageNum(), request.getPageSize());
    }

    public List<WechatReplyRule> getActiveRules(Long accountId) {
        return wechatReplyRuleRepository.findByAccountIdAndStatusOrderBySortOrderAsc(accountId, 1);
    }

    public WechatReplyRule getById(Long id) {
        return wechatReplyRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("回复规则不存在"));
    }

    @Transactional
    public WechatReplyRule create(WechatReplyRule rule) {
        return wechatReplyRuleRepository.save(rule);
    }

    @Transactional
    public WechatReplyRule update(Long id, WechatReplyRule rule) {
        WechatReplyRule existing = getById(id);

        existing.setName(rule.getName());
        existing.setKeyword(rule.getKeyword());
        existing.setMatchType(rule.getMatchType());
        existing.setReplyType(rule.getReplyType());
        existing.setReplyContent(rule.getReplyContent());
        existing.setReplyMaterialId(rule.getReplyMaterialId());
        existing.setSortOrder(rule.getSortOrder());
        existing.setStatus(rule.getStatus());

        return wechatReplyRuleRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        wechatReplyRuleRepository.deleteById(id);
    }
}
