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

    /** 按公众号统计消息总数（数据库侧 COUNT，避免把实体载入内存） */
    long countByAccountId(Long accountId);

    /** 按公众号 + 时间区间统计消息数 */
    long countByAccountIdAndCreatedAtBetween(Long accountId, LocalDateTime start, LocalDateTime end);

    @org.springframework.data.jpa.repository.Query("SELECT m.content, COUNT(m) as cnt FROM WechatMessage m WHERE m.accountId = :accountId AND m.msgType = 'text' AND m.content IS NOT NULL GROUP BY m.content ORDER BY cnt DESC LIMIT :limit")
    List<Object[]> findTopContents(@org.springframework.data.repository.query.Param("accountId") Long accountId, @org.springframework.data.repository.query.Param("limit") int limit);

    // ========================================================================
    //  统计聚合查询
    //
    //  原先 StatisticsService 用 findByAccountId() 把该公众号的全部消息读进
    //  内存再 stream 聚合，消息量上万后必然 OOM。以下查询把聚合下推到数据库，
    //  只返回几十行结果。
    // ========================================================================

    /** 按 replyType 分组计数，用于 AI / 关键词 / 默认回复占比 */
    @org.springframework.data.jpa.repository.Query("SELECT m.replyType, COUNT(m) FROM WechatMessage m WHERE m.accountId = :accountId GROUP BY m.replyType")
    List<Object[]> countByReplyType(@org.springframework.data.repository.query.Param("accountId") Long accountId);

    /** 按 msgType 分组计数，用于消息类型分布 */
    @org.springframework.data.jpa.repository.Query("SELECT m.msgType, COUNT(m) FROM WechatMessage m WHERE m.accountId = :accountId AND m.msgType IS NOT NULL GROUP BY m.msgType")
    List<Object[]> countByMsgType(@org.springframework.data.repository.query.Param("accountId") Long accountId);

    /**
     * 按日期分组计数，用于消息趋势图。
     * 结果每天一行（而非每条消息一行），即使运行数年也只有千行量级。
     * 用 FUNCTION('DATE', ...) 交给数据库按日期归并，避免在 Java 侧解析时间戳。
     */
    @org.springframework.data.jpa.repository.Query("SELECT FUNCTION('DATE', m.createdAt), COUNT(m) FROM WechatMessage m WHERE m.accountId = :accountId AND m.createdAt IS NOT NULL GROUP BY FUNCTION('DATE', m.createdAt)")
    List<Object[]> countByDay(@org.springframework.data.repository.query.Param("accountId") Long accountId);
}
