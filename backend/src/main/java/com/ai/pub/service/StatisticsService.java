package com.ai.pub.service;

import com.ai.pub.dto.StatisticsDTO;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.entity.WechatMaterial;
import com.ai.pub.repository.WechatMessageRepository;
import com.ai.pub.repository.WechatAccountRepository;
import com.ai.pub.repository.WechatMaterialRepository;
import com.ai.pub.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
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

        List<WechatMessage> allMessages = messageRepository.findByAccountId(accountId);
        List<WechatMessage> todayMessages = messageRepository.findByAccountIdAndCreatedAtBetween(
                accountId, todayStart, todayEnd);

        StatisticsDTO dto = new StatisticsDTO();
        dto.setTotalMessages((long) allMessages.size());
        dto.setTodayMessages((long) todayMessages.size());

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
                .collect(Collectors.groupingBy(WechatMessage::getMsgType, Collectors.counting()));
        dto.setMessageTypeDistribution(typeDistribution);

        Map<String, Long> dailyCount = allMessages.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getCreatedAt().toLocalDate().toString(),
                        Collectors.counting()
                ));
        dto.setDailyMessageCount(dailyCount);
        
        // 设置前端Dashboard需要的统计数据
        dto.setAccountCount(accountRepository.count());
        dto.setMessageCount(messageRepository.count());
        dto.setMaterialCount(materialRepository.count());
        dto.setUserCount(userRepository.count());

        // 素材类型分布统计
        Map<String, Long> materialDistribution = materialRepository.findAll().stream()
                .collect(Collectors.groupingBy(WechatMaterial::getType, Collectors.counting()));
        dto.setMaterialTypeDistribution(materialDistribution);

        return dto;
    }
}
