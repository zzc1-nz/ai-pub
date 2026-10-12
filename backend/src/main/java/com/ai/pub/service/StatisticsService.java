package com.ai.pub.service;

import com.ai.pub.dto.StatisticsDTO;
import com.ai.pub.repository.WechatMessageRepository;
import com.ai.pub.repository.WechatAccountRepository;
import com.ai.pub.repository.WechatMaterialRepository;
import com.ai.pub.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final WechatMessageRepository messageRepository;
    private final WechatAccountRepository accountRepository;
    private final WechatMaterialRepository materialRepository;
    private final SysUserRepository userRepository;

    public StatisticsDTO getStatistics(Long accountId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        StatisticsDTO dto = new StatisticsDTO();

        // 计数与分组全部下推到数据库。
        // 原实现先 findByAccountId() 取回该公众号的全部消息再在 Java 里 stream 聚合，
        // 消息量上万后必然 OOM；改为聚合查询后结果集只有几行到几十行。
        dto.setTotalMessages(messageRepository.countByAccountId(accountId));
        dto.setTodayMessages(messageRepository.countByAccountIdAndCreatedAtBetween(
                accountId, todayStart, todayEnd));
        dto.setMonthMessages(messageRepository.countByAccountIdAndCreatedAtBetween(
                accountId, monthStart, todayEnd));

        // Top N 常见问题
        List<Object[]> topContents = messageRepository.findTopContents(accountId, 10);
        List<Map<String, Object>> topQuestions = topContents.stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("content", row[0]);
                    map.put("count", row[1]);
                    return map;
                })
                .collect(Collectors.toList());
        dto.setTopQuestions(topQuestions);

        // 回复方式分布（AI / 关键词 / 默认）
        Map<String, Long> replyTypeCounts = toCountMap(messageRepository.countByReplyType(accountId));
        dto.setKeywordReplyCount(replyTypeCounts.getOrDefault("KEYWORD", 0L));
        dto.setAiReplyCount(replyTypeCounts.getOrDefault("AI", 0L));
        dto.setDefaultReplyCount(replyTypeCounts.getOrDefault("DEFAULT", 0L));

        // 消息类型分布
        dto.setMessageTypeDistribution(toCountMap(messageRepository.countByMsgType(accountId)));

        // 每日消息量（趋势图）
        Map<String, Long> dailyCount = new LinkedHashMap<>();
        for (Object[] row : messageRepository.countByDay(accountId)) {
            if (row[0] == null) {
                continue;
            }
            dailyCount.put(toDateString(row[0]), ((Number) row[1]).longValue());
        }
        dto.setDailyMessageCount(dailyCount);

        // 以下四项为全局口径（不区分公众号），与上面的按账号口径不同，前端需注意区分
        dto.setAccountCount(accountRepository.count());
        dto.setMessageCount(messageRepository.count());
        dto.setMaterialCount(materialRepository.count());
        dto.setUserCount(userRepository.count());

        // 素材类型分布：限定到当前公众号，避免与其它按账号口径的指标混用。
        // 同样改为聚合查询，不再把素材全量载入内存。
        Map<String, Long> materialDistribution = new HashMap<>();
        for (Object[] row : materialRepository.countByTypeGrouped(accountId)) {
            if (row[0] != null) {
                materialDistribution.put((String) row[0], ((Number) row[1]).longValue());
            }
        }
        dto.setMaterialTypeDistribution(materialDistribution);

        return dto;
    }

    /**
     * 把 [key, count] 形式的分组查询结果转成 Map。
     */
    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                result.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
            }
        }
        return result;
    }

    /**
     * 数据库返回的日期可能是 java.sql.Date，也可能因方言差异被映射为
     * LocalDate / LocalDateTime / String，这里统一成 yyyy-MM-dd，
     * 以便与前端按本地日期拼出的 key 对齐。
     */
    private String toDateString(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate().toString();
        }
        String text = String.valueOf(value);
        // 形如 "2026-10-12 00:00:00" 或 "2026-10-12" 都能取出日期部分
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }
}
