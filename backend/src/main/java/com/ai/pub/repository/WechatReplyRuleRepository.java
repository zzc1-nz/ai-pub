package com.ai.pub.repository;

import com.ai.pub.entity.WechatReplyRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WechatReplyRuleRepository extends JpaRepository<WechatReplyRule, Long> {

    Page<WechatReplyRule> findByAccountId(Long accountId, Pageable pageable);

    List<WechatReplyRule> findByAccountIdAndStatusOrderBySortOrderAsc(Long accountId, Integer status);
}
