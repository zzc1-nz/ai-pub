package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.entity.WechatMenu;
import com.ai.pub.service.WechatMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wechat/menus")
@RequiredArgsConstructor
public class WechatMenuController {

    private final WechatMenuService wechatMenuService;

    @GetMapping
    public Result<List<WechatMenu>> getByAccountId(@RequestParam Long accountId) {
        List<WechatMenu> menus = wechatMenuService.getByAccountId(accountId);
        return Result.success(menus);
    }

    @GetMapping("/account/{accountId}")
    public Result<List<WechatMenu>> getByAccountIdPath(@PathVariable Long accountId) {
        List<WechatMenu> menus = wechatMenuService.getByAccountId(accountId);
        return Result.success(menus);
    }

    @GetMapping("/{id}")
    public Result<WechatMenu> getById(@PathVariable Long id) {
        WechatMenu menu = wechatMenuService.getById(id);
        return Result.success(menu);
    }

    @PostMapping
    public Result<WechatMenu> create(@RequestBody WechatMenu menu) {
        WechatMenu created = wechatMenuService.create(menu);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<WechatMenu> update(@PathVariable Long id, @RequestBody WechatMenu menu) {
        WechatMenu updated = wechatMenuService.update(id, menu);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wechatMenuService.delete(id);
        return Result.success();
    }

    @PostMapping("/sync/{accountId}")
    public Result<Void> syncToWechat(@PathVariable Long accountId) {
        wechatMenuService.syncToWechat(accountId);
        return Result.success();
    }

    @PostMapping("/account/{accountId}/sync")
    public Result<Void> syncToWechatPath(@PathVariable Long accountId) {
        wechatMenuService.syncToWechat(accountId);
        return Result.success();
    }
}
