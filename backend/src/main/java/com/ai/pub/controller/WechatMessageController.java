package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatMessage;
import com.ai.pub.service.WechatMessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wechat/messages")
public class WechatMessageController {

    private final WechatMessageService wechatMessageService;

    public WechatMessageController(WechatMessageService wechatMessageService) {
        this.wechatMessageService = wechatMessageService;
    }

    @GetMapping
    public Result<PageResponse<WechatMessage>> getPage(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String openid,
            PageQuery request) {
        PageResponse<WechatMessage> page = wechatMessageService.getPage(accountId, openid, request);
        return Result.success(page);
    }

    @GetMapping("/account/{accountId}")
    public Result<PageResponse<WechatMessage>> getPageByAccountId(
            @PathVariable Long accountId,
            @RequestParam(required = false) String openid,
            PageQuery request) {
        PageResponse<WechatMessage> page = wechatMessageService.getPage(accountId, openid, request);
        return Result.success(page);
    }

    @GetMapping("/account/{accountId}/conversation/{openid}")
    public Result<List<WechatMessage>> getConversation(
            @PathVariable Long accountId,
            @PathVariable String openid) {
        List<WechatMessage> messages = wechatMessageService.getConversation(accountId, openid);
        return Result.success(messages);
    }

    @GetMapping("/{id}")
    public Result<WechatMessage> getById(@PathVariable Long id) {
        WechatMessage message = wechatMessageService.getById(id);
        return Result.success(message);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wechatMessageService.delete(id);
        return Result.success();
    }
}
