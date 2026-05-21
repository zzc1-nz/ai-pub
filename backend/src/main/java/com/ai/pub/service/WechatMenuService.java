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

    @Transactional
    public WechatMenu update(Long id, WechatMenu menu) {
        WechatMenu existing = getById(id);

        existing.setParentId(menu.getParentId());
        existing.setName(menu.getName());
        existing.setType(menu.getType());
        existing.setKey(menu.getKey());
        existing.setUrl(menu.getUrl());
        existing.setAppid(menu.getAppid());
        existing.setPagepath(menu.getPagepath());
        existing.setSortOrder(menu.getSortOrder());

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
