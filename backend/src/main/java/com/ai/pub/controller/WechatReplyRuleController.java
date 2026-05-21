package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatReplyRule;
import com.ai.pub.service.WechatReplyRuleService;
import com.ai.pub.service.WechatReplyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wechat/reply-rules")
public class WechatReplyRuleController {

    private final WechatReplyRuleService wechatReplyRuleService;
    private final WechatReplyService wechatReplyService;

    public WechatReplyRuleController(WechatReplyRuleService wechatReplyRuleService, WechatReplyService wechatReplyService) {
        this.wechatReplyRuleService = wechatReplyRuleService;
        this.wechatReplyService = wechatReplyService;
    }

    @GetMapping
    public Result<PageResponse<WechatReplyRule>> getPage(
            @RequestParam(required = false) Long accountId,
            PageQuery request) {
        PageResponse<WechatReplyRule> page = wechatReplyRuleService.getPage(accountId != null ? accountId : 1L, request);
        return Result.success(page);
    }

    @GetMapping("/account/{accountId}")
    public Result<PageResponse<WechatReplyRule>> getPageByAccountId(
            @PathVariable Long accountId,
            PageQuery request) {
        PageResponse<WechatReplyRule> page = wechatReplyRuleService.getPage(accountId, request);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<WechatReplyRule> getById(@PathVariable Long id) {
        WechatReplyRule rule = wechatReplyRuleService.getById(id);
        return Result.success(rule);
    }

    @PostMapping
    public Result<WechatReplyRule> create(@RequestBody WechatReplyRule rule) {
        WechatReplyRule created = wechatReplyRuleService.create(rule);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<WechatReplyRule> update(@PathVariable Long id, @RequestBody WechatReplyRule rule) {
        WechatReplyRule updated = wechatReplyRuleService.update(id, rule);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wechatReplyRuleService.delete(id);
        return Result.success();
    }

    @GetMapping("/test/match")
    public Result<String> testMatch(
            @RequestParam Long accountId,
            @RequestParam String content) {
        String reply = wechatReplyService.findReply(accountId, content);
        if (reply != null) {
            return Result.success("匹配成功: " + reply);
        } else {
            return Result.success("未匹配到规则");
        }
    }
}
