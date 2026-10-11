package com.ai.pub.service;

import com.ai.pub.entity.WechatMenu;
import com.ai.pub.repository.WechatMenuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * WechatMenuService 回归测试。
 *
 * 背景：update() 原先是无条件全量覆盖（existing.setName(menu.getName()) 等），
 * 而前端表格内联排序只提交 { sortOrder }，会把 name 置为 null，
 * 触发 wechat_menu.name 的 NOT NULL 约束异常（前端表现为 500 并回滚）。
 * 现改为部分更新语义：只覆盖请求中显式提供的字段。
 */
@ExtendWith(MockitoExtension.class)
class WechatMenuServiceTest {

    @Mock
    private WechatMenuRepository repository;

    @Mock
    private WechatApiService wechatApiService;

    @InjectMocks
    private WechatMenuService service;

    @Test
    void partialUpdateShouldOnlyChangeProvidedFields() {
        WechatMenu existing = new WechatMenu();
        existing.setId(1L);
        existing.setAccountId(1L);
        existing.setName("关于我们");
        existing.setType("click");
        existing.setKey("ABOUT");
        existing.setSortOrder(0);

        WechatMenu patch = new WechatMenu();
        patch.setSortOrder(5);   // 前端内联排序只发这一个字段

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        WechatMenu updated = service.update(1L, patch);

        assertEquals(5, updated.getSortOrder());
        assertEquals("关于我们", updated.getName(), "name 不应被置空，否则触发 NOT NULL 约束异常");
        assertEquals("click", updated.getType());
        assertEquals("ABOUT", updated.getKey());
    }

    @Test
    void updateShouldApplyAllProvidedFields() {
        WechatMenu existing = new WechatMenu();
        existing.setId(2L);
        existing.setName("旧名称");
        existing.setType("click");
        existing.setKey("OLD");

        WechatMenu patch = new WechatMenu();
        patch.setName("新名称");
        patch.setType("view");
        patch.setUrl("https://example.com");

        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        WechatMenu updated = service.update(2L, patch);

        assertEquals("新名称", updated.getName());
        assertEquals("view", updated.getType());
        assertEquals("https://example.com", updated.getUrl());
    }
}
