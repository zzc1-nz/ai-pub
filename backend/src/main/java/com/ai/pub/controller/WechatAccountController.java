package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.WechatAccount;
import com.ai.pub.service.WechatAccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wechat/accounts")
public class WechatAccountController {

    private final WechatAccountService wechatAccountService;

    public WechatAccountController(WechatAccountService wechatAccountService) {
        this.wechatAccountService = wechatAccountService;
    }

    @GetMapping
    public Result<PageResponse<WechatAccount>> getPage(PageQuery request) {
        PageResponse<WechatAccount> page = wechatAccountService.getPage(request);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<WechatAccount> getById(@PathVariable Long id) {
        WechatAccount account = wechatAccountService.getById(id);
        return Result.success(account);
    }

    @PostMapping
    public Result<WechatAccount> create(@RequestBody WechatAccount account) {
        WechatAccount created = wechatAccountService.create(account);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<WechatAccount> update(@PathVariable Long id, @RequestBody WechatAccount account) {
        WechatAccount updated = wechatAccountService.update(id, account);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wechatAccountService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/test")
    public Result<Boolean> testConnection(@PathVariable Long id) {
        boolean success = wechatAccountService.testConnection(id);
        return Result.success(success);
    }
}
