package com.ai.pub.dto;

import java.util.List;
import java.util.Map;

public class StatisticsDTO {
    private Long totalMessages;
    private Long todayMessages;
    private Long monthMessages;
    private List<Map<String, Object>> topQuestions;
    private Long keywordReplyCount;
    private Long aiReplyCount;
    private Long defaultReplyCount;
    private Map<String, Long> messageTypeDistribution;
    private Map<String, Long> dailyMessageCount;
    private Long accountCount;
    private Long messageCount;
    private Long materialCount;
    private Long userCount;
    private Map<String, Long> materialTypeDistribution;

    public StatisticsDTO() {}

    public Long getTotalMessages() { return totalMessages; }
    public void setTotalMessages(Long totalMessages) { this.totalMessages = totalMessages; }
    public Long getTodayMessages() { return todayMessages; }
    public void setTodayMessages(Long todayMessages) { this.todayMessages = todayMessages; }
    public Long getMonthMessages() { return monthMessages; }
    public void setMonthMessages(Long monthMessages) { this.monthMessages = monthMessages; }
    public List<Map<String, Object>> getTopQuestions() { return topQuestions; }
    public void setTopQuestions(List<Map<String, Object>> topQuestions) { this.topQuestions = topQuestions; }
    public Long getKeywordReplyCount() { return keywordReplyCount; }
    public void setKeywordReplyCount(Long keywordReplyCount) { this.keywordReplyCount = keywordReplyCount; }
    public Long getAiReplyCount() { return aiReplyCount; }
    public void setAiReplyCount(Long aiReplyCount) { this.aiReplyCount = aiReplyCount; }
    public Long getDefaultReplyCount() { return defaultReplyCount; }
    public void setDefaultReplyCount(Long defaultReplyCount) { this.defaultReplyCount = defaultReplyCount; }
    public Map<String, Long> getMessageTypeDistribution() { return messageTypeDistribution; }
    public void setMessageTypeDistribution(Map<String, Long> messageTypeDistribution) { this.messageTypeDistribution = messageTypeDistribution; }
    public Map<String, Long> getDailyMessageCount() { return dailyMessageCount; }
    public void setDailyMessageCount(Map<String, Long> dailyMessageCount) { this.dailyMessageCount = dailyMessageCount; }
    public Long getAccountCount() { return accountCount; }
    public void setAccountCount(Long accountCount) { this.accountCount = accountCount; }
    public Long getMessageCount() { return messageCount; }
    public void setMessageCount(Long messageCount) { this.messageCount = messageCount; }
    public Long getMaterialCount() { return materialCount; }
    public void setMaterialCount(Long materialCount) { this.materialCount = materialCount; }
    public Long getUserCount() { return userCount; }
    public void setUserCount(Long userCount) { this.userCount = userCount; }
    public Map<String, Long> getMaterialTypeDistribution() { return materialTypeDistribution; }
    public void setMaterialTypeDistribution(Map<String, Long> materialTypeDistribution) { this.materialTypeDistribution = materialTypeDistribution; }
}
