package com.ai.pub.service;

import com.ai.pub.entity.WechatReplyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 关键词自动回复规则匹配。
 *
 * 说明：本类原先还提供一个 processMessage(account, openid, content)，其中重复实现了
 * "匹配规则 → AI 兜底 → 默认回复"的完整流程，与 MessageConsumer.processMessage()
 * 逻辑重叠、无生产调用方（仅测试使用），已删除以避免两处逻辑漂移。
 * 线上实际链路是 MessageConsumer 调用 findReply()，未命中时由它自己走 AI / 默认回复。
 */
@Service
public class WechatReplyService {

    private static final Logger log = LoggerFactory.getLogger(WechatReplyService.class);

    private final WechatReplyRuleService wechatReplyRuleService;

    public WechatReplyService(WechatReplyRuleService wechatReplyRuleService) {
        this.wechatReplyRuleService = wechatReplyRuleService;
    }

    /**
     * 按当前公众号的启用规则匹配回复内容。
     *
     * @return 命中的回复内容；未命中返回 null（由调用方决定走 AI 还是默认回复）
     */
    public String findReply(Long accountId, String content) {
        List<WechatReplyRule> rules = wechatReplyRuleService.getActiveRules(accountId);

        for (WechatReplyRule rule : rules) {
            if (matchRule(rule, content)) {
                log.info("匹配到回复规则: {} (关键词: {})", rule.getName(), rule.getKeyword());
                return rule.getReplyContent();
            }
        }

        log.debug("未匹配到任何回复规则: accountId={}", accountId);
        return null;
    }

    /**
     * 判断单条规则是否命中。支持 CONTAINS / EXACT / STARTS_WITH / ENDS_WITH /
     * ALL_CONTAINS / FUZZY / REGEX 七种模式。
     */
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
}
