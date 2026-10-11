package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.dto.PageQuery;
import com.ai.pub.dto.PageResponse;
import com.ai.pub.entity.SysUser;
import com.ai.pub.service.SysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/users", "/api/sys/users"})
@PreAuthorize("hasRole('ADMIN')")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping
    public Result<PageResponse<SysUser>> getPage(PageQuery request) {
        PageResponse<SysUser> page = sysUserService.getPage(request);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        return Result.success(user);
    }

    @PostMapping
    public Result<SysUser> create(@RequestBody SysUser user) {
        SysUser created = sysUserService.create(user);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody SysUser user) {
        SysUser updated = sysUserService.update(id, user);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return Result.success();
    }
}
