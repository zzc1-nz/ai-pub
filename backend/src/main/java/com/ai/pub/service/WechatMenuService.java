package com.ai.pub.service;

import com.ai.pub.entity.WechatMenu;
import com.ai.pub.repository.WechatMenuRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WechatMenuService {

    private static final Logger log = LoggerFactory.getLogger(WechatMenuService.class);

    private final WechatMenuRepository wechatMenuRepository;
    private final WechatApiService wechatApiService;

    public List<WechatMenu> getByAccountId(Long accountId) {
        return wechatMenuRepository.findByAccountIdOrderBySortOrderAsc(accountId);
    }

    public WechatMenu getById(Long id) {
        return wechatMenuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在"));
    }

    @Transactional
    public WechatMenu create(WechatMenu menu) {
        return wechatMenuRepository.save(menu);
    }

    /**
     * 更新菜单。采用"部分更新"语义：只覆盖请求中显式提供的字段。
     *
     * 原实现是无条件全量覆盖（existing.setName(menu.getName()) 等），
     * 而前端表格内联排序只提交 { sortOrder }，导致 name 被置为 null，
     * 触发 wechat_menu.name 的 NOT NULL 约束异常（前端表现为 500 + 回滚）。
     */
    @Transactional
    public WechatMenu update(Long id, WechatMenu menu) {
        WechatMenu existing = getById(id);

        if (menu.getParentId() != null) {
            existing.setParentId(menu.getParentId());
        }
        if (menu.getName() != null) {
            existing.setName(menu.getName());
        }
        if (menu.getType() != null) {
            existing.setType(menu.getType());
        }
        if (menu.getKey() != null) {
            existing.setKey(menu.getKey());
        }
        if (menu.getUrl() != null) {
            existing.setUrl(menu.getUrl());
        }
        if (menu.getAppid() != null) {
            existing.setAppid(menu.getAppid());
        }
        if (menu.getPagepath() != null) {
            existing.setPagepath(menu.getPagepath());
        }
        if (menu.getSortOrder() != null) {
            existing.setSortOrder(menu.getSortOrder());
        }

        return wechatMenuRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        wechatMenuRepository.deleteById(id);
    }

    @Transactional
    public void syncToWechat(Long accountId) {
        log.info("开始同步菜单到微信: accountId={}", accountId);
        List<WechatMenu> menus = getByAccountId(accountId);
        if (menus.isEmpty()) {
            log.warn("没有菜单需要同步");
            return;
        }
        wechatApiService.syncMenu(accountId, menus);
        log.info("菜单同步完成: accountId={}, menuCount={}", accountId, menus.size());
    }
}
