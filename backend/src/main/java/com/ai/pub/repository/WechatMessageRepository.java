package com.ai.pub.repository;

import com.ai.pub.entity.WechatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WechatMessageRepository extends JpaRepository<WechatMessage, Long> {

    Optional<WechatMessage> findByMsgId(String msgId);

    List<WechatMessage> findByAccountId(Long accountId);

    Page<WechatMessage> findByAccountId(Long accountId, Pageable pageable);

    Page<WechatMessage> findByAccountIdAndOpenid(Long accountId, String openid, Pageable pageable);

    List<WechatMessage> findByAccountIdAndOpenidOrderByCreatedAtAsc(Long accountId, String openid);

    List<WechatMessage> findByAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start, LocalDateTime end);
}
