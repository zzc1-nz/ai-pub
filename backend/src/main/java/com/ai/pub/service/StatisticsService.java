package com.ai.pub.service;

import com.ai.pub.dto.StatisticsDTO;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.entity.WechatMaterial;
import com.ai.pub.repository.WechatMessageRepository;
import com.ai.pub.repository.WechatAccountRepository;
import com.ai.pub.repository.WechatMaterialRepository;
import com.ai.pub.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
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

        List<WechatMessage> allMessages = messageRepository.findByAccountId(accountId);
        List<WechatMessage> todayMessages = messageRepository.findByAccountIdAndCreatedAtBetween(
                accountId, todayStart, todayEnd);
        List<WechatMessage> monthMessages = messageRepository.findByAccountIdAndCreatedAtBetween(
                accountId, monthStart, todayEnd);

        StatisticsDTO dto = new StatisticsDTO();
        dto.setTotalMessages((long) allMessages.size());
        dto.setTodayMessages((long) todayMessages.size());
        dto.setMonthMessages((long) monthMessages.size());

        // Top N 常见问题
        List<Object[]> topContents = messageRepository.findTopContents(accountId, 10);
        List<Map<String, Object>> topQuestions = topContents.stream()
                .map(row -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("content", row[0]);
                    map.put("count", row[1]);
                    return map;
                })
                .collect(Collectors.toList());
        dto.setTopQuestions(topQuestions);

        dto.setKeywordReplyCount(allMessages.stream()
                .filter(m -> "KEYWORD".equals(m.getReplyType()))
                .count());
        dto.setAiReplyCount(allMessages.stream()
                .filter(m -> "AI".equals(m.getReplyType()))
                .count());
        dto.setDefaultReplyCount(allMessages.stream()
                .filter(m -> "DEFAULT".equals(m.getReplyType()))
                .count());

        Map<String, Long> typeDistribution = allMessages.stream()
                .filter(m -> m.getMsgType() != null)
                .collect(Collectors.groupingBy(WechatMessage::getMsgType, Collectors.counting()));
        dto.setMessageTypeDistribution(typeDistribution);

        // createdAt 理论上由 @CreationTimestamp 保证非空，但历史数据/异常写入可能为 null，
        // 直接 toLocalDate() 会抛 NPE 导致整个统计接口 500，这里显式过滤。
        Map<String, Long> dailyCount = allMessages.stream()
                .filter(m -> m.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getCreatedAt().toLocalDate().toString(),
                        Collectors.counting()
                ));
        dto.setDailyMessageCount(dailyCount);

        // 以下四项为全局口径（不区分公众号），与上面的按账号口径不同，前端需注意区分
        dto.setAccountCount(accountRepository.count());
        dto.setMessageCount(messageRepository.count());
        dto.setMaterialCount(materialRepository.count());
        dto.setUserCount(userRepository.count());

        // 素材类型分布：同样限定到当前公众号，避免与其它按账号口径的指标混用
        Map<String, Long> materialDistribution = materialRepository.findByAccountId(accountId, Pageable.unpaged())
                .getContent().stream()
                .filter(m -> m.getType() != null)
                .collect(Collectors.groupingBy(WechatMaterial::getType, Collectors.counting()));
        dto.setMaterialTypeDistribution(materialDistribution);

        return dto;
    }
}
