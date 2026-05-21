package com.ai.pub.repository;

import com.ai.pub.entity.WechatMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WechatMenuRepository extends JpaRepository<WechatMenu, Long> {

    List<WechatMenu> findByAccountIdOrderBySortOrderAsc(Long accountId);

    List<WechatMenu> findByAccountIdAndParentIdOrderBySortOrderAsc(Long accountId, Long parentId);
}
