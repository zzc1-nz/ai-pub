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

    Page<WechatMessage> findByAccountIdAndMsgType(Long accountId, String msgType, Pageable pageable);

    Page<WechatMessage> findByAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<WechatMessage> findByAccountIdAndMsgTypeAndCreatedAtBetween(Long accountId, String msgType, LocalDateTime start, LocalDateTime end, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT m.content, COUNT(m) as cnt FROM WechatMessage m WHERE m.accountId = :accountId AND m.msgType = 'text' AND m.content IS NOT NULL GROUP BY m.content ORDER BY cnt DESC LIMIT :limit")
    List<Object[]> findTopContents(@org.springframework.data.repository.query.Param("accountId") Long accountId, @org.springframework.data.repository.query.Param("limit") int limit);
}
