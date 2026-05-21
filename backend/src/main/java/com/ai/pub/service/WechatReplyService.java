package com.ai.pub.service;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatReplyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class WechatReplyService {

    private static final Logger log = LoggerFactory.getLogger(WechatReplyService.class);

    private final WechatReplyRuleService wechatReplyRuleService;
    private final ChatService chatService;

    public WechatReplyService(WechatReplyRuleService wechatReplyRuleService, ChatService chatService) {
        this.wechatReplyRuleService = wechatReplyRuleService;
        this.chatService = chatService;
    }

    public String processMessage(WechatAccount account, String openid, String content) {
        List<WechatReplyRule> rules = wechatReplyRuleService.getActiveRules(account.getId());

        for (WechatReplyRule rule : rules) {
            if (matchRule(rule, content)) {
                log.info("匹配到回复规则: {}", rule.getName());
                return getReplyContent(rule);
            }
        }

        if (Boolean.TRUE.equals(account.getAiEnabled())) {
            log.info("使用AI回复");
            return chatService.chat(account.getId(), openid, content);
        }

        return account.getDefaultReply() != null ? account.getDefaultReply() : "感谢您的留言！";
    }

    private boolean matchRule(WechatReplyRule rule, String content) {
        if (content == null) {
            return false;
        }

        String keyword = rule.getKeyword();
        String matchType = rule.getMatchType();

        if (matchType == null) {
            matchType = "CONTAINS";
        }

        // REGEX mode: keyword is treated as a regex pattern directly
        if ("REGEX".equals(matchType)) {
            try {
                return Pattern.compile(keyword).matcher(content).find();
            } catch (PatternSyntaxException e) {
                log.warn("正则表达式语法错误: {}", keyword);
                return false;
            }
        }

        // FUZZY mode: tokenize and check overlap for Chinese text
        if ("FUZZY".equals(matchType)) {
            return fuzzyMatch(keyword, content);
        }

        // ALL_CONTAINS mode: all comma-separated keywords must match
        if ("ALL_CONTAINS".equals(matchType)) {
            String[] keywords = keyword.split(",");
            for (String kw : keywords) {
                kw = kw.trim();
                if (!kw.isEmpty() && !content.contains(kw)) {
                    return false;
                }
            }
            return true;
        }

        String[] keywords = keyword.split(",");

        for (String kw : keywords) {
            kw = kw.trim();
            if (kw.isEmpty()) {
                continue;
            }

            switch (matchType) {
                case "EXACT":
                    if (content.equals(kw)) {
                        return true;
                    }
                    break;
                case "STARTS_WITH":
                    if (content.startsWith(kw)) {
                        return true;
                    }
                    break;
                case "ENDS_WITH":
                    if (content.endsWith(kw)) {
                        return true;
                    }
                    break;
                case "CONTAINS":
                default:
                    if (content.contains(kw)) {
                        return true;
                    }
                    break;
            }
        }

        return false;
    }

    private boolean fuzzyMatch(String keyword, String content) {
        String normalizedKeyword = keyword.toLowerCase().trim();
        String normalizedContent = content.toLowerCase().trim();

        // 完全包含的情况直接命中
        if (normalizedContent.contains(normalizedKeyword) || normalizedKeyword.contains(normalizedContent)) {
            return true;
        }

        // 对中文进行单字级别重叠度计算
        double overlapRatio = charOverlapRatio(normalizedKeyword, normalizedContent);
        // 关键词长度越短，要求重叠度越高
        double threshold = normalizedKeyword.length() <= 2 ? 0.8 : 0.5;

        return overlapRatio >= threshold;
    }

    private double charOverlapRatio(String keyword, String text) {
        int matchCount = 0;
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c != ' ' && text.indexOf(c) >= 0) {
                matchCount++;
            }
        }
        return (double) matchCount / keyword.length();
    }

    private String getReplyContent(WechatReplyRule rule) {
        String replyType = rule.getReplyType();
        
        if ("TEXT".equals(replyType)) {
            return rule.getReplyContent();
        }
        
        return rule.getReplyContent();
    }

    public String findReply(Long accountId, String content) {
        log.info("findReply: accountId={}, content={}", accountId, content);
        List<WechatReplyRule> rules = wechatReplyRuleService.getActiveRules(accountId);
        log.info("findReply: 找到 {} 条激活规则", rules.size());
        
        for (WechatReplyRule rule : rules) {
            log.info("检查规则: {} (关键词: {}, 匹配类型: {})", rule.getName(), rule.getKeyword(), rule.getMatchType());
            if (matchRule(rule, content)) {
                log.info("匹配到回复规则: {}", rule.getName());
                return getReplyContent(rule);
            }
        }
        
        log.info("未匹配到任何回复规则");
        return null;
    }
}
